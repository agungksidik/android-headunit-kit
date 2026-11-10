package com.agung.headhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The built-in USB music player: library index, play queue, MediaPlayer, audio focus, MediaSession and a
 * foreground notification. UI code talks to it from the main thread through {@link #get()}.
 */
public class PlayerService extends Service {

    static final String PREFS = "player";
    static final String KEY_AUTO_RESUME = "auto_resume";

    private static final String KEY_PATH = "path";
    private static final String KEY_POS = "pos";
    private static final String KEY_MODE = "mode";
    private static final String KEY_SHUFFLE = "shuffle";
    private static final String KEY_WAS_PLAYING = "was_playing";

    private static final String ACTION_TOGGLE = "com.agung.headhome.action.TOGGLE";
    private static final String ACTION_NEXT = "com.agung.headhome.action.NEXT";
    private static final String ACTION_PREV = "com.agung.headhome.action.PREV";

    private static final String CHANNEL_ID = "player";
    private static final int NOTIFICATION_ID = 1;
    private static final long SAVE_INTERVAL_MS = 5000;
    private static final long POLL_INTERVAL_MS = 15000;
    private static final long AUTO_RESUME_DELAY_MS = 3000;
    private static final int AUTO_RESUME_MAX_TRIES = 20;
    private static final int MAX_ERROR_STREAK = 3;
    private static final int ART_MAX_PX = 512;

    public interface Listener {
        void onPlayerChanged();
    }

    private static volatile PlayerService instance;
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    public static PlayerService get() {
        return instance;
    }

    public static void addListener(Listener l) {
        if (!LISTENERS.contains(l)) {
            LISTENERS.add(l);
        }
    }

    public static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }

    /** Starts the service when it is not running. Safe to call repeatedly. */
    public static void ensureStarted(Context ctx) {
        if (instance != null) {
            return;
        }
        try {
            ctx.startForegroundService(new Intent(ctx, PlayerService.class));
        } catch (RuntimeException e) {
            // Not allowed to start from the background right now: the next Home start retries.
        }
    }

    static boolean autoResumeEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_AUTO_RESUME, true);
    }

    static void setAutoResume(Context ctx, boolean on) {
        ctx.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_AUTO_RESUME, on).apply();
    }

    private static final AudioAttributes ATTRS = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService scanIo = Executors.newSingleThreadExecutor();
    private final ExecutorService tagIo = Executors.newSingleThreadExecutor();
    private final Random random = new Random();

    private SharedPreferences prefs;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private MediaSession session;
    private File cacheFile;

    private Library library = Library.EMPTY;
    private PlayQueue queue = new PlayQueue(Library.EMPTY.folders, random);
    private volatile boolean scanning;

    private MediaPlayer player;
    private int generation;
    private boolean prepared;
    private boolean playing;
    private boolean playWhenReady;
    private boolean resumeOnFocusGain;
    private int errorStreak;
    private String loadedPath;
    private long durationMs;
    private int pendingSeekMs;

    private String title = "";
    private String artist = "";
    private Bitmap art;

    private String restorePath;
    private int restorePosMs;
    private boolean autoResumePending;
    private int autoResumeTries;

    private final Runnable saver = new Runnable() {
        @Override
        public void run() {
            saveState();
            if (playing) {
                handler.postDelayed(this, SAVE_INTERVAL_MS);
            }
        }
    };

    private final Runnable poller = new Runnable() {
        @Override
        public void run() {
            pollRoots();
            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                resumeOnFocusGain = false;
                pauseInternal(false);
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (playing) {
                    resumeOnFocusGain = true;
                    pauseInternal(false);
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                setPlayerVolume(0.3f);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                setPlayerVolume(1f);
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false;
                    play();
                }
                break;
            default:
                break;
        }
    };

    // ---- lifecycle ----

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        cacheFile = new File(getFilesDir(), "library.idx");

        createChannel();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification());
        }

        session = new MediaSession(this, "HeadHomePlayer");
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                play();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onStop() {
                pause();
            }

            @Override
            public void onSkipToNext() {
                next();
            }

            @Override
            public void onSkipToPrevious() {
                prev();
            }

            @Override
            public void onSeekTo(long pos) {
                seekTo((int) pos);
            }

            @Override
            public void onCustomAction(String action, Bundle extras) {
                if ("cycle_repeat".equals(action)) {
                    cycleRepeat();
                } else if ("toggle_shuffle".equals(action)) {
                    toggleShuffle();
                }
            }
        }, handler);
        session.setActive(true);

        queue.setMode(RepeatMode.parse(prefs.getString(KEY_MODE, null), RepeatMode.REPEAT_ALL));
        queue.setShuffle(prefs.getBoolean(KEY_SHUFFLE, false));
        restorePath = prefs.getString(KEY_PATH, null);
        restorePosMs = prefs.getInt(KEY_POS, 0);
        autoResumePending = restorePath != null
                && prefs.getBoolean(KEY_WAS_PLAYING, false)
                && prefs.getBoolean(KEY_AUTO_RESUME, true);

        updateSession();
        loadLibrary();
        handler.postDelayed(poller, POLL_INTERVAL_MS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_TOGGLE.equals(action)) {
            toggle();
        } else if (ACTION_NEXT.equals(action)) {
            next();
        } else if (ACTION_PREV.equals(action)) {
            prev();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        saveState();
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        abandonFocus();
        if (session != null) {
            session.setActive(false);
            session.release();
        }
        scanIo.shutdownNow();
        tagIo.shutdownNow();
        instance = null;
        super.onDestroy();
    }

    // ---- state for the UI ----

    public Library library() {
        return library;
    }

    public boolean isScanning() {
        return scanning;
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean hasTrack() {
        return queue.current() != null;
    }

    public RepeatMode repeatMode() {
        return queue.mode();
    }

    public boolean shuffle() {
        return queue.shuffle();
    }

    public int folderIndex() {
        return queue.folderIndex();
    }

    public int trackIndex() {
        return queue.trackIndex();
    }

    public String title() {
        return title;
    }

    public String artist() {
        return artist;
    }

    public Bitmap art() {
        return art;
    }

    public String folderName() {
        Library.Folder f = queue.folder();
        return f != null ? f.name : "";
    }

    public int folderTrackCount() {
        Library.Folder f = queue.folder();
        return f != null ? f.tracks.size() : 0;
    }

    public long durationMs() {
        return durationMs;
    }

    public long positionMs() {
        if (player != null && prepared) {
            try {
                return player.getCurrentPosition();
            } catch (IllegalStateException e) {
                return 0;
            }
        }
        return pendingSeekMs;
    }

    // ---- controls (main thread) ----

    public void play() {
        if (queue.isEmpty()) {
            return;
        }
        if (player == null || !queue.current().path.equals(loadedPath)) {
            loadCurrent(true, 0);
            return;
        }
        playWhenReady = true;
        if (prepared && !playing) {
            startPlayback();
        }
    }

    public void pause() {
        pauseInternal(true);
    }

    public void toggle() {
        if (playing || (playWhenReady && !prepared)) {
            pause();
        } else {
            play();
        }
    }

    public void next() {
        Library.Track t = queue.next();
        if (t != null) {
            loadCurrent(true, 0);
        }
    }

    public void prev() {
        Library.Track t = queue.prev(positionMs());
        if (t == null) {
            return;
        }
        if (t.path.equals(loadedPath) && prepared && player != null) {
            seekTo(0);
            if (!playing) {
                play();
            }
        } else {
            loadCurrent(true, 0);
        }
    }

    public void seekTo(int ms) {
        if (player != null && prepared) {
            try {
                player.seekTo(Math.max(0, ms));
            } catch (IllegalStateException ignored) {
                // Player was released meanwhile.
            }
        } else {
            pendingSeekMs = Math.max(0, ms);
        }
        updateAll();
    }

    public void playTrack(int folder, int track) {
        queue.jumpTo(folder, track);
        loadCurrent(true, 0);
    }

    public void cycleRepeat() {
        queue.setMode(queue.mode().cycle());
        saveState();
        updateAll();
    }

    public void toggleShuffle() {
        queue.setShuffle(!queue.shuffle());
        saveState();
        updateAll();
    }

    public void rescan() {
        scanAndApply();
    }

    // ---- library ----

    private void loadLibrary() {
        scanning = true;
        scanIo.execute(() -> {
            File base = new File(LibraryScanner.DEFAULT_BASE);
            Map<String, String> sig = LibraryScanner.signatures(base);
            Library cached = LibraryCache.read(cacheFile);
            Library lib;
            if (cached != null && cached.signatures.equals(sig)) {
                lib = cached;
            } else {
                lib = LibraryScanner.scan(base);
                LibraryCache.write(cacheFile, lib);
            }
            final Library result = lib;
            handler.post(() -> applyLibrary(result));
        });
        notifyChanged();
    }

    private void scanAndApply() {
        scanning = true;
        notifyChanged();
        scanIo.execute(() -> {
            Library lib = LibraryScanner.scan(new File(LibraryScanner.DEFAULT_BASE));
            LibraryCache.write(cacheFile, lib);
            handler.post(() -> applyLibrary(lib));
        });
    }

    private void pollRoots() {
        if (scanning) {
            return;
        }
        scanIo.execute(() -> {
            File base = new File(LibraryScanner.DEFAULT_BASE);
            Map<String, String> sig = LibraryScanner.signatures(base);
            if (!sig.equals(library.signatures)) {
                handler.post(this::scanAndApply);
            }
        });
    }

    private void applyLibrary(Library lib) {
        scanning = false;
        RepeatMode mode = queue.mode();
        boolean shuffle = queue.shuffle();
        Library.Track cur = queue.current();
        String curPath = cur != null && loadedPath != null ? cur.path : null;
        String wanted = curPath != null ? curPath : restorePath;

        library = lib;
        PlayQueue q = new PlayQueue(lib.folders, random);
        q.setMode(mode);
        q.setShuffle(shuffle);
        boolean found = wanted != null && q.locate(wanted);
        queue = q;

        if (found) {
            if (loadedPath == null) {
                int seek = restorePosMs;
                restorePath = null;
                loadCurrent(false, seek);
                if (autoResumePending) {
                    autoResumePending = false;
                    autoResumeTries = 0;
                    handler.postDelayed(this::autoResume, AUTO_RESUME_DELAY_MS);
                }
            }
        } else if (loadedPath != null) {
            // The drive went away while a song was loaded.
            releasePlayer();
            loadedPath = null;
            playing = false;
        }
        if (loadedPath == null) {
            Library.Track c = queue.current();
            title = c != null ? c.displayName() : "";
            artist = c != null ? folderName() : "";
            art = null;
            durationMs = 0;
        }
        updateAll();
    }

    private void autoResume() {
        if (playing || queue.isEmpty()) {
            return;
        }
        Library.Track t = queue.current();
        if (t != null && new File(t.path).exists()) {
            play();
        } else if (++autoResumeTries < AUTO_RESUME_MAX_TRIES) {
            handler.postDelayed(this::autoResume, AUTO_RESUME_DELAY_MS);
        }
    }

    // ---- playback ----

    private void loadCurrent(boolean autoPlay, int seekMs) {
        releasePlayer();
        Library.Track t = queue.current();
        if (t == null) {
            loadedPath = null;
            updateAll();
            return;
        }
        final int gen = ++generation;
        playWhenReady = autoPlay;
        loadedPath = t.path;
        durationMs = 0;
        pendingSeekMs = seekMs;
        title = t.displayName();
        Library.Folder f = queue.folder();
        artist = f != null ? f.name : "";
        art = null;

        MediaPlayer mp = new MediaPlayer();
        player = mp;
        prepared = false;
        try {
            mp.setAudioAttributes(ATTRS);
            mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            mp.setDataSource(t.path);
            mp.setOnPreparedListener(p -> {
                if (gen != generation) {
                    return;
                }
                prepared = true;
                errorStreak = 0;
                durationMs = p.getDuration();
                if (pendingSeekMs > 0) {
                    p.seekTo(pendingSeekMs);
                    pendingSeekMs = 0;
                }
                if (playWhenReady) {
                    startPlayback();
                } else {
                    updateAll();
                }
            });
            mp.setOnCompletionListener(p -> {
                if (gen == generation) {
                    onCompleted();
                }
            });
            mp.setOnErrorListener((p, what, extra) -> {
                if (gen == generation) {
                    onPlayerError();
                }
                return true;
            });
            mp.prepareAsync();
        } catch (IOException | IllegalStateException | SecurityException | IllegalArgumentException e) {
            onPlayerError();
            return;
        }
        loadTags(t, gen);
        updateAll();
    }

    private void startPlayback() {
        if (player == null || !prepared) {
            return;
        }
        if (!requestFocus()) {
            playing = false;
            playWhenReady = false;
            updateAll();
            return;
        }
        try {
            player.start();
        } catch (IllegalStateException e) {
            onPlayerError();
            return;
        }
        playing = true;
        prefs.edit().putBoolean(KEY_WAS_PLAYING, true).apply();
        handler.removeCallbacks(saver);
        handler.postDelayed(saver, SAVE_INTERVAL_MS);
        updateAll();
    }

    private void pauseInternal(boolean byUser) {
        playWhenReady = false;
        if (byUser) {
            resumeOnFocusGain = false;
        }
        if (player != null && playing) {
            try {
                player.pause();
            } catch (IllegalStateException ignored) {
                // Released meanwhile.
            }
        }
        boolean wasPlaying = playing;
        playing = false;
        handler.removeCallbacks(saver);
        saveState();
        if (byUser) {
            prefs.edit().putBoolean(KEY_WAS_PLAYING, false).apply();
        }
        if (wasPlaying || byUser) {
            updateAll();
        }
    }

    private void onCompleted() {
        Library.Track t = queue.onCompletion();
        if (t == null) {
            playing = false;
            playWhenReady = false;
            handler.removeCallbacks(saver);
            prefs.edit().putBoolean(KEY_WAS_PLAYING, false).apply();
            updateAll();
        } else if (queue.mode() == RepeatMode.REPEAT_ONE && player != null) {
            try {
                player.seekTo(0);
                player.start();
            } catch (IllegalStateException e) {
                loadCurrent(true, 0);
            }
        } else {
            loadCurrent(true, 0);
        }
    }

    private void onPlayerError() {
        releasePlayer();
        loadedPath = null;
        playing = false;
        if (++errorStreak <= MAX_ERROR_STREAK) {
            Library.Track t = queue.next();
            if (t != null) {
                loadCurrent(true, 0);
                return;
            }
        }
        playWhenReady = false;
        updateAll();
    }

    private void releasePlayer() {
        generation++;
        if (player != null) {
            try {
                player.reset();
                player.release();
            } catch (RuntimeException ignored) {
                // Already gone.
            }
            player = null;
        }
        prepared = false;
    }

    private void setPlayerVolume(float v) {
        if (player != null) {
            try {
                player.setVolume(v, v);
            } catch (IllegalStateException ignored) {
                // Released meanwhile.
            }
        }
    }

    private boolean requestFocus() {
        if (focusRequest == null) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(ATTRS)
                    .setOnAudioFocusChangeListener(focusListener, handler)
                    .build();
        }
        return audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonFocus() {
        if (focusRequest != null && audio != null) {
            audio.abandonAudioFocusRequest(focusRequest);
        }
    }

    private void saveState() {
        SharedPreferences.Editor e = prefs.edit();
        e.putString(KEY_MODE, queue.mode().name());
        e.putBoolean(KEY_SHUFFLE, queue.shuffle());
        if (loadedPath != null) {
            e.putString(KEY_PATH, loadedPath);
            e.putInt(KEY_POS, (int) positionMs());
        }
        e.apply();
    }

    // ---- tags and artwork ----

    private void loadTags(Library.Track t, int gen) {
        tagIo.execute(() -> {
            String tagTitle = null;
            String tagArtist = null;
            Bitmap tagArt = null;
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(t.path);
                tagTitle = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                tagArtist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                byte[] pic = r.getEmbeddedPicture();
                if (pic != null) {
                    tagArt = decodeScaled(pic);
                }
            } catch (RuntimeException ignored) {
                // Unreadable tags: the file name and folder fallbacks stay.
            } finally {
                try {
                    r.release();
                } catch (RuntimeException | IOException ignored) {
                    // Nothing to release.
                }
            }
            final String ft = tagTitle;
            final String fa = tagArtist;
            final Bitmap fb = tagArt;
            handler.post(() -> {
                if (gen != generation) {
                    return;
                }
                if (ft != null && !ft.trim().isEmpty()) {
                    title = ft.trim();
                }
                if (fa != null && !fa.trim().isEmpty()) {
                    artist = fa.trim();
                }
                art = fb;
                updateAll();
            });
        });
    }

    private static Bitmap decodeScaled(byte[] data) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        int sample = 1;
        while (o.outWidth / sample > ART_MAX_PX * 2 || o.outHeight / sample > ART_MAX_PX * 2) {
            sample *= 2;
        }
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, d);
        if (bmp == null) {
            return null;
        }
        int max = Math.max(bmp.getWidth(), bmp.getHeight());
        if (max > ART_MAX_PX) {
            float s = (float) ART_MAX_PX / max;
            bmp = Bitmap.createScaledBitmap(bmp, Math.round(bmp.getWidth() * s), Math.round(bmp.getHeight() * s), true);
        }
        return bmp;
    }

    // ---- session, notification, listeners ----

    private void updateAll() {
        updateSession();
        updateNotification();
        notifyChanged();
    }

    private void notifyChanged() {
        for (Listener l : LISTENERS) {
            l.onPlayerChanged();
        }
    }

    private void updateSession() {
        if (session == null) {
            return;
        }
        MediaMetadata.Builder mb = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, folderName())
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs);
        if (art != null) {
            mb.putBitmap(MediaMetadata.METADATA_KEY_ART, art);
        }
        session.setMetadata(mb.build());

        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP;
        int state = playing ? PlaybackState.STATE_PLAYING
                : loadedPath != null ? PlaybackState.STATE_PAUSED : PlaybackState.STATE_STOPPED;
        PlaybackState.Builder sb = new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, positionMs(), playing ? 1f : 0f, SystemClock.elapsedRealtime());
        Bundle repeatExtras = new Bundle();
        repeatExtras.putString("mode", queue.mode().name());
        sb.addCustomAction(new PlaybackState.CustomAction.Builder("cycle_repeat",
                getString(PlayerUi.repeatLabel(queue.mode())), PlayerUi.repeatIcon(queue.mode()))
                .setExtras(repeatExtras).build());
        Bundle shuffleExtras = new Bundle();
        shuffleExtras.putBoolean("shuffle", queue.shuffle());
        sb.addCustomAction(new PlaybackState.CustomAction.Builder("toggle_shuffle",
                getString(R.string.shuffle), R.drawable.ic_shuffle).setExtras(shuffleExtras).build());
        session.setPlaybackState(sb.build());
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, getString(R.string.channel_player),
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private void updateNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, buildNotification());
    }

    private PendingIntent actionIntent(String action, int requestCode) {
        Intent i = new Intent(this, PlayerService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification.Action action(int icon, int label, String action, int requestCode) {
        return new Notification.Action.Builder(Icon.createWithResource(this, icon), getString(label),
                actionIntent(action, requestCode)).build();
    }

    private Notification buildNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, PlayerActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music)
                .setContentTitle(title.isEmpty() ? getString(R.string.dock_music) : title)
                .setContentText(artist)
                .setContentIntent(open)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(action(R.drawable.ic_prev, R.string.prev, ACTION_PREV, 1))
                .addAction(action(playing ? R.drawable.ic_pause : R.drawable.ic_play,
                        playing ? R.string.pause : R.string.play, ACTION_TOGGLE, 2))
                .addAction(action(R.drawable.ic_next, R.string.next, ACTION_NEXT, 3));
        if (art != null) {
            b.setLargeIcon(art);
        }
        Notification.MediaStyle style = new Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2);
        if (session != null) {
            style.setMediaSession(session.getSessionToken());
        }
        b.setStyle(style);
        return b.build();
    }
}
