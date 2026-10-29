package org.openbw.androidtest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline Android host with a native C++ game engine and Android rendering. */
public final class MainActivity extends Activity {
  private static final String[] MPQS = {"STARDAT.MPQ", "BROODAT.MPQ", "patch_rt.mpq"};
  private static final int PICK_DATA = 10, PICK_MAP = 11, PICK_REPLAY = 12, IMPORT_REPLAY = 13;
  private NativeNearby nearbyPermissionRequest;
  private NativeNearby nearbyBluetoothRequest;
  void requestNearbyBluetooth(NativeNearby owner,boolean discoverable) {
    nearbyBluetoothRequest=owner;
    Intent intent=new Intent(discoverable?android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE:android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE);
    if(discoverable)intent.putExtra(android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,300);
    try{startActivityForResult(intent,discoverable?49:48);}catch(SecurityException e){nearbyBluetoothRequest=null;owner.bluetoothResult(discoverable,false);}
  }

  void requestNearbyPermissions(NativeNearby owner,String[] permissions) {
    nearbyPermissionRequest=owner;requestPermissions(permissions,47);
  }
  @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
    super.onRequestPermissionsResult(request,permissions,grants);
    if(request!=47)return;NativeNearby owner=nearbyPermissionRequest;nearbyPermissionRequest=null;
    // Nearby rechecks the full permission set, including grants held before
    // this request and the Approximate/Precise location choice.
    if(owner!=null)owner.permissions();
  }
  private byte[] pendingReplay;
  private NativeSurface pendingReplayView;
  private long pendingReplayRequest;
  private final ArrayDeque<String> logs = new ArrayDeque<String>();
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private FrameLayout root;
  private NativeSurface game;
  private boolean optionsVisible = true, activityResumed;
  private InputManager inputManager;
  private boolean inputDevicesRegistered;
  private final InputManager.InputDeviceListener inputDevices = new InputManager.InputDeviceListener() {
    @Override public void onInputDeviceAdded(int deviceId) {}
    @Override public void onInputDeviceChanged(int deviceId) {}
    @Override public void onInputDeviceRemoved(int deviceId) { resetPhysicalInput(); }
  };
  private boolean importing;
  private GameDataOperation.Lease dataLease;
  private boolean waitingForDataOperation, storageSuspendedGame;
  private final Runnable operationRefresh = new Runnable() {
    @Override public void run() {
      if(isDestroyed())return;
      if(GameDataOperation.busy()) {
        if(noticeView!=null)noticeView.setText(PortText.text(GameDataOperation.message()));
        root.postDelayed(this,500);
      } else if(waitingForDataOperation) {
        waitingForDataOperation=false;notice=GameDataOperation.message();showHome();
      }
    }
  };
  private final android.content.BroadcastReceiver storageReceiver = new android.content.BroadcastReceiver() {
    @Override public void onReceive(android.content.Context context, Intent intent) {
      if (GameDataOperation.busy() || isDestroyed()) return;
      if (!GameDataStorage.available(MainActivity.this)) {
        showStorageUnavailable();
      } else if (optionsVisible) showHome();
    }
  };
  private int activeMission = 1;
  private TextView noticeView;
  private String notice = "";

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    inputManager = (InputManager) getSystemService(INPUT_SERVICE);
    AppDiagnostics.install(this);
    NativeCache.beginSession(getCacheDir());
    try { PortText.load(getAssets().open("native/ui-languages.tsv")); }
    catch (IOException e) { AppDiagnostics.recordFailure("UI translations", e); }
    refreshLanguage();
    android.content.IntentFilter storageEvents = new android.content.IntentFilter();
    storageEvents.addAction(Intent.ACTION_MEDIA_MOUNTED);
    storageEvents.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
    storageEvents.addAction(Intent.ACTION_MEDIA_EJECT);
    storageEvents.addAction(Intent.ACTION_MEDIA_REMOVED);
    storageEvents.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
    storageEvents.addDataScheme("file");
    registerReceiver(storageReceiver, storageEvents);
    for (String oldReport : new String[]{"menu-health", "menu-diagnostics", "import-report"})
      getSharedPreferences(oldReport, 0).edit().clear().apply();
    final android.content.Context app = getApplicationContext();
    io.execute(() -> AppDiagnostics.loadPreviousExits(app));
    root = new FrameLayout(this);
    root.setBackgroundColor(Color.rgb(12, 19, 27));
    setContentView(root);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          int left = 0, top = 0, right = 0, bottom = 0;
          android.content.SharedPreferences display = getSharedPreferences("native-settings", 0);
          boolean extend = !optionsVisible && display.getBoolean("wide", false)
              && display.getBoolean("wide-over-camera", false);
          if (!extend && Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
            left = insets.getDisplayCutout().getSafeInsetLeft();
            right = insets.getDisplayCutout().getSafeInsetRight();
            top = insets.getDisplayCutout().getSafeInsetTop();
            bottom = insets.getDisplayCutout().getSafeInsetBottom();
          }
          // Edge-to-edge surfaces need the keyboard inset applied explicitly.
          // This keeps the lobby compose field visible while the IME is open.
          if (Build.VERSION.SDK_INT >= 30)
            bottom = Math.max(bottom, insets.getInsets(WindowInsets.Type.ime()).bottom);
          v.setPadding(left, top, right, bottom);
          return insets;
        });
    fullscreen();
    if (ready() && !busy()) startTitle();
    else showHome();
  }

  private File missionFile(int n) {
    return ProjectFiles.existing(gameDataDir(), CampaignData.filename(CampaignCatalog.id(n)));
  }

  private boolean mapImportRunning;
  private boolean busy() { return importing || mapImportRunning || GameDataOperation.busy(); }

  private void showStorageUnavailable() {
    if(game!=null&&!optionsVisible)storageSuspendedGame=true;
    notice="The selected SD card is unavailable. Reinsert it to play or move game data.";
    showHome();
  }

  private void watchDataOperation() {
    root.removeCallbacks(operationRefresh);
    if(GameDataOperation.busy()) {
      waitingForDataOperation=true;
      root.postDelayed(operationRefresh,500);
    } else waitingForDataOperation=false;
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  File gameDataDir() { return GameDataStorage.root(this); }

  private File dataDir() {
    File f = new File(gameDataDir(), "mpq");
    if(GameDataStorage.available(this))f.mkdirs();
    return f;
  }

  private boolean ready() {
    if(!GameDataStorage.available(this))return false;
    for (String name : MPQS) if (new File(dataDir(), name).length() < 32) return false;
    return true;
  }

  private synchronized void log(String line) {
    logs.addLast(line.length() > 700 ? line.substring(0, 700) : line);
    while (logs.size() > 100) logs.removeFirst();
  }

  private TextView text(LinearLayout parent, String value, int sp, int color) {
    TextView v = new TextView(this);
    v.setText(PortText.text(value));
    v.setTextSize(sp);
    v.setTextColor(color);
    v.setPadding(0, dp(5), 0, dp(5));
    parent.addView(v);
    return v;
  }

  private Button button(LinearLayout parent, String title, Runnable action) {
    Button v = new Button(this);
    v.setText(PortText.text(title));
    v.setAllCaps(false);
    v.setTextColor(new ColorStateList(new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
        new int[] {0xff64718d, 0xffdce6ff}));
    GradientDrawable bg =
        new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xff203d6c, 0xff102346});
    bg.setStroke(dp(1), 0xff496ca0);
    bg.setCornerRadius(dp(4));
    GradientDrawable disabled = new GradientDrawable();
    disabled.setColor(0xff0e1b31); disabled.setCornerRadius(dp(4)); disabled.setStroke(dp(1), 0xff263959);
    StateListDrawable states = new StateListDrawable();
    states.addState(new int[] {-android.R.attr.state_enabled}, disabled); states.addState(new int[] {}, bg);
    v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x555e86d1), states, null));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
    lp.topMargin = dp(6); lp.bottomMargin = dp(4);
    parent.addView(v, lp);
    v.setOnClickListener(w -> action.run());
    return v;
  }

  private void showHome() {
    stopGame();
    root.removeAllViews();
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    LinearLayout panel = new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(28), dp(18), dp(28), dp(28));
    scroll.addView(panel);
    root.addView(scroll, new FrameLayout.LayoutParams(
        Math.min(getResources().getDisplayMetrics().widthPixels, dp(840)), -1, android.view.Gravity.CENTER_HORIZONTAL));
    if(GameDataOperation.busy())notice=GameDataOperation.message();
    watchDataOperation();
    text(panel, "App Options", 25, 0xffdce6ff);
    text(panel, "StarDroid · " + BuildConfig.VERSION_NAME, 13, 0xff8da4cf);
    if (ready()) button(panel, storageSuspendedGame?"Resume Game":"Return to game menu", () -> startTitle()).setEnabled(!busy());
    LinearLayout language = section(panel, "Interface language");
    button(language, languageName(), this::chooseLanguage).setEnabled(!busy());
    text(language, "Game voices and campaign text use the language of the imported files.", 13, 0xff8da4cf);
    noticeView = notice.isEmpty() ? null : text(panel, notice, 14, 0xffc5cfeb);
    LinearLayout storage = section(panel, "Game data storage");
    text(storage, GameDataStorage.internal(this)?"Internal storage":"SD card", 15, 0xffc5cfeb);
    text(storage, "Saves, replays and player settings stay in internal storage.", 13, 0xff8da4cf);
    if(!GameDataStorage.available(this))text(storage,"The selected SD card is unavailable. Reinsert it to play or move game data.",14,0xffe0c782);
    if(GameDataStorage.cards(this).isEmpty())text(storage,"No writable SD card detected.",13,0xff8da4cf);
    button(storage,GameDataOperation.moving()?"Moving game data…":"Change data storage",this::chooseDataStorage).setEnabled(!busy());
    LinearLayout setup = section(panel, "Game files");
    text(
        setup,
        "Required: StarDat.mpq, BrooDat.mpq, Patch_rt.mpq\n"
            + "StarCraft campaign and movies: StarCraft.mpq\n"
            + "Brood War campaign and movies: BroodWar.mpq\n"
            + "Original Install.exe archives and previous mission ZIPs are also accepted.",
        14,
        0xffc5cfeb);
    button(setup, importing ? "Importing…" : "Import game files", () -> pickData())
        .setEnabled(!busy() && GameDataStorage.available(this));
    LinearLayout installed = section(panel, "Installed content");
    for (String name : MPQS)
      text(
          installed,
          (new File(dataDir(), name).length() >= 32 ? "Ready   " : "Missing   ") + name,
          14,
          new File(dataDir(), name).length() >= 32 ? 0xffa6c8ac : 0xffe0c782);
    for (boolean expansion : new boolean[] {false, true}) {
      int have = 0, total = 0;
      for (Object[] item : CampaignCatalog.ALL) {
        int code = (Integer) item[0];
        if (CampaignCatalog.expansion(code) == expansion) {
          total++;
          if (missionFile(code).length() > 0) have++;
        }
      }
      text(
          installed,
          (have == total ? "✓ " : "○ ")
              + (expansion ? "Brood War" : "StarCraft")
              + " · "
              + have
              + " / "
              + total
              + " maps",
          14,
          0xffc5cfeb);
    }
    text(
        installed,
        InterfaceData.installedSummary(interfaceFile()) + " · " + (musicFile().exists() ? "✓ " : "○ ") + "Music",
        14,
        0xffc5cfeb);
    java.util.Set<String> availableMovies = StoryData.installedMovies(gameDataDir());
    for (boolean expansion : new boolean[] {false, true}) {
      int total = 1, have = availableMovies.contains(expansion ? "smk/starxintr.smk" : "smk/starintr.smk") ? 1 : 0;
      for (CinematicCatalog.Entry entry : CinematicCatalog.ALL) if (entry.expansion() == expansion) {
        total++;
        if (availableMovies.contains(entry.file)) have++;
      }
      text(installed, (expansion ? "Brood War" : "StarCraft") + " · " + have + " / " + total
          + " movies", 14, have == total ? 0xffa6c8ac : 0xffe0c782);
    }
  }

  private void refreshLanguage() {
    String detected = GameLanguage.installedLanguage(dataDir());
    GameLanguage.russianData = detected.equals("ru");
    android.content.SharedPreferences p = getSharedPreferences("native-settings", 0);
    String code = p.getString("ui-language", "en");
    boolean automatic = p.getBoolean("ui-language-auto", code.equals("en"));
    PortText.select(automatic ? detected : code);
    p.edit().putBoolean("ui-language-auto", automatic).putString("ui-language", PortText.language).putBoolean("russian-data", GameLanguage.russianData).apply();
  }

  private void chooseDataStorage() {
    if(busy())return;
    Runnable choose=()->{
      if(busy()||isDestroyed())return;
      final List<File> locations=new ArrayList<File>();locations.add(getFilesDir());locations.addAll(GameDataStorage.cards(this));
      String[] labels=new String[locations.size()];labels[0]=PortText.text("Internal storage");
      for(int i=1;i<labels.length;i++)labels[i]=PortText.text("SD card")+(labels.length>2?" "+i:"");
      int current=locations.indexOf(gameDataDir());
      new AlertDialog.Builder(this).setTitle(PortText.text("Game data storage"))
          .setSingleChoiceItems(labels,current,(dialog,which)->{
            if(locations.get(which).equals(gameDataDir())){dialog.dismiss();return;}
            dialog.dismiss();
            if(!GameDataStorage.available(this)){notice="The selected SD card is unavailable. Reinsert it to play or move game data.";showHome();return;}
            new AlertDialog.Builder(this).setTitle(PortText.text("Move game data?"))
                .setMessage(PortText.text("Existing imported files will be moved and new imports will use this location. Saves and settings stay on this device. App-specific files are removed when the app is uninstalled."))
                .setPositiveButton(PortText.text("Move game data"),(d,w)->moveData(locations.get(which)))
                .setNegativeButton(PortText.text("Cancel"),null).show();
          }).setNegativeButton(PortText.text("Cancel"),null).show();
    };
    if(game==null)choose.run();else game.checkDataMove(allowed->{if(allowed)choose.run();else{notice="Return to the main menu before moving game data.";showHome();}});
  }

  private void moveData(File destination) {
    if(busy())return;
    final NativeSurface previous=game;
    Runnable begin=()->{
      if(busy()||isDestroyed())return;
      final GameDataOperation.Lease lease=GameDataOperation.begin("Moving game data…");
      if(lease==null)return;
      dataLease=lease;notice="Moving game data…";
      final File source=gameDataDir();
      if(previous!=null)previous.shutdown();
      showHome();
      io.execute(()->{
        String result;
        try {
          if(previous!=null)previous.awaitShutdown();
          if(!GameDataStorage.available(this,source)||!GameDataStorage.available(this,destination))throw new IOException("Game data storage is unavailable.");
          if(!destination.isDirectory()&&!destination.mkdirs())throw new IOException("Cannot create game data folder.");
          ImportedDataMove.move(source,destination,destination.getUsableSpace(),()->{
            if(!GameDataStorage.available(this,source)||!GameDataStorage.available(this,destination))throw new IOException("Game data storage is unavailable.");
          },()->{
            GameDataStorage.syncImportedDirectories(destination);
            String path=destination.equals(getFilesDir())?"":destination.getCanonicalPath();
            if(!getSharedPreferences(GameDataStorage.PREFS,0).edit().putString(GameDataStorage.KEY,path).commit()){
              getSharedPreferences(GameDataStorage.PREFS,0).edit().putString(GameDataStorage.KEY,source.equals(getFilesDir())?"":source.getCanonicalPath()).commit();
              throw new IOException("Cannot save data storage setting.");
            }
          },(done,total)->importProgress(PortText.text("Moving game data…")+" "+done+" / "+total));
          result="Game data moved.";
        }catch(Exception e){result="Game data move failed: "+e.getMessage();}
        final String message=result;
        runOnUiThread(()->{if(game==previous)game=null;dataLease=null;GameDataOperation.finish(lease,message);notice=message;refreshLanguage();if(!isDestroyed())showHome();});
      });
    };
    if(previous==null)begin.run();else previous.checkDataMove(allowed->{if(allowed)begin.run();else{notice="Return to the main menu before moving game data.";showHome();}});
  }

  private String languageName() {
    switch (PortText.language) {
      case "ru": return "Русский";
      case "de": return "Deutsch";
      case "es": return "Español";
      case "fr": return "Français";
      default: return "English";
    }
  }

  private void chooseLanguage() {
    String[] codes = new String[]{"auto", "en", "ru", "de", "es", "fr"};
    String[] labels = new String[]{PortText.text("Automatic (game files)"), "English", "Русский", "Deutsch", "Español", "Français"};
    android.content.SharedPreferences p = getSharedPreferences("native-settings",0);
    int selected = p.getBoolean("ui-language-auto", true) ? 0 : java.util.Arrays.asList(codes).indexOf(PortText.language);
    new AlertDialog.Builder(this).setTitle(PortText.text("Interface language"))
        .setSingleChoiceItems(labels, Math.max(0, selected), (dialog, which) -> {
          p.edit().putBoolean("ui-language-auto", which == 0)
              .putString("ui-language", which == 0 ? GameLanguage.installedLanguage(dataDir()) : codes[which]).apply();
          refreshLanguage();
          dialog.dismiss(); showHome();
        }).setNegativeButton(PortText.text("Cancel"),null).show();
  }

  private LinearLayout section(LinearLayout parent, String heading) {
    LinearLayout panel = new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(18), dp(12), dp(18), dp(14));
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(0xff0a1732);
    bg.setStroke(dp(1), 0xff304b7c);
    bg.setCornerRadius(dp(6));
    panel.setBackground(bg);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(14);
    parent.addView(panel, lp);
    text(panel, heading, 18, 0xffdce6ff);
    return panel;
  }

  private File storyFile() {
    return ProjectFiles.existing(gameDataDir(), StoryData.NAME);
  }

  private File movieFile() {
    return ProjectFiles.existing(gameDataDir(), StoryData.MOVIES);
  }

  private File musicFile() {
    return ProjectFiles.existing(gameDataDir(), MusicData.NAME);
  }

  private File interfaceFile() {
    return ProjectFiles.existing(gameDataDir(), InterfaceData.NAME);
  }

  private void importProgress(String value) {
    GameDataOperation.update(dataLease,value);
    runOnUiThread(
        () -> {
          notice = value;
          if (!isDestroyed() && noticeView != null) noticeView.setText(PortText.text(value));
        });
  }

  private String importContent(Uri uri) throws IOException {
    String name = displayName(uri);
    if (canonical(name) != null) return "Imported " + importOne(uri) + ".";
    if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
      try (InputStream in = getContentResolver().openInputStream(uri)) {
        if (in == null) throw new IOException("Cannot read the selected ZIP.");
        return "Imported " + CampaignData.install(in, gameDataDir()) + ".";
      }
    }
    ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(uri, "r");
    if (fd == null) throw new IOException("Cannot read " + name + ".");
    try (ParcelFileDescriptor.AutoCloseInputStream in =
        new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
      boolean expansion = MissionBundle.expansionArchive(in.getChannel());
      return GameContent.install(
          in.getChannel(),
          gameDataDir(),
          expansion,
          id -> {
            ArrayList<String> names = new ArrayList<String>();
            try (BufferedReader reader =
                new BufferedReader(
                    new InputStreamReader(
                        getAssets().open("missions/" + id + "-files.txt"), "UTF-8"))) {
              String line;
              while ((line = reader.readLine()) != null)
                if (!line.trim().isEmpty()) names.add(line.trim());
            }
            return names;
          },
          value -> importProgress(value));
    }
  }

  private void pickData() {
    if(busy()||!GameDataStorage.available(this))return;
    Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    pick.setType("*/*");
    pick.addCategory(Intent.CATEGORY_OPENABLE);
    pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    try {
      startActivityForResult(pick, PICK_DATA);
    } catch (Exception e) {
      notice = "No file picker is available: " + e.getMessage();
      showHome();
    }
  }

  private String displayName(Uri uri) {
    try (Cursor c =
        getContentResolver()
            .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (c != null && c.moveToFirst()) return c.getString(0);
    } catch (Exception ignored) {
    }
    return "unknown file";
  }

  private String canonical(String name) {
    name = name.replaceAll("(?i)\\s*\\(\\d+\\)(?=\\.mpq$)", "");
    for (String candidate : MPQS) if (candidate.equalsIgnoreCase(name)) return candidate;
    return null;
  }

  private String importOne(Uri uri) throws IOException {
    String sourceName = displayName(uri), name = canonical(sourceName);
    if (name == null)
      throw new IOException("Select an extracted StarDat.mpq, BrooDat.mpq or Patch_rt.mpq file.");
    File target = new File(dataDir(), name), temp = new File(dataDir(), name + ".import");
    long count = 0;
    try {
      try (InputStream in = getContentResolver().openInputStream(uri);
          FileOutputStream out = new FileOutputStream(temp)) {
        if (in == null) throw new IOException("Cannot read " + name);
        byte[] buffer = new byte[65536];
        int n;
        while ((n = in.read(buffer)) != -1) {
          count += n;
          if (count > 512L * 1024 * 1024)
            throw new IOException(name + " is too large for classic game data.");
          out.write(buffer, 0, n);
        }
        out.getFD().sync();
      }
      boolean valid = false;
      try (FileInputStream check = new FileInputStream(temp)) {
        byte[] header = new byte[4];
        valid =
            count >= 32
                && check.read(header) == 4
                && header[0] == 'M'
                && header[1] == 'P'
                && header[2] == 'Q'
                && (header[3] == 26 || header[3] == 27);
      }
      if (!valid)
        throw new IOException(
            name + " is not a valid MPQ archive. Use the extracted original file.");
      Files.move(
          temp.toPath(),
          target.toPath(),
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
      log("Imported " + name + ": " + count + " bytes");
      return name;
    } finally {
      if (temp.exists()) temp.delete();
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if(request==48 || request==49) {
      NativeNearby owner=nearbyBluetoothRequest;nearbyBluetoothRequest=null;
      if(owner!=null)owner.bluetoothResult(request==49,request==49?result>0:result==RESULT_OK);
      return;
    }
    if (request == IMPORT_REPLAY) {
      if(result==RESULT_OK&&data!=null&&data.getData()!=null){
        final Uri uri=data.getData();final NativeSurface replayView=game;
        io.execute(()->{String problem=null;try(java.io.InputStream in=getContentResolver().openInputStream(uri)){
          if(in==null)throw new IOException("Unable to open replay");
          new NativeReplays(getFilesDir()).importReplay(in,displayName(uri),bytes->NativeBridge.replayInfo(bytes));
        }catch(Exception e){problem=e.getMessage();}
        if(replayView!=null)replayView.replayImported(problem);
        });
      }return;
    }
    if (request == PICK_REPLAY) {
      final byte[] bytes = pendingReplay;
      final NativeSurface replayView=pendingReplayView;final long replayRequest=pendingReplayRequest;
      pendingReplay = null;pendingReplayView=null;
      if (result == RESULT_OK && data != null && data.getData() != null && bytes != null) {
        final Uri uri = data.getData();
        io.execute(
            () -> {
              try (java.io.OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IOException("Unable to open replay destination");
                out.write(bytes);
                if(replayView!=null)replayView.replayExported(replayRequest,"Replay saved");
              } catch (Exception e) {
                if(replayView!=null)replayView.replayExported(replayRequest,"Replay could not be saved: "+e.getMessage());
              }
            });
      }
      return;
    }
    if (request == PICK_MAP) {
      if (result == RESULT_OK && data != null) {
        final ArrayList<Uri> selected = new ArrayList<Uri>();
        final NativeSurface mapView = game;
        if(mapView==null || busy() || !GameDataStorage.available(this))return;
        final GameDataOperation.Lease lease=GameDataOperation.begin("Reading map pack…");
        if(lease==null)return;
        dataLease=lease;mapImportRunning=true;mapView.mapImportProgress("Reading map pack…", true);
        if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) selected.add(data.getClipData().getItemAt(i).getUri());
        else if (data.getData() != null) selected.add(data.getData());
        io.execute(() -> {
          ArrayList<NativeMaps.Staged> maps = new ArrayList<NativeMaps.Staged>();
          try {
            if (selected.size() > NativeMaps.MAX_COUNT) throw new IOException("Import up to 10000 maps at once.");
            long totalMapBytes = 0;
            for (Uri uri : selected) {
              InputStream in = getContentResolver().openInputStream(uri);
              if (in == null) throw new IOException("Cannot open selected map.");
              List<NativeMaps.Staged> staged = NativeMaps.stage(in, displayName(uri), getCacheDir());
              maps.addAll(staged); for (NativeMaps.Staged map : staged) totalMapBytes += map.file.length();
              if (totalMapBytes > NativeMaps.MAX_BATCH) throw new IOException("Map pack exceeds 8 GiB.");
              if (maps.size() > NativeMaps.MAX_COUNT) throw new IOException("Import up to 10000 maps at once.");
            }
            NativeMaps.Imported imported = NativeMaps.importMaps(maps, gameDataDir(),
                map -> NativeMapCheck.validate(NativeBridge.mapScenario(map.getPath())),
                (done, count) -> {GameDataOperation.update(lease,done+" / "+count);mapView.mapImportProgress(done + " / " + count, true);});
            runOnUiThread(() -> {mapImportRunning=false;dataLease=null;GameDataOperation.finish(lease,"");if(isDestroyed())return;if(game==mapView)mapView.mapsImported(imported, "");if(optionsVisible)showHome();});
          } catch (Exception e) {
            for (NativeMaps.Staged map : maps) map.file.delete();
            runOnUiThread(() -> {mapImportRunning=false;dataLease=null;GameDataOperation.finish(lease,e.getMessage());if(isDestroyed())return;if(game==mapView)mapView.mapsImported(null, e.getMessage());if(optionsVisible){notice=e.getMessage();showHome();}});
          }
        });
      }
      return;
    }
    if (request != PICK_DATA || result != RESULT_OK || data == null || busy() || !GameDataStorage.available(this)) return;
    final ArrayList<Uri> uris = new ArrayList<Uri>();
    if (data.getClipData() != null) {
      for (int i = 0; i < data.getClipData().getItemCount(); i++)
        uris.add(data.getClipData().getItemAt(i).getUri());
    } else if (data.getData() != null) uris.add(data.getData());
    final GameDataOperation.Lease lease=GameDataOperation.begin("Importing selected files…");
    if(lease==null)return;
    dataLease=lease;importing = true;
    notice = "Importing selected files…";
    showHome();
    io.execute(
        () -> {
          ArrayList<String> results = new ArrayList<String>();
          for (Uri uri : uris) {
            try {
              results.add(displayName(uri) + ": " + importContent(uri));
            } catch (Exception e) {
              String error = displayName(uri) + ": " + e.getMessage();
              results.add(error);
              log("Import: " + error);
            }
          }
          refreshLanguage();
          final String resultText = String.join("\n", results);
          runOnUiThread(
              () -> {
                importing = false;dataLease=null;
                GameDataOperation.finish(lease,resultText);
                notice = resultText;
                if (!isDestroyed()) showHome();
              });
        });
  }

  private void startTitle() {
    if(busy())return;
    if (!ready()) {
      showHome();
      return;
    }
    optionsVisible = false;
    refreshDisplayArea();
    root.removeAllViews();
    boolean reopen=storageSuspendedGame&&game!=null;
    if (game == null) game = new NativeSurface(this);
    else if(!reopen)game.title();
    root.addView(game, new FrameLayout.LayoutParams(-1, -1));
    if(reopen) {
      final NativeSurface resuming=game;
      game.reopenStorage(()->{
        if(isDestroyed()||game!=resuming)return;
        if(!GameDataStorage.available(this)){showStorageUnavailable();return;}
        storageSuspendedGame=false;
        if(activityResumed&&!optionsVisible)game.resume();
      },message->{
        if(isDestroyed()||game!=resuming)return;
        notice="Unable to reopen game data: "+message;showHome();
      });
    } else {storageSuspendedGame=false;game.resume();}
    game.requestFocus();
  }

  private void stopGame() {
    if (game != null) game.suspend();
    optionsVisible = true;
    refreshDisplayArea();
  }

  void requestAppOptions() {
    showHome();
  }

  @Override public boolean dispatchGenericMotionEvent(android.view.MotionEvent event) {
    if (!optionsVisible && game != null) {
      if (game.handleDesktopMotion(event)) return true;
      if (game.handleGamepadMotion(event)) return true;
    }
    return super.dispatchGenericMotionEvent(event);
  }

  @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
    if (!optionsVisible && game != null) {
      if (game.handleGamepadKey(event)) return true;
      if (game.handleDesktopKey(event)) return true;
    }
    return super.dispatchKeyEvent(event);
  }

  private void resetPhysicalInput() {
    if (game == null) return;
    game.resetDesktopInput();
    game.resetGamepadInput();
  }

  private void watchInputDevices(boolean watch) {
    if (inputManager == null || inputDevicesRegistered == watch) return;
    if (watch) inputManager.registerInputDeviceListener(inputDevices, null);
    else inputManager.unregisterInputDeviceListener(inputDevices);
    inputDevicesRegistered = watch;
  }

  void requestExit() {
    finish();
  }

  void requestDiagnostics() {
    showDiagnostics();
  }

  void nativeFailure(String message) {
    if (game != null) {
      game.shutdown();
      game = null;
    }
    notice = message;
    showHome();
  }

  void pickReplay() {
    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),IMPORT_REPLAY);
  }

  void recordReplay(byte[] bytes,NativeSurface resultView,long request) {
    if(bytes==null)return;
    io.execute(()->{String result;try{
      String[] header=GameLanguage.decode(NativeBridge.replayInfo(bytes)).split("\\n")[0].split("\\t",-1);
      String label=header.length>3?header[3]:"Replay";
      new NativeReplays(getFilesDir()).save(bytes,label+" "+new java.text.SimpleDateFormat("yyyy-MM-dd HH-mm",Locale.ROOT).format(new java.util.Date()));
      result="Replay saved";
    }catch(Exception e){result="Replay could not be saved: "+e.getMessage();}
    final String notice=result;if(resultView!=null)resultView.replayRecorded(request,notice);
    });
  }

  void saveReplay(byte[] bytes,NativeSurface replayView,long request) {
    if (bytes == null) return;
    pendingReplay = bytes;pendingReplayView=replayView;pendingReplayRequest=request;
    Intent intent =
        new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType("application/octet-stream")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_TITLE, "StarDroid-" + System.currentTimeMillis() + ".rep");
    startActivityForResult(intent, PICK_REPLAY);
  }

  void pickNativeMap() {
    if(busy()||!GameDataStorage.available(this))return;
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    intent.setType("*/*");
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    startActivityForResult(intent, PICK_MAP);
  }

  private String diagnostics() {
    StringBuilder b = new StringBuilder("StarDroid " + BuildConfig.VERSION_NAME
        + " Native (" + BuildConfig.VERSION_CODE + ")\n");
    b.append(Build.MANUFACTURER)
        .append(' ')
        .append(Build.MODEL)
        .append(" · Android ")
        .append(Build.VERSION.RELEASE)
        .append(" / API ")
        .append(Build.VERSION.SDK_INT)
        .append('\n');
    b.append(game == null ? "Native engine not started" : game.diagnostics()).append('\n');
    b.append(AppDiagnostics.summary(this));
    b.append("Game data storage: ").append(gameDataDir()).append("; available=").append(GameDataStorage.available(this)).append('\n');
    for (String name : MPQS)
      b.append(name).append(": ").append(new File(dataDir(), name).length()).append(" bytes\n");
    synchronized (this) {
      for (String line : logs) b.append(line).append('\n');
    }
    b.append(
        "\n"
            + "Put together by ToadWarrior.\n"
            + "OpenBW contributors; heiner/openbw.\n"
            + "Smacker decoding: libsmacker, LGPL 2.1+.\n"
            + "Weave: Alexandre Coderre-Chabot, CC BY 4.0.\n"
            + "Original game data is imported locally and is not bundled.\n");
    return b.toString();
  }

  private void showDiagnostics() {
    final String value = diagnostics();
    TextView body = new TextView(this);
    body.setText(value);
    body.setTextSize(12);
    body.setPadding(dp(16), dp(12), dp(16), dp(12));
    body.setTextIsSelectable(true);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(body);
    new AlertDialog.Builder(this)
        .setTitle(PortText.text("Diagnostics / credits"))
        .setView(scroll)
        .setPositiveButton(
            PortText.text("Copy"),
            (d, w) -> {
              ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                  .setPrimaryClip(ClipData.newPlainText("StarDroid diagnostics", value));
              Toast.makeText(this, PortText.text("Diagnostics copied"), Toast.LENGTH_SHORT).show();
            })
        .setNegativeButton(PortText.text("Close"), null)
        .show();
  }

  @Override
  public void onBackPressed() {
    if (!optionsVisible && game != null) game.back();
    else if (!busy()) {
      if (ready()) startTitle();
      else super.onBackPressed();
    }
  }

  private void fullscreen() {
    if (Build.VERSION.SDK_INT >= 28) {
      WindowManager.LayoutParams attributes = getWindow().getAttributes();
      attributes.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
          ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
          : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
      getWindow().setAttributes(attributes);
    }
    if (Build.VERSION.SDK_INT >= 30) {
      getWindow().setDecorFitsSystemWindows(false);
      WindowInsetsController c = getWindow().getInsetsController();
      if (c != null) {
        c.hide(WindowInsets.Type.systemBars());
        c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
      }
    } else getWindow().getDecorView().setSystemUiVisibility(5894);
  }

  void refreshDisplayArea() {
    fullscreen();
    if (root != null) root.requestApplyInsets();
  }

  @Override
  public void onWindowFocusChanged(boolean focus) {
    super.onWindowFocusChanged(focus);
    if (focus) fullscreen();
    else resetPhysicalInput();
  }

  @Override
  protected void onPause() {
    activityResumed = false;
    watchInputDevices(false);
    AppDiagnostics.foreground(false);
    if (game != null) game.suspend();
    super.onPause();
  }

  @Override
  protected void onResume() {
    super.onResume();
    activityResumed = true;
    watchInputDevices(true);
    AppDiagnostics.foreground(true);
    if(busy())showHome();
    else if (!GameDataStorage.available(this))showStorageUnavailable();
    else if (game != null && !optionsVisible && !storageSuspendedGame) game.resume();
    else if (optionsVisible) showHome();
    fullscreen();
  }

  @Override
  protected void onDestroy() {
    watchInputDevices(false);
    unregisterReceiver(storageReceiver);
    root.removeCallbacks(operationRefresh);
    if (game != null) game.shutdown();
    io.shutdown();
    super.onDestroy();
  }

  @Override
  public void onTrimMemory(int level) {
    AppDiagnostics.memoryPressure(level);
    super.onTrimMemory(level);
  }
}
