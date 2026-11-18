package com.agung.headhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Custom Sound: our own screen for the factory DSP chip. See {@link SoundEngine} for the state. */
public class SoundActivity extends Activity implements SoundEngine.Listener {

    private static final String[] KNOB_KEYS = {"level", "bass", "clarity", "vocal", "treble"};
    private static final int[] KNOB_NAMES = {R.string.sound_level, R.string.sound_bass, R.string.sound_clarity,
            R.string.sound_vocal, R.string.sound_treble};
    private static final int[] KNOB_HINTS = {R.string.sound_hint_all, R.string.sound_hint_bass,
            R.string.sound_hint_clarity, R.string.sound_hint_vocal, R.string.sound_hint_treble};
    private static final int[] SEAT_NAMES = {R.string.sound_seat_driver, R.string.sound_seat_front,
            R.string.sound_seat_all, R.string.sound_seat_rear};

    /** Headroom badge: green up to +8 dB, amber above +8, red above +11. */
    private static final int HEADROOM_AMBER_ABOVE_DB = 8;
    private static final int HEADROOM_RED_ABOVE_DB = 11;

    private SoundEngine engine;
    private boolean binding;
    private boolean advanced;
    private int selectedBand = 5;

    private EqCurveView curve;
    private CabinView cabin;
    private TextView headroom;
    private Switch modeSwitch;
    private Switch limiterSwitch;
    private Switch smartSwitch;
    private View knobsRow;
    private View advPanel;
    private TextView bandHz;
    private TextView bandDb;
    private TextView allLevel;
    private TextView abButton;
    /** Card order: five styles, the working "Preset Saya" (null), then the genres. */
    private static final String[] CARD_KEYS = {"flat_atas", "nendang", "vokal", "datar", "malam", null,
            "koplo", "dangdut", "akustik", "rock", "jazz", "dj_edm", "pop", "serba_bisa"};

