package com.agung.headhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class PlayQueueTest {

    private static Library.Folder folder(String name, int tracks) {
        List<Library.Track> list = new ArrayList<>();
        for (int i = 1; i <= tracks; i++) {
            list.add(new Library.Track("/m/" + name + "/" + i + ".mp3", i + ".mp3"));
        }
        return new Library.Folder("/m/" + name, name, list);
    }

    private static PlayQueue queue(int... sizes) {
        List<Library.Folder> folders = new ArrayList<>();
        for (int i = 0; i < sizes.length; i++) {
            folders.add(folder("f" + i, sizes[i]));
        }
        return new PlayQueue(folders, new Random(42));
    }

    private static String id(Library.Track t) {
        return t.path.substring("/m/".length());
    }

    @Test
    public void advancesFolderByFolder() {
        PlayQueue q = queue(2, 2);
        q.setMode(RepeatMode.CONTINUE);
        assertEquals("f0/1.mp3", id(q.current()));
        assertEquals("f0/2.mp3", id(q.onCompletion()));
        assertEquals("f1/1.mp3", id(q.onCompletion()));
        assertEquals(1, q.folderIndex());
        assertEquals("f1/2.mp3", id(q.onCompletion()));
    }

    @Test
    public void continueStopsAfterLastSong() {
        PlayQueue q = queue(1, 1);
        q.setMode(RepeatMode.CONTINUE);
        assertEquals("f1/1.mp3", id(q.onCompletion()));
        assertNull(q.onCompletion());
        assertNull(q.next());
        assertEquals("f1/1.mp3", id(q.current()));
    }

    @Test
    public void repeatAllWraps() {
        PlayQueue q = queue(1, 1);
        q.setMode(RepeatMode.REPEAT_ALL);
        q.onCompletion();
        assertEquals("f0/1.mp3", id(q.onCompletion()));
        assertEquals(0, q.folderIndex());
    }

    @Test
    public void repeatFolderLoopsCurrentFolder() {
        PlayQueue q = queue(2, 2);
        q.setMode(RepeatMode.REPEAT_FOLDER);
        assertEquals("f0/2.mp3", id(q.onCompletion()));
        assertEquals("f0/1.mp3", id(q.onCompletion()));
        assertEquals(0, q.folderIndex());
    }

    @Test
    public void repeatOneKeepsSongOnCompletionButNextMoves() {
        PlayQueue q = queue(2);
        q.setMode(RepeatMode.REPEAT_ONE);
        assertEquals("f0/1.mp3", id(q.onCompletion()));
        assertEquals("f0/1.mp3", id(q.onCompletion()));
        assertEquals("f0/2.mp3", id(q.next()));
    }

    @Test
    public void prevRestartsAfterThreeSecondsThenGoesBackAcrossFolders() {
        PlayQueue q = queue(2, 2);
        q.jumpTo(1, 0);
        assertEquals("f1/1.mp3", id(q.prev(3500)));
        assertEquals("f0/2.mp3", id(q.prev(1000)));
        assertEquals(0, q.folderIndex());
        assertEquals("f0/1.mp3", id(q.prev(0)));
    }

    @Test
    public void prevAtVeryStartWrapsOnlyInRepeatAll() {
        PlayQueue q = queue(2, 3);
        q.setMode(RepeatMode.REPEAT_ALL);
        assertEquals("f1/3.mp3", id(q.prev(0)));
        PlayQueue c = queue(2, 3);
        c.setMode(RepeatMode.CONTINUE);
        assertEquals("f0/1.mp3", id(c.prev(0)));
    }

    @Test
    public void shuffleStaysInsideFolderUntilExhausted() {
        PlayQueue q = queue(5, 3);
        q.setMode(RepeatMode.CONTINUE);
        q.setShuffle(true);
        Set<String> seen = new HashSet<>();
        seen.add(id(q.current()));
        for (int i = 0; i < 4; i++) {
            Library.Track t = q.onCompletion();
            assertNotNull(t);
            assertTrue(t.path.startsWith("/m/f0/"));
            assertTrue("repeated " + id(t), seen.add(id(t)));
        }
        assertEquals(5, seen.size());
        Library.Track nextFolder = q.onCompletion();
        assertTrue(nextFolder.path.startsWith("/m/f1/"));
        Set<String> seen1 = new HashSet<>();
        seen1.add(id(nextFolder));
        seen1.add(id(q.onCompletion()));
        seen1.add(id(q.onCompletion()));
        assertEquals(3, seen1.size());
        assertNull(q.onCompletion());
    }

    @Test
    public void jumpToWithShuffleStartsOnTheChosenSong() {
        PlayQueue q = queue(6);
        q.setShuffle(true);
        q.jumpTo(0, 4);
        assertEquals("f0/5.mp3", id(q.current()));
        assertEquals(4, q.trackIndex());
    }

    @Test
    public void shuffleOffKeepsCurrentSongAndRestoresOrder() {
        PlayQueue q = queue(4);
        q.setShuffle(true);
        q.jumpTo(0, 2);
        q.setShuffle(false);
        assertFalse(q.shuffle());
        assertEquals("f0/3.mp3", id(q.current()));
        assertEquals("f0/4.mp3", id(q.next()));
    }

    @Test
    public void locateFindsTrackByPath() {
        PlayQueue q = queue(2, 2);
        assertTrue(q.locate("/m/f1/2.mp3"));
        assertEquals(1, q.folderIndex());
        assertEquals(1, q.trackIndex());
        assertFalse(q.locate("/m/none.mp3"));
    }

    @Test
    public void emptyQueueDoesNothing() {
        PlayQueue q = queue();
        assertTrue(q.isEmpty());
        assertNull(q.current());
        assertNull(q.next());
        assertNull(q.prev(0));
        assertNull(q.onCompletion());
    }

    @Test
    public void repeatModeCycles() {
        assertEquals(RepeatMode.REPEAT_ALL, RepeatMode.CONTINUE.cycle());
        assertEquals(RepeatMode.REPEAT_FOLDER, RepeatMode.REPEAT_ALL.cycle());
        assertEquals(RepeatMode.REPEAT_ONE, RepeatMode.REPEAT_FOLDER.cycle());
        assertEquals(RepeatMode.CONTINUE, RepeatMode.REPEAT_ONE.cycle());
    }
}
