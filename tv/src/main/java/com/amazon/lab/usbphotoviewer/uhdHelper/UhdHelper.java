package com.amazon.lab.usbphotoviewer.uhdHelper;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UhdHelper is a convenience class to provide interfaces to query
 * 1) Supported Display modes.
 * 2) Get current display mode
 * 3) Set preferred mode.
 */
public class UhdHelper {
    Context mContext;
    private UhdHelperListener mListener;
    final public static String version = "v1.0";
    private String sDisplayClassName = "android.view.Display";
    private String sSupportedModesMethodName = "getSupportedModes";
    private String sPreferredDisplayModeIdFieldName = "preferredDisplayModeId";
    private String sGetModeMethodName = "getMode";
    private String sGetModeIdMethodName = "getModeId";
    private String sGetPhysicalHeightMethodName = "getPhysicalHeight";
    private String sGetPhysicalWidthMethodName = "getPhysicalWidth";
    private String sGetRefreshRateMethodName = "getRefreshRate";
    private AtomicBoolean mIsSetModeInProgress;
    private WorkHandler mWorkHandler;
    private HDMIPlugReceiver hdmiPlugReceiver;
    private OverlayStateChangeReceiver overlayStateChangeReceiver;
    boolean isReceiversRegistered;
    private Display mInternalDisplay;
    private boolean showInterstitial = false;
    private boolean isInterstitialFadeReceived = false;
    private Window mTargetWindow;
    private int currentOverlayStatus;
    public final static String MODESWITCH_OVERLAY_ENABLE = "com.amazon.tv.notification.modeswitch_overlay.action.ENABLE";
    public final static String MODESWITCH_OVERLAY_DISABLE = "com.amazon.tv.notification.modeswitch_overlay.action.DISABLE";
    public final static String MODESWITCH_OVERLAY_EXTRA_STATE = "com.amazon.tv.notification.modeswitch_overlay.extra.STATE";
    public final static String MODESWITCH_OVERLAY_STATE_CHANGED= "com.amazon.tv.notification.modeswitch_overlay.action.STATE_CHANGED";
    public final static int OVERLAY_STATE_DISMISSED = 0;
    /**
     * Physical height of UHD in pixels ( {@value} )
     */
    public static final int HEIGHT_UHD = 2160;
    /**
     * {@value} ms to wait for broadcast before declaring timeout.
     */
    public static final int SET_MODE_TIMEOUT_DELAY_MS = 15 * 1000;
    /**
     * {@value} ms to wait for Interstitial broadcast before declaring timeout.
     */
    public static final int SHOW_INTERSTITIAL_TIMEOUT_DELAY_MS = 2* 1000;

    private static final String TAG = UhdHelper.class.getSimpleName();

    /**
     * Construct a UhdHelper object.
     * @param context Activity context.
     */
    public UhdHelper(Context context) {
        mContext = context;
        mInternalDisplay = new Display();
        mIsSetModeInProgress = new AtomicBoolean(false);
        mWorkHandler = new WorkHandler(Looper.getMainLooper());
        hdmiPlugReceiver = new HDMIPlugReceiver();
        overlayStateChangeReceiver = new OverlayStateChangeReceiver();
        isReceiversRegistered = false;

    }

    private static final int HDMI_AUDIO_PLUG_BRODACAST_MSG = 1;
    private static final int MODE_CHANGE_TIMEOUT_MSG = 2;
    private static final int SEND_CALLBACK_WITH_SUPPLIED_RESULT = 3;
    private static final int INTERSTITIAL_FADED_BROADCAST_MSG = 4;
    private static final int INTERSTITIAL_TIMEOUT_MSG = 5;
    /**
     * Handler that handles the broadcast or timeout
     * prcoessing and issues callbacks accordingly.
     */
    private class WorkHandler extends Handler {
        private int mRequestedModeId;
        private UhdHelperListener mCallbackListener;
        public WorkHandler(Looper looper) {
            super(looper);
        }
        public void setExpectedMode(int modeId) {
            mRequestedModeId = modeId;
        }
        private void setCallbackListener(UhdHelperListener listener) {
            this.mCallbackListener = listener;
        }
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HDMI_AUDIO_PLUG_BRODACAST_MSG:
                    Display.Mode mode = getMode();
                    if(mode == null) {
                        Log.w(TAG, "Mode query returned null after hdmi bcast");
                        return;
                    }
                    if(mode.getModeId() == mRequestedModeId) {
                        Log.i(TAG, "Broadcast for expected change! Id= " + mRequestedModeId);
                        maybeDoACallback(mode);
                        doPostModeSetCleanup();
                    } else {
                        Log.w(TAG, "HDMI plug but not expected mode. Mode= "+ mode + " expected= " + mRequestedModeId);
                    }

