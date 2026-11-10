package com.agung.headhome;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Walks the USB mount root and builds the {@link Library}. Plain java.io, no Android types. */
public final class LibraryScanner {

    public static final String DEFAULT_BASE = "/mnt/media_rw";

    private static final Set<String> EXTENSIONS = new HashSet<>(
            Arrays.asList("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav"));
    private static final int MAX_DEPTH = 12;

    private LibraryScanner() {
    }

    public static boolean isAudio(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** Readable USB roots below the base directory. */
    static List<File> roots(File base) {
        List<File> roots = new ArrayList<>();
        File[] children = base.listFiles();
        if (children != null) {
            for (File c : children) {
                if (c.isDirectory() && c.canRead()) {
                    roots.add(c);
                }
            }
        }
        Collections.sort(roots, (a, b) -> NaturalOrder.INSTANCE.compare(a.getName(), b.getName()));
        return roots;
    }

    /** Cheap change signature per root: last modified time and the number of entries it holds. */
    public static Map<String, String> signatures(File base) {
        Map<String, String> sig = new TreeMap<>();
        for (File root : roots(base)) {
            String[] names = root.list();
            sig.put(root.getAbsolutePath(), root.lastModified() + ":" + (names == null ? 0 : names.length));
        }
        return sig;
    }

    public static Library scan(File base) {
        List<Library.Folder> folders = new ArrayList<>();
        for (File root : roots(base)) {
            walk(root, folders, 0);
        }
        Collections.sort(folders, (a, b) -> NaturalOrder.INSTANCE.compare(a.path, b.path));
        return new Library(folders, signatures(base));
    }

    private static void walk(File dir, List<Library.Folder> out, int depth) {
        File[] children = dir.listFiles();
        if (children == null || depth > MAX_DEPTH) {
            return;
        }
        List<Library.Track> tracks = new ArrayList<>();
        for (File c : children) {
            String name = c.getName();
            if (name.startsWith(".")) {
                continue;
            }
            if (c.isDirectory()) {
                if (c.canRead()) {
                    walk(c, out, depth + 1);
                }
            } else if (isAudio(name)) {
                tracks.add(new Library.Track(c.getAbsolutePath(), name));
            }
        }
        if (!tracks.isEmpty()) {
            Collections.sort(tracks, (a, b) -> NaturalOrder.INSTANCE.compare(a.fileName, b.fileName));
            out.add(new Library.Folder(dir.getAbsolutePath(), dir.getName(), tracks));
        }
    }
}
