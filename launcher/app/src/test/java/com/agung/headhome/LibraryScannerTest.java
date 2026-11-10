package com.agung.headhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class LibraryScannerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static void touch(File f) throws Exception {
        f.getParentFile().mkdirs();
        assertTrue(f.createNewFile());
    }

    @Test
    public void scansFoldersWithAudioInNaturalOrderAndRoundTripsCache() throws Exception {
        File base = tmp.newFolder("base");
        touch(new File(base, "usb/MP3/10 Band/b.MP3"));
        touch(new File(base, "usb/MP3/10 Band/a2.mp3"));
        touch(new File(base, "usb/MP3/10 Band/a10.mp3"));
        touch(new File(base, "usb/MP3/2 Band/x.flac"));
        touch(new File(base, "usb/MP3/2 Band/cover.jpg"));
        touch(new File(base, "usb/MP3/empty/readme.txt"));
        touch(new File(base, "usb/MP3/.hidden/h.mp3"));

        Library lib = LibraryScanner.scan(base);
        assertEquals(2, lib.folders.size());
        assertEquals("2 Band", lib.folders.get(0).name);
        assertEquals("10 Band", lib.folders.get(1).name);
        assertEquals("a2.mp3", lib.folders.get(1).tracks.get(0).fileName);
        assertEquals("a10.mp3", lib.folders.get(1).tracks.get(1).fileName);
        assertEquals("b.MP3", lib.folders.get(1).tracks.get(2).fileName);
        assertEquals(4, lib.trackCount());
        assertEquals(1, lib.signatures.size());

        File cache = new File(tmp.getRoot(), "library.idx");
        LibraryCache.write(cache, lib);
        Library back = LibraryCache.read(cache);
        assertNotNull(back);
        assertEquals(lib.signatures, back.signatures);
        assertEquals(2, back.folders.size());
        assertEquals(lib.folders.get(1).tracks.get(2).path, back.folders.get(1).tracks.get(2).path);
    }

    @Test
    public void audioExtensionsAreCaseInsensitive() {
        assertTrue(LibraryScanner.isAudio("a.Mp3"));
        assertTrue(LibraryScanner.isAudio("a.OPUS"));
        assertTrue(!LibraryScanner.isAudio("a.jpg"));
        assertTrue(!LibraryScanner.isAudio("mp3"));
    }
}