                    break;
                case MODE_CHANGE_TIMEOUT_MSG:
                    Log.i(TAG, "Time out without mode change");
                    maybeDoACallback(null);
                    doPostModeSetCleanup();
                    break;
                case SEND_CALLBACK_WITH_SUPPLIED_RESULT:
                    maybeDoACallback((Display.Mode) msg.obj);
                    if(msg.arg1 == 1) {
                        doPostModeSetCleanup();
                    }
                    break;
                case INTERSTITIAL_FADED_BROADCAST_MSG:
                    if(!isInterstitialFadeReceived) {
                        Log.i(TAG,"Broadcast for text fade received, Initializing the mode change.");
                        isInterstitialFadeReceived = true;
                        initModeChange(mRequestedModeId, null);
                    }
                    break;
                case INTERSTITIAL_TIMEOUT_MSG:
                    if(!isInterstitialFadeReceived){
                        Log.w(TAG,"Didn't received any broadcast for interstitial text fade till time out, starting the mode change.");
                        isInterstitialFadeReceived = true; //So we don't do another.
                        initModeChange(mRequestedModeId, null);
                    }
                default:
                    break;
            }
        }

        private void maybeDoACallback(Display.Mode mode) {
            if(this.mCallbackListener !=null) {
                Log.d(TAG, "Sending callback to listener");
                this.mCallbackListener.onModeChanged(mode);
            } else {
                Log.d(TAG, "Can't issue callback as no listener registered");
            }
        }
        /**
         * Removal of message and unregistering receiver after mode set is done.
         */
        private void doPostModeSetCleanup() {
            if(currentOverlayStatus!= OVERLAY_STATE_DISMISSED){
                Log.i(TAG, "Tearing down the overlay Post mode switch attempt.");
                currentOverlayStatus=OVERLAY_STATE_DISMISSED;
                hideOptimizingOverlay();
            }
            synchronized (mIsSetModeInProgress) {
                //need these to be run in order, tell compiler
                // not to reoder the instructions.
                this.removeMessages(MODE_CHANGE_TIMEOUT_MSG);
                if(isReceiversRegistered) {
                    mContext.unregisterReceiver(hdmiPlugReceiver);
                    mContext.unregisterReceiver(overlayStateChangeReceiver);
                    isReceiversRegistered = false;
                }
                this.removeMessages(HDMI_AUDIO_PLUG_BRODACAST_MSG);
                mCallbackListener = null;
                mIsSetModeInProgress.set(false);
            }
        }
    }

    /**
     * Private class for receiving the
     * {@link AudioManager#ACTION_HDMI_AUDIO_PLUG ACTION_HDMI_AUDIO_PLUG} events.
     */
    private class HDMIPlugReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(isInitialStickyBroadcast()) {
                Log.i(TAG, "Ignoring the initial sticky HDMI plug broadcast");
                return;
            }
            int pluggedStatus = intent.getIntExtra((AudioManager.EXTRA_AUDIO_PLUG_STATE), -1);
            if (pluggedStatus > 0) {
                mWorkHandler.obtainMessage(HDMI_AUDIO_PLUG_BRODACAST_MSG).sendToTarget();
            }
        }
    }

    /**
     * Private class for receiving the
     * {@link com.amazon.tv.notification.modeswitch_overlay.extra.STATE STATE} events.
     */
    private class OverlayStateChangeReceiver extends BroadcastReceiver {
        private final int OVERLAY_FADE_COMPLETE_EXTRA = 3;
        @Override
        public void onReceive(Context context, Intent intent) {
            currentOverlayStatus = intent.getIntExtra((MODESWITCH_OVERLAY_EXTRA_STATE), -1);
            if(currentOverlayStatus==OVERLAY_FADE_COMPLETE_EXTRA && !isInterstitialFadeReceived){
                mWorkHandler.removeMessages(INTERSTITIAL_TIMEOUT_MSG);
                mWorkHandler.sendMessage(mWorkHandler.obtainMessage(INTERSTITIAL_FADED_BROADCAST_MSG));
                Log.i(TAG, "Got the Interstitial text fade broadcast, Starting the mode change");
            }
        }
    }

    /**
     * Utility method to check if device is Amazon Fire TV device
     * @return {@code true} true if device is Amazon Fire TV device.
     */
    private boolean isAmazonFireTVDevice(){
        String deviceName = Build.MODEL;
        String manufacturerName = Build.MANUFACTURER;
        return (deviceName.startsWith("AFT")
                && "Amazon".equalsIgnoreCase(manufacturerName));
    }

    /**
     * Returns the current Display mode.
     *
     * @return {@link Display.Mode Mode}
     * that is currently set on the system or NULL if an error occurred.
     */
    public Display.Mode getMode() {
        android.view.Display currentDisplay = getCurrentDisplay();
        if (currentDisplay == null) {
            return null;
        }
        try {
            Class<?> classToInvestigate = Class.forName(sDisplayClassName);
            Method getModeMethod = classToInvestigate.getDeclaredMethod(sGetModeMethodName, null);
            Object currentMode = getModeMethod.invoke(currentDisplay, null);
            return convertReturnedModeToInternalMode(currentMode);
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
        Log.e(TAG, "Current Mode is not present in supported Modes");
        return null;
    }

    /**
     * Utility function to parse android.view.Display,Mode to
     * {@link Display.Mode mode}
     * @param systemMode
     * @return {@link Display.Mode Mode} object
     * or NULL if an error occurred.
     */
    private Display.Mode convertReturnedModeToInternalMode(Object systemMode) {
        Display.Mode returnedInstance = null;
        try {
            Class modeClass = systemMode.getClass();
            int modeId = (int) modeClass.getDeclaredMethod(sGetModeIdMethodName).invoke(systemMode);
            int width = (int) modeClass.getDeclaredMethod(sGetPhysicalWidthMethodName).invoke(systemMode);
            int height = (int) modeClass.getDeclaredMethod(sGetPhysicalHeightMethodName).invoke(systemMode);
            float refreshRate = (float) modeClass.getDeclaredMethod(sGetRefreshRateMethodName).invoke(systemMode);
            returnedInstance =  mInternalDisplay.getModeInstance(modeId, width, height, refreshRate);
        } catch(Exception e) {
            Log.e(TAG, "error converting", e);
        }
        return returnedInstance;
    }

    /**
     * Returns all the supported modes.
     *
     * @return An array of
     * {@link Display.Mode Mode} objects
     * or NULL if an error occurred.
     */
    public Display.Mode[] getSupportedModes() {
        Display.Mode[] returnedSupportedModes = null;
        try {
            Class classToInvestigate = Class.forName(sDisplayClassName);
            Method getSupportedMethod = classToInvestigate.getDeclaredMethod(sSupportedModesMethodName, null);
            Object[] SupportedModes = (Object[]) getSupportedMethod.invoke(getCurrentDisplay(), null);
            returnedSupportedModes = new Display.Mode[SupportedModes.length];
            int i = 0;
            for (Object mode : SupportedModes) {
                returnedSupportedModes[i++] = convertReturnedModeToInternalMode(mode);
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return returnedSupportedModes;
    }

    /**
     * Returns current {@link android.view.Display Display} object.
     * Assumes that the 1st display is the actual display.
     *
     * @return {@link android.view.Display Display}
     */
    private android.view.Display getCurrentDisplay() {
        if (mContext == null)
            return null;
        DisplayManager displayManager = (DisplayManager) mContext.getSystemService(Context.DISPLAY_SERVICE);
        android.view.Display[] displays = displayManager.getDisplays();
        if (displays == null || displays.length == 0) {
            Log.e(TAG, "ERROR on device to get the display");
            return null;
        }
        //assuming the 1st display is the actual display.
        return displays[0];
    }

    /**
     * Change the display mode to the supplied mode.
     * <p>
     * Note that you must register a {@link UhdHelperListener listener} using
     * {@link UhdHelper#registerModeChangeListener(UhdHelperListener) registerModeChangeListener}
     * to receive the callback for success or failure.
     * Also, note that this method need to be called from Main UI thread.
     * <p>
     * The method will not attempt a mode switch and fail immediately with callback if
     * 1) Device SDK is less than Android L
     * 2) Device is Android L but not Amazon AFT* devices.
     *
     *
     * @param targetWindow {@link Window Window} to use for setting the display
     *                      and call parameters
     * @param modeId The desired mode to switch to. Must be a valid mode supported
     *               by the platform.
     * @param allowOverlayDisplay Flag request to allow display overlay on applicable device.
     */
    public void setPreferredDisplayModeId(Window targetWindow, int modeId, boolean allowOverlayDisplay) {
        /*
         * The Android M preview adds a preferredDisplayModeId to
         * WindowManager.LayoutParams.preferredDisplayModeId API. A PreferredDisplayModeId can be
         * set in the LayoutParams of any Window.
         */
        String deviceName = Build.MODEL;
        boolean supportedDevice = true;
        // Let the handler know what listener to use, we will
        // send null callback in case of an error.
        mWorkHandler.setCallbackListener(mListener);
        //We fail for following conditions
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            supportedDevice = false;
        } else {
            switch (Build.VERSION.SDK_INT) {
                case Build.VERSION_CODES.LOLLIPOP:
                case Build.VERSION_CODES.LOLLIPOP_MR1:
                    if (!isAmazonFireTVDevice()) {
                        supportedDevice = false;
                    }
                    break;
            }
        }

        //Some basic failure conditions that need handling
        if (!supportedDevice) {
            Log.i(TAG, "Attempt to set preferred Display mode on an unsupported device: " + deviceName);
            //send and cleanup
            mWorkHandler.sendMessage(mWorkHandler.obtainMessage(SEND_CALLBACK_WITH_SUPPLIED_RESULT,1,1, null));
            return;
        } else if(!isAmazonFireTVDevice()){
            //We cannot not show interstitial for Non-Amazon Fire TV devices
            allowOverlayDisplay=false;
        }
        if(mIsSetModeInProgress.get()) {
            Log.e(TAG, "setPreferredDisplayModeId is already in progress! " +
                    "Cannot set another while it is in progress");
            //Send but don't cleanup as further processing is expected.
            mWorkHandler.sendMessage(mWorkHandler.obtainMessage(SEND_CALLBACK_WITH_SUPPLIED_RESULT, null));
            return;
        }
        Display.Mode currentMode = getMode();
        if(currentMode == null || currentMode.getModeId()== modeId) {
            Log.i(TAG, "Current mode id same as mode id requested or is Null. Aborting.");
            //send and cleanup receivers/callback listeners
            mWorkHandler.sendMessage(mWorkHandler.obtainMessage(SEND_CALLBACK_WITH_SUPPLIED_RESULT,1,1, currentMode));
            return;
        }
        //Check if the modeId given is even supported by the system.
        Display.Mode[] supportedModes = getSupportedModes();
        boolean isRequestedModeSupported=false;
        boolean isRequestedModeUhd = false;
        for(Display.Mode mode : supportedModes){
            if(mode.getModeId()==modeId){
                isRequestedModeUhd = (mode.getPhysicalHeight() >= HEIGHT_UHD ? true:false);
                isRequestedModeSupported = true;
                break;
            }
        }
        if(!isRequestedModeSupported) {
            Log.e(TAG, "Requested mode id not among the supported Mode Id.");
            //send and cleanup receivers/callback listeners
            mWorkHandler.sendMessage(mWorkHandler.obtainMessage(SEND_CALLBACK_WITH_SUPPLIED_RESULT,1,1, null));
            return;
        }

        //We are now going to do setMode call and will do callback for it.
        mIsSetModeInProgress.set(true);
        //Let the handler know what modeId hdmi plug event to look for
        mWorkHandler.setExpectedMode(modeId);
        //register the hdmi plug event. Since it is sticky broadcast, we receive one bcast
        // right away.
        mContext.registerReceiver(hdmiPlugReceiver, new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG));
        mContext.registerReceiver(overlayStateChangeReceiver, new IntentFilter(MODESWITCH_OVERLAY_STATE_CHANGED));
        isReceiversRegistered = true;

        mTargetWindow = targetWindow;
        showInterstitial = (allowOverlayDisplay && isRequestedModeUhd);

        //Also check if flag is available, otherwise fail and return
        WindowManager.LayoutParams mWindowAttributes = mTargetWindow.getAttributes();
        //Check if the field is available or not. This is for early failure.
        Class<?> cLayoutParams = mWindowAttributes.getClass();
        Field attributeFlags;
        try {
            attributeFlags = cLayoutParams.getDeclaredField(sPreferredDisplayModeIdFieldName);
        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage());
            //send and cleanup receivers/callback listeners
            mWorkHandler.sendMessage(mWorkHandler.obtainMessage(SEND_CALLBACK_WITH_SUPPLIED_RESULT,1,1, null));
            return;
        }

        if(showInterstitial) {
            isInterstitialFadeReceived=false;
            showOptimizingOverlay();
            mWorkHandler.sendMessageDelayed(mWorkHandler.obtainMessage(INTERSTITIAL_TIMEOUT_MSG), SHOW_INTERSTITIAL_TIMEOUT_DELAY_MS);
        } else{
            initModeChange(modeId, attributeFlags);
        }
    }

    /**
     * Start the mode change by setting the preferredDisplayModeId field of {@link WindowManager.LayoutParams}
     */
    private void initModeChange(int modeId, Field attributeFlagField){
        WindowManager.LayoutParams mWindowAttributes = mTargetWindow.getAttributes();
        try {
            if(attributeFlagField == null) {
                Class<?> cLayoutParams = mWindowAttributes.getClass();
                attributeFlagField = cLayoutParams.getDeclaredField(sPreferredDisplayModeIdFieldName);
            }
            //attempt mode switch
            attributeFlagField.setInt(mWindowAttributes, modeId);
            mTargetWindow.setAttributes(mWindowAttributes);

        } catch (Exception e) {
            Log.e(TAG, e.getLocalizedMessage());
            //send and cleanup receivers/callback listeners
            mWorkHandler.sendMessage(mWorkHandler.obtainMessage(SEND_CALLBACK_WITH_SUPPLIED_RESULT,1,1, null));
            return;
        }
        //We assume that the mode change is not instantaneous and will send the HDMI plug broadcast.
        // Start the clock on the mode change timeout
        mWorkHandler.sendMessageDelayed(mWorkHandler.obtainMessage(MODE_CHANGE_TIMEOUT_MSG), SET_MODE_TIMEOUT_DELAY_MS);
    }

    /**
     * Send the broadcast to show overlay display
     */
    private void showOptimizingOverlay(){
        final Intent overlayIntent = new Intent(MODESWITCH_OVERLAY_ENABLE);
        mContext.sendBroadcast(overlayIntent);
        Log.i(TAG,"Sending the broadcast to display overlay");
    }

    /**
     * Send the broadcast to hide overlay display if showing.
     */
    private void hideOptimizingOverlay() {
        final Intent overlayIntent = new Intent(MODESWITCH_OVERLAY_DISABLE);
        mContext.sendBroadcast(overlayIntent);
        Log.i(TAG, "Sending the broadcast to hide display overlay");

    }

    /**
     * Register a {@link UhdHelperListener listener} to be notified of result
     * of the {@link UhdHelper#setPreferredDisplayModeId(Window, int,boolean) setPreferredDisplayModeId}
     * call.
     *
     * @param listener that will receive the result of the callback.
     */
    public void registerModeChangeListener(UhdHelperListener listener) {
        mListener = listener;
    }

    /**
     *Register the {@link UhdHelperListener listener}
     * @param listener
     */
    public void unregisterDisplayModeChangeListener(UhdHelperListener listener) {
        mListener = null;
    }

}
