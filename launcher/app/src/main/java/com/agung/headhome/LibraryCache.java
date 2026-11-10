package com.agung.headhome;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Tab-separated text cache of the library index. */
public final class LibraryCache {

    private static final String HEADER = "H\tv1";

    private LibraryCache() {
    }

    public static void write(File file, Library lib) {
        File tmp = new File(file.getPath() + ".tmp");
        try (BufferedWriter w = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
            w.write(HEADER);
            w.newLine();
            for (Map.Entry<String, String> e : lib.signatures.entrySet()) {
                w.write("R\t" + e.getKey() + "\t" + e.getValue());
                w.newLine();
            }
            for (Library.Folder f : lib.folders) {
                w.write("F\t" + f.path + "\t" + f.name);
                w.newLine();
                for (Library.Track t : f.tracks) {
                    w.write("T\t" + t.fileName);
                    w.newLine();
                }
            }
        } catch (IOException e) {
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(file)) {
            tmp.delete();
        }
    }

    /** Returns the cached library, or null when there is no usable cache. */
    public static Library read(File file) {
        if (!file.isFile()) {
            return null;
        }
        Map<String, String> sig = new TreeMap<>();
        List<Library.Folder> folders = new ArrayList<>();
        String folderPath = null;
        String folderName = null;
        List<Library.Track> tracks = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            if (!HEADER.equals(r.readLine())) {
                return null;
            }
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t");
                if (p.length < 2) {
                    return null;
                }
                switch (p[0]) {
                    case "R":
                        if (p.length < 3) {
                            return null;
                        }
                        sig.put(p[1], p[2]);
                        break;
                    case "F":
                        if (folderPath != null && !tracks.isEmpty()) {
                            folders.add(new Library.Folder(folderPath, folderName, tracks));
                        }
                        if (p.length < 3) {
                            return null;
                        }
                        folderPath = p[1];
                        folderName = p[2];
                        tracks = new ArrayList<>();
                        break;
                    case "T":
                        if (folderPath == null) {
                            return null;
                        }
                        tracks.add(new Library.Track(folderPath + "/" + p[1], p[1]));
                        break;
                    default:
                        return null;
                }
            }
        } catch (IOException e) {
            return null;
        }
        if (folderPath != null && !tracks.isEmpty()) {
            folders.add(new Library.Folder(folderPath, folderName, tracks));
        }
        return new Library(folders, sig);
    }
}
