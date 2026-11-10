package com.agung.headhome;

import android.content.Context;
import android.widget.ImageButton;
import android.widget.Toast;

import java.util.Locale;

/** Icons, labels and small helpers shared by the Home music card and the player screen. */
final class PlayerUi {

    private PlayerUi() {
    }

    static int repeatIcon(RepeatMode m) {
        switch (m) {
            case CONTINUE:
                return R.drawable.ic_next_folder;
            case REPEAT_FOLDER:
                return R.drawable.ic_repeat_folder;
            case REPEAT_ONE:
                return R.drawable.ic_repeat_one;
            default:
                return R.drawable.ic_repeat;
        }
    }

    static int repeatLabel(RepeatMode m) {
        switch (m) {
            case CONTINUE:
                return R.string.repeat_continue;
            case REPEAT_FOLDER:
                return R.string.repeat_folder;
            case REPEAT_ONE:
                return R.string.repeat_one;
            default:
                return R.string.repeat_all;
        }
    }

    /** Applies the player state to shuffle, play and repeat buttons. */
    static void updateButtons(Context ctx, PlayerService p, ImageButton shuffle, ImageButton play, ImageButton repeat) {
        boolean playing = p != null && p.isPlaying();
        play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        play.setContentDescription(ctx.getString(playing ? R.string.pause : R.string.play));
        boolean on = p != null && p.shuffle();
        shuffle.setColorFilter(ctx.getColor(on ? R.color.accent : R.color.text));
        RepeatMode m = p != null ? p.repeatMode() : RepeatMode.REPEAT_ALL;
        repeat.setImageResource(repeatIcon(m));
        repeat.setContentDescription(ctx.getString(repeatLabel(m)));
    }

    static void cycleRepeat(Context ctx, PlayerService p) {
        if (p == null) {
            return;
        }
        p.cycleRepeat();
        Toast.makeText(ctx, repeatLabel(p.repeatMode()), Toast.LENGTH_SHORT).show();
    }

    static String format(long ms) {
        long total = Math.max(0, ms) / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.ROOT, "%d:%02d", m, s);
    }
}
