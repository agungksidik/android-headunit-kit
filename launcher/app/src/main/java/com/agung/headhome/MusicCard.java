package com.agung.headhome;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;

/**
 * The music card. Shows a playing media session of another app, or the built-in USB player when nothing
 * else is playing. Also hosts the MCU volume row.
 */
final class MusicCard implements PlayerService.Listener {

    private enum Source { NONE, EXTERNAL, OURS }

    private static final long VOLUME_REPEAT_START_MS = 400;
    private static final long VOLUME_REPEAT_MS = 150;

    private final Context ctx;
    private final MediaSessionManager sessions;
    private final ComponentName listenerComponent;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final TextView header;
    private final ImageView art;
    private final TextView title;
    private final TextView artist;
    private final TextView album;
    private final ProgressBar progress;
    private final TextView elapsed;
    private final TextView duration;
    private final ImageButton play;
    private final ImageButton shuffle;
    private final ImageButton repeat;
    private final ProgressBar volumeBar;
    private final TextView volumeValue;

    private MediaController current;
    private Source source = Source.NONE;
    private Bitmap shownArt;
    private boolean started;
    private int volume;
    private int volumeMax = 40;
    private ContentObserver volumeObserver;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            render();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            render();
        }

        @Override
        public void onSessionDestroyed() {
            refreshSessions();
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener = this::select;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!started) {
                return;
            }
            refreshSessions();
            render();
            handler.postDelayed(this, 1000);
        }
    };

    MusicCard(Context ctx, View root) {
        this.ctx = ctx;
        this.sessions = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
        this.listenerComponent = new ComponentName(ctx, MediaListener.class);
        header = root.findViewById(R.id.music_header);
        art = root.findViewById(R.id.music_art);
        title = root.findViewById(R.id.music_title);
        artist = root.findViewById(R.id.music_artist);
        album = root.findViewById(R.id.music_album);
        progress = root.findViewById(R.id.music_progress);
        elapsed = root.findViewById(R.id.music_elapsed);
        duration = root.findViewById(R.id.music_duration);
        play = root.findViewById(R.id.music_play);
        shuffle = root.findViewById(R.id.music_shuffle);
        repeat = root.findViewById(R.id.music_repeat);
        volumeBar = root.findViewById(R.id.vol_bar);
        volumeValue = root.findViewById(R.id.vol_value);
        ImageButton prev = root.findViewById(R.id.music_prev);
        ImageButton next = root.findViewById(R.id.music_next);

        final float radius = 14 * ctx.getResources().getDisplayMetrics().density;
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        art.setClipToOutline(true);

        prev.setOnClickListener(v -> transport(Action.PREV));
        play.setOnClickListener(v -> transport(Action.TOGGLE));
        next.setOnClickListener(v -> transport(Action.NEXT));
        shuffle.setOnClickListener(v -> {
            PlayerService p = PlayerService.get();
            if (p != null) {
                p.toggleShuffle();
            }
        });
        repeat.setOnClickListener(v -> PlayerUi.cycleRepeat(ctx, PlayerService.get()));
        root.setOnClickListener(v -> openSourceApp());

        holdToRepeat(root.findViewById(R.id.vol_minus), -1);
        holdToRepeat(root.findViewById(R.id.vol_plus), 1);
        readVolume();
        render();
    }

    void start() {
        started = true;
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent);
        } catch (SecurityException ignored) {
            // Notification access not granted: only the built-in player can show.
        }
        PlayerService.addListener(this);
        volumeObserver = McuVolume.observe(ctx, handler, this::readVolume);
        readVolume();
        refreshSessions();
        render();
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 1000);
    }

    void stop() {
        started = false;
        handler.removeCallbacks(tick);
        PlayerService.removeListener(this);
        if (volumeObserver != null) {
            ctx.getContentResolver().unregisterContentObserver(volumeObserver);
            volumeObserver = null;
        }
        try {
            sessions.removeOnActiveSessionsChangedListener(sessionsListener);
        } catch (SecurityException ignored) {
            // Nothing was registered.
        }
        if (current != null) {
            current.unregisterCallback(callback);
            current = null;
        }
    }

    /** Re-applies the localized texts, e.g. after a locale change. */
    void refreshTexts() {
        render();
    }

    @Override
    public void onPlayerChanged() {
        render();
    }

    // ---- volume ----

    private void readVolume() {
        volumeMax = McuVolume.max(ctx);
        volume = McuVolume.get(ctx);
        showVolume();
    }

    private void showVolume() {
        volumeBar.setMax(volumeMax);
        volumeBar.setProgress(volume);
        volumeValue.setText(String.valueOf(volume));
    }

    private void stepVolume(int delta) {
        int v = Math.max(0, Math.min(volumeMax, volume + delta));
        if (v == volume) {
            return;
        }
        volume = v;
        showVolume();
        McuVolume.set(ctx, v);
    }

    private void holdToRepeat(View button, int delta) {
        final Runnable repeater = new Runnable() {
            @Override
            public void run() {
                stepVolume(delta);
                handler.postDelayed(this, VOLUME_REPEAT_MS);
            }
        };
        button.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    stepVolume(delta);
                    handler.postDelayed(repeater, VOLUME_REPEAT_START_MS);
                    return true;
                case MotionEvent.ACTION_UP:
                    v.performClick();
                    // fall through
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    handler.removeCallbacks(repeater);
                    return true;
                default:
                    return true;
            }
        });
    }

    // ---- sessions ----

    private void refreshSessions() {
        List<MediaController> list = null;
        try {
            list = sessions.getActiveSessions(listenerComponent);
        } catch (SecurityException ignored) {
            // Notification access not granted.
        }
        select(list);
    }

    private static boolean isPlaying(MediaController c) {
        PlaybackState s = c.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }

    /** Picks the playing session of another app. The built-in player's own session is ignored here. */
    private void select(List<MediaController> list) {
        MediaController pick = null;
        if (list != null) {
            for (MediaController c : list) {
                if (ctx.getPackageName().equals(c.getPackageName())) {
                    continue;
                }
                if (isPlaying(c)) {
                    pick = c;
                    break;
                }
                if (pick == null) {
                    pick = c;
                }
            }
        }
        boolean same = pick != null && current != null && current.getSessionToken().equals(pick.getSessionToken());
        if (same) {
            return;
        }
        if (current != null) {
            current.unregisterCallback(callback);
        }
        current = pick;
        if (current != null) {
            current.registerCallback(callback, handler);
        }
        render();
    }

    /** Another app owns the card only while it is actually playing; otherwise it is the built-in player. */
    private Source chooseSource() {
        PlayerService ours = PlayerService.get();
        boolean ourPlaying = ours != null && ours.isPlaying();
        boolean extPlaying = current != null && isPlaying(current);
        return extPlaying && !ourPlaying ? Source.EXTERNAL : Source.OURS;
    }

    // ---- rendering ----

    private void render() {
        source = chooseSource();
        boolean ours = source == Source.OURS;
        shuffle.setVisibility(ours ? View.VISIBLE : View.INVISIBLE);
        repeat.setVisibility(ours ? View.VISIBLE : View.INVISIBLE);
        switch (source) {
            case OURS:
                PlayerService p = PlayerService.get();
                if (p != null) {
                    renderOurs(p);
                } else {
                    PlayerService.ensureStarted(ctx);
                    renderStarting();
                }
                break;
            case EXTERNAL:
                renderExternal();
                break;
            default:
                renderNone();
                break;
        }
    }

    private void renderNone() {
        header.setText(R.string.music_header);
        title.setText(R.string.no_music);
        artist.setText("");
        album.setText("");
        setArt(null);
        progress.setProgress(0);
        elapsed.setText("");
        duration.setText("");
        play.setImageResource(R.drawable.ic_play);
        play.setContentDescription(ctx.getString(R.string.play));
    }

    /** The player service is not running yet: show the USB header and a scanning hint. */
    private void renderStarting() {
        header.setText(ctx.getString(R.string.music_header_source, ctx.getString(R.string.source_usb)));
        title.setText(R.string.scanning_music);
        artist.setText("");
        album.setText("");
        setArt(null);
        progress.setProgress(0);
        elapsed.setText("");
        duration.setText("");
        play.setImageResource(R.drawable.ic_play);
        play.setContentDescription(ctx.getString(R.string.play));
    }

    private void renderOurs(PlayerService p) {
        header.setText(ctx.getString(R.string.music_header_source, ctx.getString(R.string.source_usb)));
        if (p.hasTrack()) {
            title.setText(p.title().isEmpty() ? ctx.getString(R.string.no_music) : p.title());
            artist.setText(ctx.getString(R.string.folder_position, p.folderName(),
                    p.trackIndex() + 1, p.folderTrackCount()));
            album.setText(p.artist());
        } else {
            title.setText(p.isScanning() ? R.string.scanning_music : R.string.no_usb);
            artist.setText("");
            album.setText("");
        }
        setArt(p.art());
        PlayerUi.updateButtons(ctx, p, shuffle, play, repeat);
        long dur = p.durationMs();
        long pos = p.positionMs();
        elapsed.setText(p.hasTrack() ? PlayerUi.format(pos) : "");
        if (dur > 0) {
            duration.setText(PlayerUi.format(dur));
            progress.setProgress((int) (1000L * Math.min(pos, dur) / dur));
        } else {
            duration.setText(p.hasTrack() ? "--:--" : "");
            progress.setProgress(0);
        }
    }

    private void renderExternal() {
        MediaMetadata md = current.getMetadata();
        PlaybackState st = current.getPlaybackState();

        header.setText(ctx.getString(R.string.music_header_source, sourceLabel(current.getPackageName())));
        CharSequence t = md != null ? md.getText(MediaMetadata.METADATA_KEY_TITLE) : null;
        title.setText(t != null && t.length() > 0 ? t : ctx.getString(R.string.no_music));
        artist.setText(text(md, MediaMetadata.METADATA_KEY_ARTIST));
        album.setText(text(md, MediaMetadata.METADATA_KEY_ALBUM));
        Bitmap bmp = null;
        if (md != null) {
            bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (bmp == null) {
                bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            }
        }
        setArt(bmp);

        boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
        play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        play.setContentDescription(ctx.getString(playing ? R.string.pause : R.string.play));

        long dur = md != null ? md.getLong(MediaMetadata.METADATA_KEY_DURATION) : 0;
        long pos = position(st, dur);
        elapsed.setText(PlayerUi.format(pos));
        if (dur > 0) {
            duration.setText(PlayerUi.format(dur));
            progress.setProgress((int) (1000L * pos / dur));
        } else {
            duration.setText("--:--");
            progress.setProgress(0);
        }
    }

    private static CharSequence text(MediaMetadata md, String key) {
        if (md == null) {
            return "";
        }
        CharSequence c = md.getText(key);
        return c != null ? c : "";
    }

    private void setArt(Bitmap bmp) {
        if (bmp == shownArt && (bmp != null || art.getDrawable() != null)) {
            return;
        }
        shownArt = bmp;
        if (bmp != null) {
            art.setPadding(0, 0, 0, 0);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.clearColorFilter();
            art.setImageBitmap(bmp);
        } else {
            int pad = (int) (24 * ctx.getResources().getDisplayMetrics().density);
            art.setPadding(pad, pad, pad, pad);
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setImageResource(R.drawable.ic_note);
            art.setColorFilter(ctx.getColor(R.color.muted));
        }
    }

    private static long position(PlaybackState st, long duration) {
        if (st == null) {
            return 0;
        }
        long pos = st.getPosition();
        if (pos < 0) {
            return 0;
        }
        if (st.getState() == PlaybackState.STATE_PLAYING) {
            long delta = SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime();
            pos += (long) (delta * st.getPlaybackSpeed());
        }
        if (duration > 0 && pos > duration) {
            pos = duration;
        }
        return Math.max(0, pos);
    }

    private String sourceLabel(String pkg) {
        PackageManager pm = ctx.getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    // ---- actions ----

    private enum Action { PREV, TOGGLE, NEXT }

    private void transport(Action action) {
        if (source == Source.OURS) {
            PlayerService p = PlayerService.get();
            if (p == null) {
                return;
            }
            switch (action) {
                case PREV:
                    p.prev();
                    break;
                case NEXT:
                    p.next();
                    break;
                default:
                    p.toggle();
            }
            return;
        }
        if (current == null) {
            return;
        }
        try {
            MediaController.TransportControls tc = current.getTransportControls();
            switch (action) {
                case PREV:
                    tc.skipToPrevious();
                    break;
                case NEXT:
                    tc.skipToNext();
                    break;
                default:
                    PlaybackState st = current.getPlaybackState();
                    if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                        tc.pause();
                    } else {
                        tc.play();
                    }
            }
        } catch (SecurityException ignored) {
            // The session went away or access was revoked.
        }
    }

    private void openSourceApp() {
        if (source == Source.OURS) {
            ctx.startActivity(new Intent(ctx, PlayerActivity.class));
            return;
        }
        if (current == null) {
            return;
        }
        try {
            PendingIntent pi = current.getSessionActivity();
            if (pi != null) {
                pi.send();
                return;
            }
            Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(current.getPackageName());
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(launch);
            }
        } catch (PendingIntent.CanceledException | SecurityException ignored) {
            // The app cannot be opened from here.
        }
    }
}