    private final View[] presetCards = new View[CARD_KEYS.length];
    private int lastActiveCard = -2;
    private android.widget.HorizontalScrollView presetScroll;
    private final View[] knobs = new View[5];
    private final TextView[] seatButtons = new TextView[4];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.sound_screen_title);
        setContentView(R.layout.activity_sound);
        engine = SoundEngine.get(this);

        findViewById(R.id.sound_back).setOnClickListener(v -> finish());
        curve = findViewById(R.id.sound_curve);
        cabin = findViewById(R.id.sound_cabin);
        headroom = findViewById(R.id.sound_headroom);
        modeSwitch = findViewById(R.id.sound_mode32);
        limiterSwitch = findViewById(R.id.sound_limiter);
        smartSwitch = findViewById(R.id.sound_smart);
        knobsRow = findViewById(R.id.sound_knobs);
        advPanel = findViewById(R.id.sound_adv);
        bandHz = findViewById(R.id.sound_band_hz);
        bandDb = findViewById(R.id.sound_band_db);
        allLevel = findViewById(R.id.sound_all_level);
        abButton = findViewById(R.id.sound_ab);

        presetScroll = findViewById(R.id.presets_scroll);
        buildPresetCards();
        final float cardStep = (184 + 10) * getResources().getDisplayMetrics().density;
        findViewById(R.id.presets_scroll_left).setOnClickListener(v -> presetScroll.smoothScrollBy(-(int) cardStep, 0));
        findViewById(R.id.presets_scroll_right).setOnClickListener(v -> presetScroll.smoothScrollBy((int) cardStep, 0));
        presetScroll.setOnScrollChangeListener((v, x, y, ox, oy) -> updateScrollHints());
        presetScroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateScrollHints());
        buildKnobs();
        buildSeatButtons();

        curve.setListener(band -> {
            selectedBand = band;
            refresh();
        });
        modeSwitch.setOnCheckedChangeListener((b, on) -> {
            if (!binding) {
                advanced = on;
                refresh();
            }
        });
        limiterSwitch.setOnCheckedChangeListener((b, on) -> {
            if (!binding) {
                engine.setLimiter(on);
            }
        });
        smartSwitch.setOnCheckedChangeListener((b, on) -> {
            if (!binding) {
                engine.setSmartLoudness(on);
            }
        });
        findViewById(R.id.sound_band_prev).setOnClickListener(v -> {
            selectedBand = Math.max(0, selectedBand - 1);
            refresh();
        });
        findViewById(R.id.sound_band_next).setOnClickListener(v -> {
            selectedBand = Math.min(SoundPreset.BANDS - 1, selectedBand + 1);
            refresh();
        });
        findViewById(R.id.sound_band_down).setOnClickListener(v -> engine.stepBand(selectedBand, -1));
        findViewById(R.id.sound_band_up).setOnClickListener(v -> engine.stepBand(selectedBand, 1));
        findViewById(R.id.sound_all_down).setOnClickListener(v -> engine.step("level", -1));
        findViewById(R.id.sound_all_up).setOnClickListener(v -> engine.step("level", 1));
        findViewById(R.id.sound_flatten).setOnClickListener(v -> engine.flatten());
        abButton.setOnClickListener(v -> engine.toggleB());
        findViewById(R.id.sound_factory).setOnClickListener(v -> Launch.start(this, Launch.NWD_DSP));
        findViewById(R.id.sound_presets_button).setOnClickListener(v -> showPresetSheet());
    }

    @Override
    protected void onStart() {
        super.onStart();
        engine.addListener(this);
        refresh();
    }

    @Override
    protected void onStop() {
        super.onStop();
        engine.removeListener(this);
    }

    @Override
    public void onSoundChanged() {
        refresh();
    }

    // ---- building ----

    private void buildPresetCards() {
        ViewGroup row = findViewById(R.id.presets_row);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < presetCards.length; i++) {
            View card = inflater.inflate(R.layout.item_preset_card, row, false);
            final int index = i;
            card.setOnClickListener(v -> {
                if (CARD_KEYS[index] != null) {
                    engine.pickBuiltin(CARD_KEYS[index]);
                } else {
                    engine.pickCustom();
                }
            });
            presetCards[i] = card;
            row.addView(card);
        }
    }

    private void buildKnobs() {
        ViewGroup row = (ViewGroup) knobsRow;
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < knobs.length; i++) {
            View k = inflater.inflate(R.layout.item_knob, row, false);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) k.getLayoutParams();
            lp.setMarginStart(i == 0 ? 0 : (int) (5 * getResources().getDisplayMetrics().density));
            lp.setMarginEnd(i == knobs.length - 1 ? 0 : (int) (5 * getResources().getDisplayMetrics().density));
            final String key = KNOB_KEYS[i];
            String name = getString(KNOB_NAMES[i]);
            ((TextView) k.findViewById(R.id.knob_name)).setText(name);
            ((TextView) k.findViewById(R.id.knob_band)).setText(KNOB_HINTS[i]);
            k.findViewById(R.id.knob_minus).setContentDescription(getString(R.string.sound_down_fmt, name));
            k.findViewById(R.id.knob_plus).setContentDescription(getString(R.string.sound_up_fmt, name));
            k.findViewById(R.id.knob_minus).setOnClickListener(v -> engine.step(key, -1));
            k.findViewById(R.id.knob_plus).setOnClickListener(v -> engine.step(key, 1));
            if (i == 0) {
                k.setBackgroundResource(R.drawable.bg_knob_level);
            }
            knobs[i] = k;
            row.addView(k);
        }
    }

    private void buildSeatButtons() {
        ViewGroup[] rows = {findViewById(R.id.sound_seat_row1), findViewById(R.id.sound_seat_row2)};
        int gap = (int) (4 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < seatButtons.length; i++) {
            TextView b = new TextView(this);
            b.setText(SEAT_NAMES[i]);
            b.setGravity(Gravity.CENTER);
            b.setTextSize(14);
            b.setTypeface(b.getTypeface(), android.graphics.Typeface.BOLD);
            b.setClickable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            lp.setMarginStart(i % 2 == 0 ? 0 : gap);
            lp.setMarginEnd(i % 2 == 0 ? gap : 0);
            final int seat = i;
            b.setOnClickListener(v -> engine.setSeat(seat));
            rows[i / 2].addView(b, lp);
            seatButtons[i] = b;
        }
    }

    // ---- refresh ----

    private static String signed(int v) {
        return (v > 0 ? "+" : "") + v;
    }

    private void refresh() {
        binding = true;
        SoundEngine e = engine;
        int[] gains = e.showB ? new int[SoundPreset.BANDS] : e.userGains();
        int level = e.showB ? 0 : e.working.level;

        // Preset cards
        int accent = getColor(R.color.accent);
        for (int i = 0; i < presetCards.length; i++) {
            View card = presetCards[i];
            String key = CARD_KEYS[i];
            boolean builtin = key != null;
            boolean active = builtin
                    ? e.activeId.equals(SoundEngine.ID_BUILTIN + key)
                    : e.activeId.equals(SoundEngine.ID_CUSTOM) || e.activeId.startsWith(SoundEngine.ID_USER);
            int[] mini;
            String name;
            String tag;
            if (builtin) {
                mini = SoundCurve.gains(SoundPreset.builtin(key));
                name = SoundEngine.builtinLabel(this, key);
                tag = getString(tagFor(key));
            } else {
                mini = SoundCurve.gains(e.working);
                boolean user = e.activeId.startsWith(SoundEngine.ID_USER);
                name = user ? e.activeLabel(this) : getString(R.string.sound_preset_mine);
                tag = getString(user ? R.string.sound_tag_user : R.string.sound_tag_mine);
            }
            if (active && lastActiveCard != i) {
                lastActiveCard = i;
                final View target = card;
                presetScroll.post(() -> presetScroll.smoothScrollTo(
                        Math.max(0, target.getLeft() - (presetScroll.getWidth() - target.getWidth()) / 2), 0));
            }
            card.setBackgroundResource(active ? R.drawable.bg_preset_card_active : R.drawable.bg_preset_card);
            ((MiniCurveView) card.findViewById(R.id.preset_mini)).set(mini, active ? accent : 0xFF5D636C);
            ((TextView) card.findViewById(R.id.preset_name)).setText(name);
            ((TextView) card.findViewById(R.id.preset_tag)).setText(tag);
        }

        // Curve and headroom
        curve.set(gains, level, advanced, selectedBand);
        int peak = SoundCurve.peak(gains);
        if (!e.applied) {
            headroom.setText(R.string.sound_not_applied);
            headroom.setTextColor(0xFFC4C8CE);
            headroom.setBackgroundResource(R.drawable.bg_badge_neutral);
        } else if (e.showB) {
            headroom.setText(R.string.sound_comparing);
            headroom.setTextColor(0xFFC4C8CE);
            headroom.setBackgroundResource(R.drawable.bg_badge_neutral);
        } else if (peak > HEADROOM_RED_ABOVE_DB) {
            headroom.setText(getString(R.string.sound_headroom_high, signed(peak)));
            headroom.setTextColor(0xFFFF6B6B);
            headroom.setBackgroundResource(R.drawable.bg_badge_high);
        } else if (peak > HEADROOM_AMBER_ABOVE_DB) {
            if (e.limiter || e.isSerbaBisa()) {
                headroom.setText(getString(R.string.sound_headroom_limited,
                        signed(Math.min(peak, SoundAdjust.PEAK_LIMIT_DB))));
            } else {
                headroom.setText(getString(R.string.sound_headroom_unguarded, signed(peak)));
            }
            headroom.setTextColor(0xFFE0B25B);
            headroom.setBackgroundResource(R.drawable.bg_badge_warn);
        } else {
            headroom.setText(R.string.sound_headroom_ok);
            headroom.setTextColor(0xFF3EC7A8);
            headroom.setBackgroundResource(R.drawable.bg_badge_ok);
        }

        // Simple mode knobs and 32-band panel
        modeSwitch.setChecked(advanced);
        knobsRow.setVisibility(advanced ? View.GONE : View.VISIBLE);
        advPanel.setVisibility(advanced ? View.VISIBLE : View.GONE);
        int[] values = {e.working.level, e.working.bass, e.working.clarity, e.working.vocal, e.working.treble};
        for (int i = 0; i < knobs.length; i++) {
            ((TextView) knobs[i].findViewById(R.id.knob_value)).setText(signed(values[i]));
        }
        bandHz.setText(hzLabel(ChipDsp.FREQUENCIES[selectedBand]));
        bandDb.setText(signed(gains[selectedBand]));
        allLevel.setText(signed(e.working.level));

        // Cabin and seats
        cabin.setSeat(e.seat);
        for (int i = 0; i < seatButtons.length; i++) {
            boolean sel = i == e.seat;
            seatButtons[i].setBackgroundResource(sel ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            seatButtons[i].setTextColor(sel ? 0xFF0B0D10 : 0xFFC4C8CE);
        }

        // Toggles and A/B
        boolean serba = e.isSerbaBisa();
        limiterSwitch.setChecked(serba || e.limiter);
        smartSwitch.setChecked(serba || e.smartLoudness);
        limiterSwitch.setEnabled(!serba);
        smartSwitch.setEnabled(!serba);
        TextView limiterHint = findViewById(R.id.sound_limiter_hint);
        limiterHint.setText(serba ? R.string.sound_handled_by_serba : R.string.sound_limiter_hint);
        findViewById(R.id.sound_smart_hint).setVisibility(serba ? View.INVISIBLE : View.VISIBLE);
        abButton.setText(e.showB ? R.string.sound_ab_b : R.string.sound_ab_a);
        abButton.setBackgroundResource(e.showB ? R.drawable.bg_ab_active : R.drawable.bg_ab);
        binding = false;
        if (e.consumeBlockedNotice()) {
            new AlertDialog.Builder(this, R.style.Theme_HeadHome_Dialog)
                    .setTitle(R.string.sound_blocked_title)
                    .setMessage(R.string.sound_blocked_body)
                    .setPositiveButton(R.string.sound_ok, null)
                    .show();
        }
    }

    /** Shows the fades and chevrons only on the side where more cards are hidden. */
    private void updateScrollHints() {
        View content = presetScroll.getChildAt(0);
        if (content == null) {
            return;
        }
        int max = content.getWidth() - presetScroll.getWidth();
        int x = presetScroll.getScrollX();
        int left = max > 0 && x > 2 ? View.VISIBLE : View.GONE;
        int right = max > 0 && x < max - 2 ? View.VISIBLE : View.GONE;
        findViewById(R.id.presets_fade_left).setVisibility(left);
        findViewById(R.id.presets_scroll_left).setVisibility(left);
        findViewById(R.id.presets_fade_right).setVisibility(right);
        findViewById(R.id.presets_scroll_right).setVisibility(right);
    }

    private static int tagFor(String key) {
        switch (key) {
            case "flat_atas":
                return R.string.sound_tag_flat_atas;
            case "nendang":
                return R.string.sound_tag_nendang;
            case "vokal":
                return R.string.sound_tag_vokal;
            case "datar":
                return R.string.sound_tag_datar;
            case "koplo":
                return R.string.sound_tag_koplo;
            case "dangdut":
                return R.string.sound_tag_dangdut;
            case "akustik":
                return R.string.sound_tag_akustik;
            case "rock":
                return R.string.sound_tag_rock;
            case "jazz":
                return R.string.sound_tag_jazz;
            case "dj_edm":
                return R.string.sound_tag_dj_edm;
            case "pop":
                return R.string.sound_tag_pop;
            case "serba_bisa":
                return R.string.sound_tag_serba_bisa;
            default:
                return R.string.sound_tag_malam;
        }
    }

    private static String hzLabel(int hz) {
        return hz >= 1000 ? String.format(Locale.ROOT, "%s kHz", trim(hz / 1000.0)) : hz + " Hz";
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    // ---- presets sheet ----

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private TextView textButton(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(14);
        t.setTextColor(0xFFC4C8CE);
        t.setBackgroundResource(R.drawable.bg_flat_btn);
        t.setPadding(dp(14), 0, dp(14), 0);
        t.setClickable(true);
        return t;
    }

    private void showPresetSheet() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        final AlertDialog[] holder = new AlertDialog[1];

        TextView save = textButton(getString(R.string.sound_save_as));
        box.addView(save, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        save.setOnClickListener(v -> {
            holder[0].dismiss();
            promptName("", name -> {
                if (!engine.saveAs(name)) {
                    Toast.makeText(this, R.string.sound_limit, Toast.LENGTH_SHORT).show();
                }
            });
        });

        addGroup(box, R.string.sound_group_styles, SoundPreset.STYLE_KEYS, holder);
        addGroup(box, R.string.sound_group_genres, SoundPreset.GENRE_KEYS, holder);
        TextView mineHeader = groupHeader(getString(R.string.sound_group_mine));
        box.addView(mineHeader);
        if (engine.users.isEmpty()) {
            TextView none = new TextView(this);
            none.setText(R.string.sound_no_user_presets);
            none.setTextColor(getColor(R.color.muted));
            none.setPadding(0, dp(16), 0, dp(8));
            box.addView(none);
        }
        for (final SoundPreset p : new java.util.ArrayList<>(engine.users)) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = new TextView(this);
            name.setText(p.name);
            name.setTextSize(17);
            name.setTextColor(getColor(R.color.text));
            name.setPadding(0, dp(12), 0, dp(12));
            name.setClickable(true);
            name.setOnClickListener(v -> {
                holder[0].dismiss();
                engine.pickUser(p.name);
            });
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView rename = textButton(getString(R.string.sound_rename));
            rename.setOnClickListener(v -> {
                holder[0].dismiss();
                promptName(p.name, n -> engine.rename(p.name, n));
            });
            row.addView(rename, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
            TextView delete = textButton(getString(R.string.sound_delete));
            delete.setOnClickListener(v -> {
                holder[0].dismiss();
                engine.delete(p.name);
            });
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
            dl.setMarginStart(dp(8));
            row.addView(delete, dl);
            box.addView(row);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        holder[0] = new AlertDialog.Builder(this, R.style.Theme_HeadHome_Dialog)
                .setTitle(R.string.sound_presets_title)
                .setView(scroll)
                .setNegativeButton(R.string.cancel, null)
                .create();
        holder[0].show();
    }

    private TextView groupHeader(String text) {
        TextView h = new TextView(this);
        h.setText(text.toUpperCase(Locale.getDefault()));
        h.setTextSize(12);
        h.setLetterSpacing(0.12f);
        h.setTextColor(getColor(R.color.muted));
        h.setPadding(0, dp(16), 0, dp(4));
        return h;
    }

    private void addGroup(LinearLayout box, int title, String[] keys, final AlertDialog[] holder) {
        box.addView(groupHeader(getString(title)));
        for (final String key : keys) {
            TextView row = new TextView(this);
            row.setText(SoundEngine.builtinLabel(this, key));
            row.setTextSize(17);
            row.setTextColor(getColor(engine.activeId.equals(SoundEngine.ID_BUILTIN + key)
                    ? R.color.accent : R.color.text));
            row.setPadding(0, dp(10), 0, dp(10));
            row.setClickable(true);
            row.setOnClickListener(v -> {
                holder[0].dismiss();
                engine.pickBuiltin(key);
            });
            box.addView(row);
        }
    }

    private interface NameCallback {
        void onName(String name);
    }

    private void promptName(String initial, NameCallback cb) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(initial);
        input.setSelectAllOnFocus(true);
        input.setTextColor(getColor(R.color.text));
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        FrameLayoutHolder.pad(input, dp(20));
        new AlertDialog.Builder(this, R.style.Theme_HeadHome_Dialog)
                .setTitle(R.string.sound_name_title)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.sound_save, (d, w) -> cb.onName(input.getText().toString()))
                .show();
    }

    /** Tiny helper so the name field has side padding inside the dialog. */
    private static final class FrameLayoutHolder {
        static void pad(View v, int px) {
            v.setPadding(px, v.getPaddingTop(), px, v.getPaddingBottom());
        }
    }
}
