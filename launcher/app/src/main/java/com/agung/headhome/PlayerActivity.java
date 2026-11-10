package com.agung.headhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Outline;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.PathInterpolator;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** Folder list, songs (or search results) and a vinyl now-playing view for the built-in USB player. */
public class PlayerActivity extends Activity implements PlayerService.Listener {

    private static final long SEARCH_DEBOUNCE_MS = 200;
    private static final long ARM_MS = 700;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private ListView folderList;
    private ListView songList;
    private TextView songsHeader;
    private TextView songsEmpty;
    private TextView summary;
    private View searchBox;
    private EditText searchInput;
    private View searchClear;
    private View searchHideKeyboard;
    private View playerRoot;
    private View vinylCard;
    private View bottomBar;
    private boolean playingNow;
    private VinylView vinyl;
    private ImageView arm;
    private TextView vinylTitle;
    private TextView vinylSub;
    private TextView npTitle;
    private TextView npSub;
    private TextView npElapsed;
    private TextView npDuration;
    private SeekBar npSeek;
    private ImageButton npShuffle;
    private ImageButton npPlay;
    private ImageButton npRepeat;
    private VolumeControl volume;

    private final FolderAdapter folderAdapter = new FolderAdapter();
    private final SongAdapter songAdapter = new SongAdapter();

    private int selectedFolder = -1;
    private int lastPlayingFolder = -1;
    private int shownFolderCount = -1;
    /** Search results, or null when no search is active. */
    private List<LibrarySearch.Hit> hits;
    private boolean seeking;
    private boolean started;
    private Boolean armLifted;

