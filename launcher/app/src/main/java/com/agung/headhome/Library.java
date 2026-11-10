package com.agung.headhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The music index: folders (a directory that directly holds audio) in natural order, each with its tracks. */
public final class Library {

    public static final class Track {
        public final String path;
        public final String fileName;

        public Track(String path, String fileName) {
            this.path = path;
            this.fileName = fileName;
        }

        /** File name without its extension. */
        public String displayName() {
            int dot = fileName.lastIndexOf('.');
            return dot > 0 ? fileName.substring(0, dot) : fileName;
        }
    }

    public static final class Folder {
        public final String path;
        public final String name;
        public final List<Track> tracks;

        public Folder(String path, String name, List<Track> tracks) {
            this.path = path;
            this.name = name;
            this.tracks = Collections.unmodifiableList(new ArrayList<>(tracks));
        }
    }

    public static final Library EMPTY = new Library(new ArrayList<>(), new TreeMap<>());

    public final List<Folder> folders;
    /** Per USB root: a cheap signature (lastModified and entry count) taken when the index was built. */
    public final Map<String, String> signatures;

    public Library(List<Folder> folders, Map<String, String> signatures) {
        this.folders = Collections.unmodifiableList(new ArrayList<>(folders));
        this.signatures = Collections.unmodifiableMap(new TreeMap<>(signatures));
    }

    public int trackCount() {
        int n = 0;
        for (Folder f : folders) {
            n += f.tracks.size();
        }
        return n;
    }
}
