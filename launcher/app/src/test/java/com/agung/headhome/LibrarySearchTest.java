package com.agung.headhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class LibrarySearchTest {

    private static Library.Folder folder(String name, String... songs) {
        List<Library.Track> tracks = new ArrayList<>();
        for (String s : songs) {
            tracks.add(new Library.Track("/m/" + name + "/" + s, s));
        }
        return new Library.Folder("/m/" + name, name, tracks);
    }

    private static Library lib(Library.Folder... folders) {
        List<Library.Folder> list = new ArrayList<>();
        for (Library.Folder f : folders) {
            list.add(f);
        }
        return new Library(list, new TreeMap<>());
    }

    @Test
    public void foldersComeBeforeSongs() {
        Library l = lib(folder("Rindu Band", "a.mp3"), folder("Other", "Rindu Setengah Mati.mp3", "zzz.mp3"));
        List<LibrarySearch.Hit> hits = LibrarySearch.search(l, "rindu");
        assertEquals(2, hits.size());
        assertTrue(hits.get(0).isFolder);
        assertEquals("Rindu Band", hits.get(0).title);
        assertEquals(1, hits.get(0).count);
        assertFalse(hits.get(1).isFolder);
        assertEquals("Rindu Setengah Mati", hits.get(1).title);
        assertEquals("Other", hits.get(1).subtitle);
        assertEquals(1, hits.get(1).folder);
        assertEquals(0, hits.get(1).track);
    }

    @Test
    public void ignoresCaseAndDiacritics() {
        Library l = lib(folder("Café Del Mar", "Señorita.mp3"));
        assertEquals(1, LibrarySearch.search(l, "CAFE").size());
        assertEquals(2, LibrarySearch.search(l, "sen").size() + LibrarySearch.search(l, "café").size());
        assertEquals(1, LibrarySearch.search(l, "senorita").size());
    }

    @Test
    public void matchesTitleWithoutExtension() {
        Library l = lib(folder("F", "Song.mp3"));
        assertTrue(LibrarySearch.search(l, "mp3").isEmpty());
        assertEquals(1, LibrarySearch.search(l, "song").size());
    }

    @Test
    public void blankQueryGivesNothing() {
        Library l = lib(folder("F", "a.mp3"));
        assertTrue(LibrarySearch.search(l, "  ").isEmpty());
        assertTrue(LibrarySearch.search(l, null).isEmpty());
    }

    @Test
    public void resultsAreCappedAt200() {
        String[] songs = new String[300];
        for (int i = 0; i < songs.length; i++) {
            songs[i] = "hit " + i + ".mp3";
        }
        assertEquals(LibrarySearch.MAX_RESULTS, LibrarySearch.search(lib(folder("F", songs)), "hit").size());
    }

    @Test
    public void initialsFromFirstTwoWords() {
        assertEquals("AS", LibrarySearch.initials("Avenged Sevenfold"));
        assertEquals("D", LibrarySearch.initials("D'MASIV"));
        assertEquals("ID", LibrarySearch.initials("Iis Dahlia - Rindu (2011)"));
        assertEquals("", LibrarySearch.initials("123"));
    }
}