    private final Runnable searchRunnable = this::runSearch;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshProgress();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.dock_music);
        setContentView(R.layout.activity_player);
        ((TextView) findViewById(R.id.header_title)).setText(R.string.dock_music);
        findViewById(R.id.header_back).setOnClickListener(v -> finish());

        summary = findViewById(R.id.header_summary);
        searchBox = findViewById(R.id.search_box);
        searchInput = findViewById(R.id.search_input);
        searchClear = findViewById(R.id.search_clear);
        searchHideKeyboard = findViewById(R.id.search_hide_keyboard);
        playerRoot = findViewById(R.id.player_root);
        vinylCard = findViewById(R.id.vinyl_card);
        bottomBar = findViewById(R.id.bottom_bar);
        folderList = findViewById(R.id.folder_list);
        songList = findViewById(R.id.song_list);
        songsHeader = findViewById(R.id.songs_header);
        songsEmpty = findViewById(R.id.songs_empty);
        vinyl = findViewById(R.id.vinyl);
        arm = findViewById(R.id.tonearm);
        vinylTitle = findViewById(R.id.vinyl_title);
        vinylSub = findViewById(R.id.vinyl_sub);
        npTitle = findViewById(R.id.np_title);
        npSub = findViewById(R.id.np_sub);
        npElapsed = findViewById(R.id.np_elapsed);
        npDuration = findViewById(R.id.np_duration);
        npSeek = findViewById(R.id.np_seek);
        npShuffle = findViewById(R.id.np_shuffle);
        npPlay = findViewById(R.id.np_play);
        npRepeat = findViewById(R.id.np_repeat);

        roundClip(findViewById(R.id.folders_card));
        roundClip(findViewById(R.id.songs_card));
        roundClip(findViewById(R.id.vinyl_card));

        float d = getResources().getDisplayMetrics().density;
        arm.setPivotX(101 * d);
        arm.setPivotY(15 * d);

        folderList.setAdapter(folderAdapter);
        songList.setAdapter(songAdapter);
        folderList.setOnItemClickListener((parent, view, position, id) -> {
            clearSearch();
            selectedFolder = position;
            folderAdapter.notifyDataSetChanged();
            songAdapter.notifyDataSetChanged();
            songList.setSelection(0);
            updateSongsHeader();
        });
        songList.setOnItemClickListener((parent, view, position, id) -> onSongTap(position));

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                boolean has = e.length() > 0;
                searchClear.setVisibility(has ? View.VISIBLE : View.GONE);
                searchBox.setBackgroundResource(has ? R.drawable.bg_search_active : R.drawable.bg_search);
                updateSearchMode();
                handler.removeCallbacks(searchRunnable);
                if (e.toString().trim().isEmpty()) {
                    runSearch();
                } else {
                    handler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
                }
            }
        });
        searchClear.setOnClickListener(v -> searchInput.setText(""));
        searchHideKeyboard.setOnClickListener(v -> hideKeyboard());
        searchInput.setOnFocusChangeListener((v, hasFocus) -> updateSearchMode());
        hideKeyboardOnListTouch(folderList);
        hideKeyboardOnListTouch(songList);
        playerRoot.requestFocus();
        updateSearchMode();
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                        getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                return true;
            }
            return false;
        });

        npSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                PlayerService p = PlayerService.get();
                if (fromUser && p != null && p.durationMs() > 0) {
                    npElapsed.setText(PlayerUi.format(p.durationMs() * progress / 1000L));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                PlayerService p = PlayerService.get();
                if (p != null && p.durationMs() > 0) {
                    p.seekTo((int) (p.durationMs() * bar.getProgress() / 1000L));
                }
                seeking = false;
            }
        });

        npShuffle.setOnClickListener(v -> {
            PlayerService p = PlayerService.get();
            if (p != null) {
                p.toggleShuffle();
            }
        });
        findViewById(R.id.np_prev).setOnClickListener(v -> {
            PlayerService p = PlayerService.get();
            if (p != null) {
                p.prev();
            }
        });
        npPlay.setOnClickListener(v -> {
            PlayerService p = PlayerService.get();
            if (p != null) {
                p.toggle();
            }
        });
        findViewById(R.id.np_next).setOnClickListener(v -> {
            PlayerService p = PlayerService.get();
            if (p != null) {
                p.next();
            }
        });
        npRepeat.setOnClickListener(v -> PlayerUi.cycleRepeat(this, PlayerService.get()));

        volume = new VolumeControl(this, handler, findViewById(R.id.np_vol_bar), findViewById(R.id.np_vol_value),
                findViewById(R.id.np_vol_minus), findViewById(R.id.np_vol_plus));
    }

    private void roundClip(View v) {
        final float radius = 20 * getResources().getDisplayMetrics().density;
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        v.setClipToOutline(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        PlayerService.ensureStarted(this);
        PlayerService.addListener(this);
        volume.start();
        refresh();
        handler.postDelayed(tick, 500);
    }

    @Override
    protected void onStop() {
        super.onStop();
        started = false;
        PlayerService.removeListener(this);
        volume.stop();
        handler.removeCallbacks(tick);
        handler.removeCallbacks(searchRunnable);
        vinyl.setSpinning(false);
    }

    @Override
    public void onPlayerChanged() {
        refresh();
    }

    // ---- keyboard and search mode ----

    private void hideKeyboardOnListTouch(ListView list) {
        list.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                hideKeyboard();
            }
            return false;
        });
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState == SCROLL_STATE_TOUCH_SCROLL) {
                    hideKeyboard();
                }
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {
            }
        });
    }

    /** Hides the keyboard and takes focus away from the search field. The query stays. */
    private void hideKeyboard() {
        if (!searchInput.hasFocus()) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        playerRoot.requestFocus();
        updateSearchMode();
    }

    private boolean isKeyboardVisible() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            return insets != null && insets.isVisible(WindowInsets.Type.ime());
        }
        return searchInput.hasFocus();
    }

    /** While searching the vinyl card is hidden and the bottom bar shrinks to title and play/pause. */
    private void updateSearchMode() {
        boolean focused = searchInput.hasFocus();
        boolean active = focused || searchInput.getText().length() > 0;
        searchHideKeyboard.setVisibility(focused ? View.VISIBLE : View.GONE);
        vinylCard.setVisibility(active ? View.GONE : View.VISIBLE);
        vinyl.setSpinning(started && playingNow && !active);

        float d = getResources().getDisplayMetrics().density;
        ViewGroup.LayoutParams bar = bottomBar.getLayoutParams();
        bar.height = (int) ((active ? 64 : 128) * d);
        bottomBar.setLayoutParams(bar);
        int detail = active ? View.GONE : View.VISIBLE;
        findViewById(R.id.np_progress_row).setVisibility(detail);
        findViewById(R.id.np_sub).setVisibility(detail);
        findViewById(R.id.np_shuffle).setVisibility(detail);
        findViewById(R.id.np_prev).setVisibility(detail);
        findViewById(R.id.np_next).setVisibility(detail);
        findViewById(R.id.np_repeat).setVisibility(detail);
        findViewById(R.id.np_volume_zone).setVisibility(detail);
        ViewGroup.LayoutParams play = npPlay.getLayoutParams();
        play.width = (int) ((active ? 44 : 66) * d);
        play.height = play.width;
        npPlay.setLayoutParams(play);
        npPlay.setPadding((int) ((active ? 12 : 19) * d), (int) ((active ? 12 : 19) * d),
                (int) ((active ? 12 : 19) * d), (int) ((active ? 12 : 19) * d));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && searchInput.hasFocus()
                && !inside(searchBox, ev) && !inside(folderList, ev) && !inside(songList, ev)) {
            hideKeyboard();
        }
        return super.dispatchTouchEvent(ev);
    }

    private static boolean inside(View v, MotionEvent ev) {
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        Rect r = new Rect(loc[0], loc[1], loc[0] + v.getWidth(), loc[1] + v.getHeight());
        return r.contains((int) ev.getRawX(), (int) ev.getRawY());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (isKeyboardVisible()) {
            hideKeyboard();
            return;
        }
        if (searchInput.getText().length() > 0) {
            searchInput.setText("");
            playerRoot.requestFocus();
            updateSearchMode();
            return;
        }
        if (searchInput.hasFocus()) {
            playerRoot.requestFocus();
            updateSearchMode();
            return;
        }
        super.onBackPressed();
    }

    // ---- search ----

    private void clearSearch() {
        if (searchInput.getText().length() > 0) {
            searchInput.setText("");
        }
    }

    private void runSearch() {
        String q = searchInput.getText().toString().trim();
        PlayerService p = PlayerService.get();
        hits = q.isEmpty() || p == null ? null : LibrarySearch.search(p.library(), q);
        updateSongsHeader();
        songsEmpty.setVisibility(hits != null && hits.isEmpty() ? View.VISIBLE : View.GONE);
        songAdapter.notifyDataSetChanged();
        songList.setSelection(0);
    }

    private void onSongTap(int position) {
        hideKeyboard();
        PlayerService p = PlayerService.get();
        if (p == null) {
            return;
        }
        if (hits != null) {
            LibrarySearch.Hit h = hits.get(position);
            clearSearch();
            selectedFolder = h.folder;
            if (h.isFolder) {
                folderAdapter.notifyDataSetChanged();
                folderList.setSelection(Math.max(0, h.folder - 2));
                songAdapter.notifyDataSetChanged();
                songList.setSelection(0);
            } else {
                p.playTrack(h.folder, h.track);
            }
            updateSongsHeader();
            return;
        }
        if (selectedFolder >= 0) {
            p.playTrack(selectedFolder, position);
        }
    }

    private void updateSongsHeader() {
        PlayerService p = PlayerService.get();
        if (hits != null) {
            songsHeader.setText(getString(R.string.search_results, hits.size()));
        } else if (p != null && selectedFolder >= 0 && selectedFolder < p.library().folders.size()) {
            songsHeader.setText(p.library().folders.get(selectedFolder).name.toUpperCase(Locale.getDefault()));
        } else {
            songsHeader.setText("");
        }
    }

    // ---- state ----

    private void refresh() {
        PlayerService p = PlayerService.get();
        List<Library.Folder> folders = p != null ? p.library().folders : null;
        boolean none = folders == null || folders.isEmpty();
        summary.setText(none
                ? getString(p != null && p.isScanning() ? R.string.scanning_music : R.string.no_usb)
                : getString(R.string.player_summary, folders.size(), p.library().trackCount()));

        if (!none) {
            boolean libraryChanged = folders.size() != shownFolderCount;
            shownFolderCount = folders.size();
            int playing = p.folderIndex();
            if (selectedFolder < 0 || selectedFolder >= folders.size() || libraryChanged
                    || playing != lastPlayingFolder) {
                selectedFolder = playing;
                lastPlayingFolder = playing;
                folderList.setSelection(Math.max(0, playing - 2));
                songList.setSelection(Math.max(0, p.trackIndex() - 2));
            }
        }
        updateSongsHeader();
        folderAdapter.notifyDataSetChanged();
        songAdapter.notifyDataSetChanged();

        boolean hasTrack = p != null && p.hasTrack();
        boolean playing = p != null && p.isPlaying();
        String title = hasTrack ? p.title() : getString(R.string.no_music);
        String sub = hasTrack
                ? getString(R.string.folder_position, p.folderName(), p.trackIndex() + 1, p.folderTrackCount())
                : "";
        vinylTitle.setText(title);
        vinylSub.setText(sub);
        npTitle.setText(title);
        npSub.setText(sub);
        vinyl.setLabel(hasTrack ? p.art() : null);
        playingNow = playing;
        vinyl.setSpinning(started && playing && vinylCard.getVisibility() == View.VISIBLE);
        setArmLifted(!playing);

        PlayerUi.updateButtons(this, p, npShuffle, npPlay, npRepeat);
        boolean repeating = p != null && p.repeatMode() != RepeatMode.CONTINUE;
        npRepeat.setColorFilter(getColor(repeating ? R.color.accent : R.color.text));
        refreshProgress();
    }

    private void setArmLifted(boolean lifted) {
        if (armLifted != null && armLifted == lifted) {
            return;
        }
        boolean first = armLifted == null;
        armLifted = lifted;
        float target = lifted ? -30f : 0f;
        arm.animate().cancel();
        if (first || !ValueAnimator.areAnimatorsEnabled()) {
            arm.setRotation(target);
        } else {
            arm.animate().rotation(target).setDuration(ARM_MS)
                    .setInterpolator(new PathInterpolator(0.3f, 0.7f, 0.2f, 1f)).start();
        }
    }

    private void refreshProgress() {
        PlayerService p = PlayerService.get();
        if (p == null || !p.hasTrack()) {
            if (!seeking) {
                npSeek.setProgress(0);
            }
            npElapsed.setText("");
            npDuration.setText("");
            return;
        }
        long dur = p.durationMs();
        long pos = p.positionMs();
        npDuration.setText(dur > 0 ? PlayerUi.format(dur) : "--:--");
        if (!seeking) {
            npElapsed.setText(PlayerUi.format(pos));
            npSeek.setProgress(dur > 0 ? (int) (1000L * Math.min(pos, dur) / dur) : 0);
        }
    }

    // ---- adapters ----

    private final class FolderAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            PlayerService p = PlayerService.get();
            return p != null ? p.library().folders.size() : 0;
        }

        @Override
        public Object getItem(int position) {
            return PlayerService.get().library().folders.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView
                    : LayoutInflater.from(PlayerActivity.this).inflate(R.layout.item_folder, parent, false);
            Library.Folder f = PlayerService.get().library().folders.get(position);
            boolean selected = position == selectedFolder;
            int color = getColor(selected ? R.color.accent : R.color.text);
            TextView name = v.findViewById(R.id.folder_name);
            name.setText(f.name);
            name.setTextColor(color);
            ((TextView) v.findViewById(R.id.folder_count)).setText(getString(R.string.songs_count, f.tracks.size()));
            ImageView icon = v.findViewById(R.id.folder_icon);
            icon.setColorFilter(selected ? getColor(R.color.accent) : 0xFFC4C8CE);
            v.findViewById(R.id.folder_edge).setBackgroundColor(selected ? getColor(R.color.accent) : 0);
            v.setBackgroundColor(selected ? getColor(R.color.row_selected) : 0);
            return v;
        }
    }

    private final class SongAdapter extends BaseAdapter {
        private Library.Folder folder() {
            PlayerService p = PlayerService.get();
            if (p == null || selectedFolder < 0 || selectedFolder >= p.library().folders.size()) {
                return null;
            }
            return p.library().folders.get(selectedFolder);
        }

        @Override
        public int getCount() {
            if (hits != null) {
                return hits.size();
            }
            Library.Folder f = folder();
            return f != null ? f.tracks.size() : 0;
        }

        @Override
        public Object getItem(int position) {
            return hits != null ? hits.get(position) : folder().tracks.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public int getItemViewType(int position) {
            if (hits == null) {
                return 0;
            }
            return hits.get(position).isFolder ? 1 : 2;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            int type = getItemViewType(position);
            int layout = type == 0 ? R.layout.item_song : type == 1 ? R.layout.item_hit_folder : R.layout.item_hit_song;
            View v = convertView != null ? convertView
                    : LayoutInflater.from(PlayerActivity.this).inflate(layout, parent, false);
            if (type == 0) {
                bindSong(v, position);
            } else {
                LibrarySearch.Hit h = hits.get(position);
                ((TextView) v.findViewById(R.id.hit_title)).setText(h.title);
                if (type == 1) {
                    ((TextView) v.findViewById(R.id.hit_count)).setText(getString(R.string.songs_count, h.count));
                } else {
                    ((TextView) v.findViewById(R.id.hit_subtitle)).setText(h.subtitle);
                }
            }
            return v;
        }

        private void bindSong(View v, int position) {
            PlayerService p = PlayerService.get();
            Library.Track t = folder().tracks.get(position);
            boolean current = p.hasTrack() && selectedFolder == p.folderIndex() && position == p.trackIndex();
            TextView name = v.findViewById(R.id.song_name);
            name.setText(t.displayName());
            name.setTextColor(getColor(current ? R.color.accent : R.color.text));
            name.setTypeface(android.graphics.Typeface.create("sans-serif", current
                    ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
            ((TextView) v.findViewById(R.id.song_index)).setText(String.valueOf(position + 1));
            EqualizerView eq = v.findViewById(R.id.song_eq);
            eq.setVisibility(current ? View.VISIBLE : View.GONE);
            eq.setPlaying(current && p.isPlaying());
            v.setBackgroundColor(current ? getColor(R.color.row_current) : 0);
        }
    }
}
