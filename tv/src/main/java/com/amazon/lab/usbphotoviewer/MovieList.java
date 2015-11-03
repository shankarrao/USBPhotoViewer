package com.amazon.lab.usbphotoviewer;

import android.os.Environment;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

public final class MovieList {
    public static final String MOVIE_CATEGORY[] = {
            "Photos",
            "Movies",
            "Category Two",
            "Category Three",
            "Category Four",
            "Category Five",
    };

    public static List<Movie> list;

    public static List<Movie> setupMovies() {
        FilenameFilter[] filter = new FilenameFilter[1];
        filter[0] = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".mp4");
            }
        };
        File[] allMatchingFiles =
        Utils.listFilesAsArray(new File("/storage/emulated/0/Pictures"), filter, -1);
        list = new ArrayList<Movie>();
        String title[] = new String[10];
        title[0] = allMatchingFiles[0].getAbsolutePath();


        String description = allMatchingFiles[0].getAbsolutePath();

        String videoUrl[] = new String[10];
        videoUrl[0] = allMatchingFiles[0].getAbsolutePath();

        String bgImageUrl[] = new String[10];

        String cardImageUrl[] = new String[10];
        bgImageUrl[0] = allMatchingFiles[0].getAbsolutePath();
        cardImageUrl[0] = allMatchingFiles[0].getAbsolutePath();

        list.add(buildMovieInfo("Videos", title[0],
                description, "Phone Videos", videoUrl[0], cardImageUrl[0], bgImageUrl[0]));
        /*list.add(buildMovieInfo("category", title[0],
                description, "Studio One", videoUrl[0], cardImageUrl[0], bgImageUrl[0]));
        list.add(buildMovieInfo("category", title[0],
                description, "Studio Two", videoUrl[0], cardImageUrl[0], bgImageUrl[0]));
        list.add(buildMovieInfo("category", title[0],
                description, "Studio Three", videoUrl[0], cardImageUrl[0], bgImageUrl[0]));
        list.add(buildMovieInfo("category", title[0],
                description, "Studio Four", videoUrl[0], cardImageUrl[0], bgImageUrl[0]));
        */

        return list;
    }

    private static Movie buildMovieInfo(String category, String title,
                                        String description, String studio, String videoUrl, String cardImageUrl,
                                        String bgImageUrl) {
        Movie movie = new Movie();
        movie.setId(Movie.getCount());
        Movie.incCount();
        movie.setTitle(title);
        movie.setDescription(description);
        movie.setStudio(studio);
        movie.setCategory(category);
        movie.setCardImageUrl(cardImageUrl);
        movie.setBackgroundImageUrl(bgImageUrl);
        movie.setVideoUrl(videoUrl);
        return movie;
    }
}
