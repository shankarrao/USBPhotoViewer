package com.amazon.lab.usbphotoviewer;

import android.graphics.Path;
import android.os.Environment;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

public final class MovieList {
    public static final String MOVIE_CATEGORY[] = {
            "Videos",
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

        String title[] = new String[allMatchingFiles.length];

        for (int i = 0; i < allMatchingFiles.length;i++) {
            title[i] = allMatchingFiles[i].getAbsolutePath();
            list.add(buildMovieInfo("Videos", allMatchingFiles[i].getAbsolutePath().substring(allMatchingFiles[i].getAbsolutePath().lastIndexOf("/")+1),
                    allMatchingFiles[i].getName(), "Phone Videos", title[i], title[i], title[i]));
        }

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
