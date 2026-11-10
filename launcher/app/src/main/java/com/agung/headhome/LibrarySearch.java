package com.agung.headhome;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Folder and song search over the library: case and diacritics insensitive. Pure logic, no Android types. */
public final class LibrarySearch {

    public static final int MAX_RESULTS = 200;

    public static final class Hit {
        public final boolean isFolder;
        public final int folder;
        /** Track index in the folder's natural order, -1 for a folder hit. */
        public final int track;
        public final String title;
        /** The folder name for a song hit, empty for a folder hit. */
        public final String subtitle;
        /** Number of songs for a folder hit, 0 for a song hit. */
        public final int count;

        Hit(boolean isFolder, int folder, int track, String title, String subtitle, int count) {
            this.isFolder = isFolder;
            this.folder = folder;
            this.track = track;
            this.title = title;
            this.subtitle = subtitle;
            this.count = count;
        }
    }

    private LibrarySearch() {
    }

    /** Lower case with diacritics removed, for matching. */
    public static String normalize(String s) {
        String d = Normalizer.normalize(s, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(d.length());
        for (int i = 0; i < d.length(); i++) {
            char c = d.charAt(i);
            if (Character.getType(c) != Character.NON_SPACING_MARK) {
                sb.append(c);
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /** Folders whose name contains the query first, then songs whose title contains it; at most 200 hits. */
    public static List<Hit> search(Library lib, String query) {
        List<Hit> hits = new ArrayList<>();
        String q = normalize(query == null ? "" : query.trim());
        if (q.isEmpty()) {
            return hits;
        }
        for (int f = 0; f < lib.folders.size() && hits.size() < MAX_RESULTS; f++) {
            Library.Folder folder = lib.folders.get(f);
            if (normalize(folder.name).contains(q)) {
                hits.add(new Hit(true, f, -1, folder.name, "", folder.tracks.size()));
            }
        }
        for (int f = 0; f < lib.folders.size() && hits.size() < MAX_RESULTS; f++) {
            Library.Folder folder = lib.folders.get(f);
            for (int t = 0; t < folder.tracks.size() && hits.size() < MAX_RESULTS; t++) {
                String title = folder.tracks.get(t).displayName();
                if (normalize(title).contains(q)) {
                    hits.add(new Hit(false, f, t, title, folder.name, 0));
                }
            }
        }
        return hits;
    }

    /** Up to two upper case initials from the first letters of the first two words ("Avenged Sevenfold" is "AS"). */
    public static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        boolean inWord = false;
        int words = 0;
        for (int i = 0; i < name.length() && words < 2; i++) {
            char c = name.charAt(i);
            if (Character.isLetter(c)) {
                if (!inWord) {
                    sb.append(Character.toUpperCase(c));
                    words++;
                    inWord = true;
                }
            } else if (c != '\'') {
                inWord = false;
            }
        }
        return sb.toString();
    }
}
