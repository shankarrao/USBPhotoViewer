/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.amazon.lab.usbphotoviewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.util.Collection;
import java.util.Vector;

/**
 * A collection of utility methods, all static.
 */
public class Utils {

    /*
     * Making sure public utility methods remain static
     */
    private Utils() {
    }

    /**
     * Returns the screen/display size
     */
    public static Point getDisplaySize(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size;
    }

    /**
     * Shows a (long) toast
     */
    public static void showToast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    /**
     * Shows a (long) toast.
     */
    public static void showToast(Context context, int resourceId) {
        Toast.makeText(context, context.getString(resourceId), Toast.LENGTH_LONG).show();
    }

    public static int convertDpToPixel(Context ctx, int dp) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    /**
     * Formats time in milliseconds to hh:mm:ss string format.
     */
    public static String formatMillis(int millis) {
        String result = "";
        int hr = millis / 3600000;
        millis %= 3600000;
        int min = millis / 60000;
        millis %= 60000;
        int sec = millis / 1000;
        if (hr > 0) {
            result += hr + ":";
        }
        if (min >= 0) {
            if (min > 9) {
                result += min + ":";
            } else {
                result += "0" + min + ":";
            }
        }
        if (sec > 9) {
            result += sec;
        } else {
            result += "0" + sec;
        }
        return result;
    }
    public static File[] listFilesAsArray(File directory, FilenameFilter[] filter,
                                   int recurse) {
        Collection<File> files = listFiles(directory, filter, recurse);

        File[] arr = new File[files.size()];
        return files.toArray(arr);
    }

    public static Collection<File> listFiles(File directory,
                                      FilenameFilter[] filter, int recurse) {

        Vector<File> files = new Vector<File>();

        File[] entries = directory.listFiles();
        String s[] = directory.list();

        if (entries != null) {
            for (File entry : entries) {
                for (FilenameFilter filefilter : filter) {
                    if (filter == null
                            || filefilter
                            .accept(directory, entry.getName())) {
                        files.add(entry);
                        Log.e("USBPhotoViewer", "Added: "
                                + entry.getName());
                    }
                }
                if ((recurse <= -1) || (recurse > 0 && entry.isDirectory())) {
                    recurse--;
                    files.addAll(listFiles(entry, filter, recurse));
                    recurse++;
                }
            }
        }
        return files;
    }

    public static Drawable getThumbnail(String url) {
        Bitmap b = ThumbnailUtils.createVideoThumbnail(url, MediaStore.Video.Thumbnails.MINI_KIND);
        Drawable drawable = new BitmapDrawable(b);
        return drawable;
    }

    public static int getMovieHeight(String url) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(new FileInputStream(url).getFD());
        } catch (Exception e) {
            e.printStackTrace();
        }
        String height = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        return Integer.parseInt(height);
    }
}
