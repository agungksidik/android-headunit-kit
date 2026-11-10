package com.agung.headhome;

import java.util.List;
import java.util.Random;

/**
 * Playback order over a library: folder by folder in natural order, songs in natural order, or a random
 * permutation inside the current folder when shuffle is on. Pure logic, no Android types.
 * Methods that move return the new current track, or null when playback should stop.
 */
public final class PlayQueue {

    public static final long RESTART_THRESHOLD_MS = 3000;

    private final List<Library.Folder> folders;
    private final Random random;
    private int folderIdx;
    private int[] order = new int[0];
    private int orderPos;
    private RepeatMode mode = RepeatMode.REPEAT_ALL;
    private boolean shuffle;

    public PlayQueue(List<Library.Folder> folders, Random random) {
        this.folders = folders;
        this.random = random;
        if (!folders.isEmpty()) {
            buildOrder(-1);
        }
    }

    public boolean isEmpty() {
        return folders.isEmpty() || order.length == 0;
    }

    public int folderCount() {
        return folders.size();
    }

    public int folderIndex() {
        return folderIdx;
    }

    /** Index of the current track in the folder's natural order. */
    public int trackIndex() {
        return isEmpty() ? 0 : order[orderPos];
    }

    public Library.Folder folder() {
        return isEmpty() ? null : folders.get(folderIdx);
    }

    public Library.Track current() {
        return isEmpty() ? null : folders.get(folderIdx).tracks.get(order[orderPos]);
    }

    public RepeatMode mode() {
        return mode;
    }

    public void setMode(RepeatMode mode) {
        this.mode = mode;
    }

    public boolean shuffle() {
        return shuffle;
    }

    /** Turning shuffle on or off keeps the current track and re-orders the rest of its folder. */
    public void setShuffle(boolean on) {
        if (on == shuffle) {
            return;
        }
        shuffle = on;
        if (!isEmpty()) {
            buildOrder(order[orderPos]);
        }
    }

    public void jumpTo(int folder, int track) {
        if (folders.isEmpty()) {
            return;
        }
        folderIdx = Math.max(0, Math.min(folder, folders.size() - 1));
        int n = folders.get(folderIdx).tracks.size();
        buildOrder(n == 0 ? -1 : Math.max(0, Math.min(track, n - 1)));
    }

    /** Makes the track with this path current. Returns false when it is not in the library. */
    public boolean locate(String path) {
        for (int f = 0; f < folders.size(); f++) {
            List<Library.Track> tracks = folders.get(f).tracks;
            for (int t = 0; t < tracks.size(); t++) {
                if (tracks.get(t).path.equals(path)) {
                    jumpTo(f, t);
                    return true;
                }
            }
        }
        return false;
    }

    /** Next button. At the end of the last folder CONTINUE stops; the other modes wrap. */
    public Library.Track next() {
        return advance(mode != RepeatMode.CONTINUE);
    }

    /** The current song ended on its own. */
    public Library.Track onCompletion() {
        if (isEmpty()) {
            return null;
        }
        if (mode == RepeatMode.REPEAT_ONE) {
            return current();
        }
        return advance(mode == RepeatMode.REPEAT_ALL);
    }

    /** Previous button: restarts the song after 3 s, else goes back across folder boundaries. */
    public Library.Track prev(long positionMs) {
        if (isEmpty()) {
            return null;
        }
        if (positionMs > RESTART_THRESHOLD_MS) {
            return current();
        }
        if (orderPos > 0) {
            orderPos--;
            return current();
        }
        if (mode == RepeatMode.REPEAT_FOLDER) {
            orderPos = order.length - 1;
            return current();
        }
        if (folderIdx > 0) {
            folderIdx--;
            buildOrder(-1);
            orderPos = order.length - 1;
            return current();
        }
        if (mode == RepeatMode.REPEAT_ALL || mode == RepeatMode.REPEAT_ONE) {
            folderIdx = folders.size() - 1;
            buildOrder(-1);
            orderPos = order.length - 1;
            return current();
        }
        return current();
    }

    private Library.Track advance(boolean wrapAll) {
        if (isEmpty()) {
            return null;
        }
        if (orderPos + 1 < order.length) {
            orderPos++;
            return current();
        }
        if (mode == RepeatMode.REPEAT_FOLDER) {
            buildOrder(-1);
            return current();
        }
        if (folderIdx + 1 < folders.size()) {
            folderIdx++;
            buildOrder(-1);
            return current();
        }
        if (wrapAll) {
            folderIdx = 0;
            buildOrder(-1);
            return current();
        }
        return null;
    }

    /** Builds the play order of the current folder. firstTrack >= 0 is the track to start with. */
    private void buildOrder(int firstTrack) {
        int n = folders.get(folderIdx).tracks.size();
        order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        orderPos = 0;
        if (n == 0) {
            return;
        }
        if (!shuffle) {
            orderPos = firstTrack >= 0 ? firstTrack : 0;
            return;
        }
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = order[i];
            order[i] = order[j];
            order[j] = tmp;
        }
        if (firstTrack >= 0) {
            for (int i = 0; i < n; i++) {
                if (order[i] == firstTrack) {
                    order[i] = order[0];
                    order[0] = firstTrack;
                    break;
                }
            }
        }
    }
}
