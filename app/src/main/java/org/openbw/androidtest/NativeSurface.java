package org.openbw.androidtest;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.json.*;

/** One native render worker; Android vsync schedules frames, no browser runtime. */
final class NativeSurface extends SurfaceView
    implements SurfaceHolder.Callback, Choreographer.FrameCallback {
  private final MainActivity activity;
  private final File files, gameFiles, cache;
  private final SharedPreferences prefs;
  private final ControlSettings controls = new ControlSettings();
  private final GamepadControls gamepad = new GamepadControls();
  private final DesktopSettings desktopSettings = new DesktopSettings();
  private final HashMap<Integer,Integer> gamepadHeld = new HashMap<Integer,Integer>();
  private float padX=320,padY=200,padPointerX,padPointerY,padCameraX,padCameraY,padHatX,padHatY;
  private boolean padActive,padPrecision,padNativeDrag,padTouchDrag;
  private int padDevice=-1,padGroup=1;
  private long padClickAt;
  private Bitmap padCursor;
  private Bitmap[] padArrows;
  private int[] padArrowX,padArrowY;
  private boolean padArrowChecked;
  private String padDragPage="",padDragModal="";
  private String gamepadReturnModal="";
  private int gamepadBinding;
  private final Object gate = new Object();
  private final Object surfaceGate = new Object();
  private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<Runnable>();
  private volatile boolean running = true, surface = false, foreground = true;
  private volatile long requested = 0;
  private volatile int physicalWidth = 640, physicalHeight = 480;
  private Thread worker;
  private NativeAudio audio;
  private NativeArt art;
  private NativeGlue glue;
  private NativeHud hud;
  private NativeCampaign campaign;
  private JSONArray missions;
  private JSONObject commandMaps, currentMission;
  private Bitmap world;
  private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private String page = "loading", modal = "", failure = "", hint = "", nextScenario = "";
  private boolean expansion = false,
      wide = false, overCamera = false,
      zoomOut = false,
      instant = false,
      activeGame = false;
  private float zoom = 1, effects = 1, music = .65f;
  private boolean muted, orderLines, oneShotSelect, nearbyDefenders = true;
  private String selectedProfile = "Player";
  private final ArrayList<String> profiles = new ArrayList<String>();
  private final CampaignProgress campaignProgress;
  private String campaignProfile = "";
  private final NativeReplays replays;
  private List<File> replayFiles=Collections.emptyList();
  private final List<Integer> replayPlayers=new ArrayList<Integer>();
  private final List<Integer> replayRaces=new ArrayList<Integer>();
  private File selectedReplay,playingReplay;
  private Bitmap replayThumbnail;
  private boolean replayPreviewUnavailable;
  private String replayDetails="",replayNotice="";
  private float replayOffset;
  private boolean replayGame,replayPaused,replayExpansion,replayReturnExpansion,replayValid;
  private int replayRate=1;
  private boolean replaySeeking;
  private long replaySaveRequest;
  private NativeResults replaySaveScreen;
  private long replayBrowserGeneration;
  private Runnable replayAcknowledgement;
  private NativeArt.Font replayLargeSource,replaySmallSource,replayLargeGreen,replaySmallGreen;
  private double speed = 42;
  private Runnable pendingScreen;
  private boolean slideLocked;
  private int chosenMission = -1, profileScroll;
  private float missionOffset, profileOffset, listVelocity, listStart;
  private final RectF missionViewport = new RectF(), profileViewport = new RectF();
  private String scrollingList = "";
  private long listMoveTime;
  private RoomMatch nearby;
  private boolean networkStats;
  private boolean nearbyWifi=true;private int nearbyPage;
  private String nearbyTransport="";
  private volatile String lastNetworkDiagnostics="No multiplayer session";
  private volatile NativeNearby nearbyRadio;
  private int nearbyGeneration;
  private String nearbyStatus="Host or join a game. Keep Bluetooth and Wi-Fi on.";
  private final LinkedHashMap<String,String> nearbyGames=new LinkedHashMap<String,String>();
  private File customMap, activeMapSource;
  private List<File> customMaps = new ArrayList<File>();
  private float mapOffset;
  private final RectF mapViewport=new RectF(60,130,328,240);
  private final NativeMapPreview mapPreviews=new NativeMapPreview();
  private Bitmap mapThumbnail;
  private String mapPreviewError="";
  private MatchSetup customSetup=new MatchSetup(false),nearbySetup=new MatchSetup(true);
  private boolean multiplayerSetup,nearbyExpansion=true,multiplayerSaves,nearbyResuming,multiplayerGame;
  private byte[] nearbyResume;
  private String nearbyStage="connection",selectedNearby="";
  private int movingSlot=-1;
  private volatile boolean chatEditing,profileEditing;
  private String profileDraft="",profileDelete="";
  private final android.text.SpannableStringBuilder profileInput=new android.text.SpannableStringBuilder();
  private String chatDraft="";
  private final android.text.SpannableStringBuilder chatInput=new android.text.SpannableStringBuilder();

  private String mapImportStatus = "";
  private final JSONObject[] savedSlots = new JSONObject[NativeSaves.SLOTS];
  private final String[] savedSlotErrors = new String[NativeSaves.SLOTS];
  private int pendingSaveSlot;
  private String saveMode = "load", saveMessage = "";
  private String customName = "Weave", customDescription = "", customTileset = "Desert";
  private int customWidth = 64, customHeight = 64, customStarts = 4;
  private int customRace = 1, customBotSlot = 1;
  private int resourcePercent = 100, commandPercent = 100, groupPercent = 100, descriptionPercent = 100, touchPercent = 100;
  private boolean smoothGraphics;
  private Bitmap titleArtwork;
  private int opponentRace = 1; // Zerg, Terran or Protoss.
  private int terranAiLevel = 2;
  private int fps = 60,
      logicalWidth = 640,
      missionRace = 1,
      missionCode = 0,
      listRace = 1,
      listScroll = 0;
  private double clock = 0, lastTime = 0, accumulator = 0, hintUntil = 0;
  private int hintSupplyRace = -1;
  private int lastOutcome = 0, errorSeq = -1, musicIndex = 0;
  private String commandError = "";
  private double commandErrorUntil;
  private String musicPhase = "", currentMusicPath = "";
  private boolean currentMusicExpansion;
  private int musicThemeBoundary;
  private final HashMap<String, Integer> musicThemeStarts = new HashMap<String, Integer>();
  private float speechMusicGain = 1;
  private final ArrayList<Button> buttons = new ArrayList<Button>();
  private final ArrayList<JSONObject> visibleMissions = new ArrayList<JSONObject>();
  private final ArrayList<NativePortrait> briefMovies = new ArrayList<NativePortrait>();
  private final ArrayList<StoryPage> story = new ArrayList<StoryPage>();
  private int storyIndex = 0;
  private double storyTime = 0;
  private NativeResults results;
  private NativeArt.Movie cinematic;
  private double cinematicStart;
  private Runnable cinematicDone;
  private final Set<String> availableMovies = new HashSet<String>();
  private String[] tips = new String[0];
  private int tipIndex;
  private boolean startupTips;
  private long renderedFrames = 0;
  private double totalRenderMs = 0, maxRenderMs = 0;
  private long diagnosticsAt,diagnosticsFrames;
  private double drawWindowMs,frameWindowMs,displayFps,drawMs,frameMs;
  private volatile String diagnostics = "Native renderer starting";
  private final MenuHotkeys menuHotkeys=new MenuHotkeys();
  private final MenuHotkeys backgroundMenuHotkeys=new MenuHotkeys();
  private final ArrayList<MenuCaption> menuCaptions=new ArrayList<>();
  private boolean menuBehindModal;

  private static final class MenuCaption {
    final Button button;
    final MenuHotkeys.Entry entry;
    final java.util.function.Consumer<String> draw;
    final boolean highlight, navigation;
    MenuCaption(Button button,MenuHotkeys.Entry entry,boolean highlight,boolean navigation,java.util.function.Consumer<String> draw){
      this.button=button;this.entry=entry;this.highlight=highlight;this.navigation=navigation;this.draw=draw;
    }
  }

  private static final class Button {
    RectF rect;
    Runnable action, longAction;
    boolean enabled,vertical,placementButton,controlGroup;
    int key;
    String page, modal;
    java.util.function.Consumer<Float> slider;

    Button(RectF r, Runnable a, boolean e) {
      rect = r;
      action = a;
      enabled = e;
    }
  }

  private static final class StoryPage {
    Bitmap background;
    NativeArt.Font font;
    String text;
    int duration = 5000;
    boolean lower;
    StoryLayout layout;
  }

  NativeSurface(MainActivity a) {
    super(a);
    activity = a;
    files = a.getFilesDir();
    gameFiles = a.gameDataDir();
    replays = new NativeReplays(files);
    cache = new File(a.getCacheDir(), "native-mpq");
    prefs = a.getSharedPreferences("native-settings", 0);
    controls.load(prefs);
    gamepad.load(prefs);
    desktopSettings.load(prefs);
    fps = prefs.getInt("fps", 60);
    resourcePercent = ControlSettings.clamp(prefs.getInt("resource-percent", 100), 100, 150);
    commandPercent = ControlSettings.clamp(prefs.getInt("command-percent", 100), 100, 120);
    groupPercent = ControlSettings.clamp(prefs.getInt("group-percent", commandPercent), 75, 150);
    descriptionPercent = ControlSettings.clamp(prefs.getInt("description-percent", 100), 100, 150);
    touchPercent = ControlSettings.clamp(prefs.getInt("touch-percent", 100), 100, 150);
    speed = prefs.getFloat("frame-ms", prefs.getInt("speed", 42));
    loadAudioSettings();
    orderLines = prefs.getBoolean("order-lines", false);
    nearbyDefenders = prefs.getBoolean("nearby-defenders", true);
    networkStats = prefs.getBoolean("network-stats", false);
    expansion = prefs.getBoolean("expansion", false);
    selectedProfile = prefs.getString("profile", "Player");
    try {
      JSONArray saved = new JSONArray(prefs.getString("profiles", "[\"Player\"]"));
      for (int i = 0; i < Math.min(32, saved.length()); i++) profiles.add(saved.getString(i));
    } catch (Exception ignored) { profiles.add("Player"); }
    if (!profiles.contains(selectedProfile)) selectedProfile = profiles.isEmpty() ? "" : profiles.get(0);
    campaignProgress = new CampaignProgress(prefs.getString(CampaignProgress.KEY, ""), profiles);
    saveCampaignProgress();
    wide = prefs.getBoolean("wide", false);
    overCamera = prefs.getBoolean("wide-over-camera", false);
    zoomOut = prefs.getBoolean("zoom-out", true);
    zoom = prefs.getFloat("zoom", 1);
    getHolder().addCallback(this);
    setFocusableInTouchMode(true);
    setKeepScreenOn(true);
    for (int i = 0; i < 4; i++) briefMovies.add(null);
    worker =
        new Thread(
            null,
            () -> {
              // A replacement Activity waits for the previous engine to release JNI state.
              synchronized (NativeSurface.class) {
                loop();
              }
            },
            "StarDroid native renderer",
            8L * 1024 * 1024);
    worker.start();
    Choreographer.getInstance().postFrameCallback(this);
  }

  void enqueue(Runnable r) {
    tasks.add(r);
    synchronized (gate) {
      gate.notifyAll();
    }
  }

  private void loadGraphicsSettings() {
    smoothGraphics = prefs.getBoolean("smooth-graphics", true);
    NativeArt.setSmoothGraphics(smoothGraphics);
  }

  void resume() {
    foreground = true;
    enqueue(
        () -> {
          lastTime = 0;
          if (audio != null) audio.foreground(true);
          syncAudio();
        });
  }

  // A remounted card invalidates old mmap-backed archive handles. Reopen data
  // on the engine worker without ending or rebuilding the live simulation.
  void reopenStorage(Runnable ready,java.util.function.Consumer<String> failed) {
    enqueue(()->{
      try {
        if(!GameDataStorage.available(activity,gameFiles))throw new IOException("Game data storage is unavailable.");
        loadArchives();
        if(activeMapSource!=null)NativeBridge.open(3,activeMapSource.getPath());
        lastTime=0;accumulator=0;
        activity.runOnUiThread(ready);
      }catch(Exception e){
        AppDiagnostics.recordFailure("Reopen game data after storage removal",e);
        activity.runOnUiThread(()->failed.accept(e.getMessage()));
      }
    });
  }

  void suspend() {
    foreground = false;
    enqueue(
        () -> {
          accumulator = 0;
          resetDesktop(true);
          resetGamepad();
          cancelGesture();
          if (audio != null) audio.foreground(false);
          syncAudio();
        });
  }

  void title() {
    enqueue(
        () -> {
          try {
            NativeBridge.endGame();
            loadArchives();
            art = new NativeArt();
            menuLayouts.clear();
            showTitle();
          } catch (Exception e) {
            error(e);
          }
        });
  }

  void shutdown() {
    if(nearbyRadio!=null)nearbyRadio.close();
    running = false;
    Choreographer.getInstance().removeFrameCallback(this);
    synchronized (gate) {
      gate.notifyAll();
    }
  }

  void checkDataMove(java.util.function.Consumer<Boolean> callback) {
    if(worker==null||!worker.isAlive()){callback.accept(true);return;}
    enqueue(()->{boolean allowed=!activeGame && nearby==null && nearbyRadio==null && pendingScreen==null && !page.equals("loading") && !page.equals("briefing") && !page.equals("story") && !page.equals("cinematic");
      activity.runOnUiThread(()->callback.accept(allowed));});
  }

  void awaitShutdown() throws java.io.IOException {
    try {if(worker!=null)worker.join();}
    catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.IOException("Game shutdown interrupted.",e);}
  }

  String diagnostics() {
    return diagnostics+"\n"+lastNetworkDiagnostics;
  }

  @Override
  public void surfaceCreated(SurfaceHolder h) {
    synchronized (surfaceGate) {
      surface = true;
    }
    synchronized (gate) {
      gate.notifyAll();
    }
  }

  @Override
  public void surfaceChanged(SurfaceHolder h, int format, int w, int height) {
    physicalWidth = w;
    physicalHeight = height;
    enqueue(
        () -> {
          if (activeGame) resizeWorld();
        });
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder h) {
    resetDesktopInput();resetGamepadInput();
    // Wait for any outstanding Canvas post before Android destroys the surface.
    synchronized (surfaceGate) {
      surface = false;
    }
  }

  @Override
  public void doFrame(long nanos) {
    if (!running) return;
    requested = nanos;
    synchronized (gate) {
      gate.notifyAll();
    }
    Choreographer.getInstance().postFrameCallback(this);
  }

  private void loop() {
    long processed = 0;
    try {
      // The previous Activity's renderer has released the shared drawing state.
      loadGraphicsSettings();
      loadArchives();
      art = new NativeArt();
      missions =
          new JSONArray(
              new String(
                  NativeData.read(activity.getAssets().open("native/missions.json"), 262144),
                  "UTF-8"));
      commandMaps =
          new JSONObject(
              new String(
                  NativeData.read(activity.getAssets().open("native/commands.json"), 262144),
                  "UTF-8"));
      audio = new NativeAudio(activity, () -> enqueue(this::syncAudio));
      audio.foreground(foreground);
      showTitle();
      while (running) {
        Runnable task;
        while ((task = tasks.poll()) != null) {
          try {
            task.run();
          } catch (Exception e) {
            error(e);
          }
        }
        if(nearby!=null&&nearby.playing&&nearby.finished()&&(!foreground||!surface))nearby.tick(16);
        if(nearby!=null)nearby.pulse(!foreground || (page.equals("game") && !modal.isEmpty() && lastOutcome==0));
        long frame = requested;
        if (!surface
            || !foreground
            || frame == processed
            || (page.equals("game") && modal.isEmpty() && frame - processed < 1000000000L / fps - 500000)) {
          synchronized (gate) {
            gate.wait(16);
          }
          continue;
        }
        processed = frame;
        double now = frame / 1000000.,
            dt = lastTime == 0 ? 0 : Math.max(0, Math.min(100, now - lastTime));
        lastTime = now;
        clock += dt;
        long begin = System.nanoTime();
        try {
          tick(dt);
          long drawBegin=System.nanoTime();
          synchronized (surfaceGate) {
            if (!surface) continue;
            Canvas c = null;
            try {
              c = getHolder().lockHardwareCanvas();
              if (c != null) {
                renderCanvas(c, dt);
              }
            } finally {
              if (c != null) getHolder().unlockCanvasAndPost(c);
            }
          }
          drawWindowMs+=(System.nanoTime()-drawBegin)/1e6;
        } catch (Exception e) {
          error(e);
        }
        double ms = (System.nanoTime() - begin) / 1e6;
        renderedFrames++;
        totalRenderMs += ms;
        maxRenderMs = Math.max(maxRenderMs, ms);
        frameWindowMs+=ms;
        long diagnosticsNow=System.nanoTime()/1000000;
        if (diagnosticsAt==0)diagnosticsAt=diagnosticsNow;
        if (diagnosticsNow-diagnosticsAt>=1000) {
          long count=renderedFrames-diagnosticsFrames;
          if(!activeGame || (page.equals("game") && modal.isEmpty())) {
            displayFps=count*1000.0/(diagnosticsNow-diagnosticsAt);
            drawMs=count>0?drawWindowMs/count:0;frameMs=count>0?frameWindowMs/count:0;
          }
          diagnosticsAt=diagnosticsNow;diagnosticsFrames=renderedFrames;drawWindowMs=frameWindowMs=0;
          if(nearby!=null)lastNetworkDiagnostics=nearby.diagnostics()+"\n"+(nearbyRadio==null?"":nearbyRadio.diagnostics());
          diagnostics =
              "Runtime: native C++ / JNI ("
                  + Build.SUPPORTED_ABIS[0]
                  + ")\n"
                  + "Display: Android SurfaceView + hardware Canvas\n"
                  + "Audio: native PCM mixer / AudioTrack\n"
                  + "WebView: not used\n"
                  + "Frames drawn: "
                  + renderedFrames
                  + "\nRecent display rate: "+String.format(Locale.ROOT,"%.1f FPS; draw %.2f ms/frame; total frame work %.2f ms/frame",displayFps,drawMs,frameMs)
                  + "\nAverage frame work: "
                  + String.format(Locale.ROOT, "%.2f", totalRenderMs / renderedFrames)
                  + " ms\nWorst frame work: "
                  + String.format(Locale.ROOT, "%.2f", maxRenderMs)
                  + " ms\nSimulation frame: "
                  + (activeGame ? NativeBridge.value(8, 0) : 0)
                  + "\nMission: "
                  + missionCode
                  + "\nNearby: " + (nearby==null ? "Inactive" : (nearby.host ? "Host" : "Guest")+" / "+nearby.status)
                  + "\nLast error: "
                  + failure;
          AppDiagnostics.session("Page: " + page + "; active match=" + activeGame + "\n" + diagnostics);
        }
      }
    } catch (Throwable e) {
      AppDiagnostics.recordFailure("Renderer stopped", e);
      failure = e.toString();
      activity.runOnUiThread(() -> activity.nativeFailure(failure));
    } finally {
      if (audio != null) audio.close();
      if (hud != null) hud.close();
      closeGlue();
      closeBriefMovies();
      NativeBridge.close();
    }
  }

  private void loadArchives() throws Exception {
    musicThemeStarts.clear();
    String[] core = {"STARDAT.MPQ", "BROODAT.MPQ", "patch_rt.mpq"};
    for (int i = 0; i < 3; i++)
      NativeBridge.open(i, new File(new File(gameFiles, "mpq"), core[i]).getPath());
    String detectedLanguage = GameLanguage.detect(NativeBridge.asset(-1, "rez/stat_txt.tbl"));
    GameLanguage.russianData = detectedLanguage.equals("ru");
    if (prefs.getBoolean("ui-language-auto", true)) PortText.select(detectedLanguage);
    prefs.edit().putBoolean("russian-data", GameLanguage.russianData).apply();
    String[] bundle = {
      InterfaceData.NAME,
      MusicData.NAME,
      "StarDroid-Music-BroodWar.zip",
      StoryData.NAME,
      "StarDroid-Story-BroodWar.zip",
      StoryData.MOVIES,
      StoryData.EXP_MOVIES
    };
    availableMovies.clear();
    for (int i = 0; i < bundle.length; i++) {
      File source = ProjectFiles.existing(gameFiles, bundle[i]);
      if (i < 5) {
        File p = NativeData.prepare(source, cache, "content" + i);
        NativeBridge.open(4 + i, p == null ? "" : p.getPath());
      } else {
        // Cinematics are opened individually on demand. Close the old mapping
        // before reclaiming full-movie caches created by earlier versions.
        NativeBridge.open(4 + i, "");
        NativeData.discard(cache, "content" + i);
      }
      if (i >= 5 && source.isFile()) try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(source)) {
        JSONArray entries = new JSONObject(new String(NativeData.read(z.getInputStream(z.getEntry("manifest.json")), 131072), "UTF-8")).getJSONArray("entries");
        for (int j = 0; j < entries.length(); j++) availableMovies.add(NativeArt.normal(entries.getJSONObject(j).getString("name")));
      }
    }
  }

  private void error(Exception e) {
    AppDiagnostics.recordFailure("Engine error (returned to menu)", e);
    closeNearby();
    failure = e.toString();
    android.util.Log.e("StarDroid", "Native host", e);
    activeGame = false;
    page = "main";
    logicalWidth = 640;
    closeGlue();
    if (hud != null) {
      hud.close();
      hud = null;
    }
    modal = "error";
    hint = failure;
  }

  private void playIntroduction() {
    modal = "intro-choose";
  }

  private boolean playCinematic(String path, boolean broodwar, Runnable next, boolean explainMissing) {
    if (!availableMovies.contains(path)) {
      if (explainMissing) message("Import " + (broodwar ? "BroodWar.mpq" : "StarCraft.mpq") + " in App Options to play this cinematic.");
      return false;
    }
    try {
      closeGlue(); closeBriefMovies();
      if (hud != null) {hud.close(); hud = null;}
      NativeBridge.endGame(); activeGame = false;
      NativeBridge.sound(null, 63, false);
      int movieSlot = broodwar ? 10 : 9;
      try {
        File source = ProjectFiles.existing(gameFiles, broodwar ? StoryData.EXP_MOVIES : StoryData.MOVIES);
        File movie = NativeData.prepareEntry(source, cache, "cinematic", path);
        if (movie == null) throw new IOException("Imported cinematic is unavailable");
        NativeBridge.open(movieSlot, movie.getPath());
        cinematic = new NativeArt.Movie(movieSlot, path);
      } finally {
        // libsmacker copies the movie into its own storage during movieOpenAsset.
        // Neither its video frames nor audio depend on this temporary MPQ afterward.
        NativeBridge.open(movieSlot, "");
        NativeData.discard(cache, "cinematic");
      }
      cinematicDone = next;
      cinematicStart = clock;
      page = "movie"; logicalWidth = 640; modal = "";
      return true;
    } catch (Exception e) {error(e); return true;}
  }

  private void finishCinematic() {
    if (cinematic != null) {cinematic.close(); cinematic = null;}
    Runnable next = cinematicDone; cinematicDone = null;
    if (next != null) next.run(); else showMain();
  }

  private void chooseMission(JSONObject m) {
    if (m.has("cinematic")) {
      final int race = listRace;
      playCinematic(m.optString("cinematic"), m.optString("game").equals("broodwar"),
          () -> {showCampaign(); missionList(race);}, true);
    } else loadMission(m);
  }

  private void closeGlue() {
    if(replayThumbnail!=null){replayThumbnail.recycle();replayThumbnail=null;}
    replayPreviewUnavailable=false;
    hoverButton = -1;
    commandHover.cancel();
    if (glue != null) {
      glue.close();
      glue = null;
    }
  }

  private void closeBriefMovies() {
    for (int i = 0; i < 4; i++) {
      if (briefMovies.get(i) != null) briefMovies.get(i).close();
      briefMovies.set(i, null);
    }
  }

  private void screen(String name, String bin, String pal, boolean slide) {
    closeGlue();
    menuHoverX=menuHoverY=-1;hoverButton=-1;
    page = name;
    logicalWidth = 640;
    modal = "";
    try {
      glue = new NativeGlue(art, bin, pal, slide);
    } catch (Exception e) {
      failure = "Interface: " + e.getMessage();
      AppDiagnostics.recordFailure("Menu loading failed: " + bin, e);
      glue = null;
    }
    lastTime = 0;
    slideLocked = !slide;
  }

  private void showTitle() {
    showMain();
    try {
      try {
        titleArtwork = new NativeArt.PCX(NativeBridge.asset(expansion ? 1 : 0,
            "glue/title/title.pcx"), false, -1).image;
      } catch (RuntimeException unavailable) {
        titleArtwork = art.pcx("glue/title/title.pcx", false, -1).image;
      }
      page = "title";
    } catch (RuntimeException unavailable) {
      // The optional splash must never prevent opening the game menu.
      titleArtwork = null;
    }
  }

  private void showMain() {
    if(replayGame)expansion=replayReturnExpansion;replayGame=false;
    multiplayerSaves=false;multiplayerGame=false;
    closeNearby();
    results = null;
    if (cinematic != null) {
      cinematic.close();
      cinematic = null;
    }
    NativeBridge.endGame();
    activeGame = false;
    campaign = null;
    currentMission = null;
    lastOutcome = 0;
    if (hud != null) {
      hud.close();
      hud = null;
    }
    closeBriefMovies();
    screen("main", "glumain", "palmm", false);
    music("title");
  }

  private void showReplays() {
    replayBrowserGeneration++;
    if(replayGame)expansion=replayReturnExpansion;
    replayGame=false;activeGame=false;campaign=null;currentMission=null;lastOutcome=0;
    closeNearby();NativeBridge.endGame();closeBriefMovies();
    if(hud!=null){hud.close();hud=null;}
    results=null;multiplayerGame=false;multiplayerSaves=false;
    screen("replays","gluload","palnl",true);
    if(glue==null)screen("replays","glucustm","palnl",true);
    if(glue!=null)sfx("swishin");
    music("title");refreshReplays();
  }

  private void refreshReplays() {
    try {
      replayFiles=replays.list();
      if(selectedReplay==null||!replayFiles.contains(selectedReplay))selectedReplay=replayFiles.isEmpty()?null:replayFiles.get(0);
      replayOffset=Math.min(replayOffset,replayMaxOffset());selectReplay(selectedReplay);
    } catch(Exception e){replayFiles=Collections.emptyList();selectReplay(null);replayMessage(e.getMessage());}
  }

  private RectF replayListBounds() {
    NativeArt.Widget box=glue==null?null:glue.layout.get(glue.name.equals("gluload")?6:5);
    return box==null?new RectF(60,130,348,248):box.rect();
  }
  private float replayMaxOffset(){return Math.max(0,replayFiles.size()*22-replayListBounds().height());}
  private void selectReplay(File file) {
    if(replayThumbnail!=null)replayThumbnail.recycle();replayThumbnail=null;replayPreviewUnavailable=false;
    selectedReplay=file;replayDetails="";replayValid=false;replayPlayers.clear();replayRaces.clear();replayNotice="";
    if(file==null)return;
    try {
      byte[] data=replays.read(file);
      String[] rows=GameLanguage.decode(NativeBridge.replayInfo(data)).split("\n");
      String[] info=rows[0].split("\t",-1);replayExpansion=info[1].equals("1");
      replayDetails=info[3]+"\n"+(replayExpansion?"Brood War":"StarCraft")+"\n"+replayTime(Integer.parseInt(info[0]));
      for(int i=1;i<rows.length;i++){String[] player=rows[i].split("\t",-1);replayPlayers.add(Integer.parseInt(player[0]));replayRaces.add(Integer.parseInt(player[1]));replayDetails+="\n"+player[2]+" · "+MatchSetup.RACES[Integer.parseInt(player[1])%3];}
      replayValid=true;
      try {replayThumbnail=mapPreviews.create(NativeBridge.replayScenario(data));}
      catch(Exception unavailable){replayPreviewUnavailable=true;}
    }catch(Exception e){replayMessage(e.getMessage());}
  }

  private static String replayTime(int frames){int seconds=Math.max(0,frames)/24;return String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}

  private void drawReplayBrowser(Canvas c) {
    if(glue==null){drawMenuUnavailable(c,this::showCampaign);return;}
    if(glue.exiting||glue.elapsed<NativeGlue.ENTER_MS)return;
    uiLine(c,"Load Replay",glue.large,40,66,290,22,0,2);
    // The legacy custom-game list is much shorter and lower than gluload's list.
    // Use the imported BIN's list rectangle for both clipping and touch targets.
    RectF box=replayListBounds();float right=box.right-20;
    replayOffset=Math.max(0,Math.min(replayOffset,replayMaxOffset()));
    c.save();c.clipRect(box.left,box.top,right,box.bottom);
    if(replayFiles.isEmpty())glue.small.draw(c,"No saved replays",box.left+4,box.top+8,(int)(right-box.left-8),0,2,0);
    for(int i=Math.max(0,(int)(replayOffset/22));i<replayFiles.size()&&i*22-replayOffset<box.height();i++){
      final File file=replayFiles.get(i);float y=box.top+i*22-replayOffset;
      uiLine(c,NativeReplays.label(file),glue.small,box.left+4,y,(int)(right-box.left-8),22,0,file.equals(selectedReplay)?4:2);
      add(new RectF(box.left,Math.max(box.top,y),right,Math.min(box.bottom,y+22)),()->selectReplay(file),true);
    }
    c.restore();
    drawReplayScrollbar(c,box);
    if(glue.name.equals("gluload")){
      try {c.drawBitmap(art.pcx("glue/load/pinfo.pcx",true,-1).image,388,55,NativeArt.PIXELS);}
      catch(Exception missing){replayPanel(c,390,104,218,250);}
      drawReplayDetails(c,404,112,190,224);
    }else{
      // glucustm already supplies the right-hand original pInfo background.
      // Never cover it with the unrelated generic blue dialog panel.
      drawReplayDetails(c,410,58,190,244);
    }
    replayActionButton(c,"Import Replay",44,332,164,22,()->activity.runOnUiThread(activity::pickReplay),true);
    replayActionButton(c,"Export",218,332,136,22,()->{
      try{byte[] data=replays.read(selectedReplay);final long request=replayBrowserGeneration;activity.runOnUiThread(()->activity.saveReplay(data,this,request));}
      catch(Exception e){replayMessage("Replay could not be saved: "+e.getMessage());}
    },selectedReplay!=null);
    replayButton(c,"Delete",98,419,164,22,()->modal="replay-delete",selectedReplay!=null);
    replayMenuAction(c,glue.name.equals("gluload")?4:12,"OK",this::openSelectedReplay,replayValid);
    replayMenuAction(c,glue.name.equals("gluload")?5:13,"Cancel",()->navigate(this::showCampaign),true);
  }

  private void openSelectedReplay() {
    if(!replayValid||selectedReplay==null)return;
    final File file=selectedReplay;
    navigate(()->startReplay(file));
  }

  private void drawReplayDetails(Canvas c,int x,int y,int width,int height) {
    c.save();c.clipRect(x,y,x+width,y+height);
    if(replayValid) {
      final int thumbnailHeight=96;
      paint.setColor(0xff05080c);c.drawRect(x+(width-96)/2f,y,x+(width+96)/2f,y+thumbnailHeight,paint);
      if(replayThumbnail!=null) {
        float scale=Math.min(96f/replayThumbnail.getWidth(),96f/replayThumbnail.getHeight());
        float w=replayThumbnail.getWidth()*scale,h=replayThumbnail.getHeight()*scale;
        c.drawBitmap(replayThumbnail,null,new RectF(x+(width-w)/2f,y+(thumbnailHeight-h)/2f,x+(width+w)/2f,y+(thumbnailHeight+h)/2f),NativeArt.PIXELS);
      }else if(replayPreviewUnavailable)uiLine(c,"Preview unavailable",glue.small,x,y+38,width,20,1,2);
      y+=104;height-=104;
    }
    String[] rows=replayDetails.split("\n");NativeArt.Font font=glue.small;int lineHeight=14;
    if(rows.length*lineHeight>height){font=glue.tiny;lineHeight=Math.max(font.height,Math.min(12,height/Math.max(1,rows.length)));}
    // A single fitted row per player keeps all eight players below the preview.
    for(int i=0;i<rows.length&&i*lineHeight<height;i++)uiLine(c,rows[i],font,x,y+i*lineHeight,width,lineHeight,0,2);
    c.restore();
  }

  private void drawReplayScrollbar(Canvas c,RectF box) {
    float x=box.right-18,y=box.top,max=replayMaxOffset();int height=Math.round(box.height());
    glue.scrollbar(c,x,y,height,max==0?0:replayOffset/max);
    add(new RectF(x-2,y,box.right,y+16),()->replayOffset=Math.max(0,replayOffset-22),max>0);
    add(new RectF(x-2,box.bottom-16,box.right,box.bottom),()->replayOffset=Math.min(replayMaxOffset(),replayOffset+22),max>0);
    Button bar=new Button(new RectF(x-2,y+16,box.right,box.bottom-16),()->{},max>0);bar.vertical=true;
    final float travel=height-46;final long[] dragStarted={Long.MIN_VALUE};final float[] grip={0};
    bar.slider=v->{
      float maximum=replayMaxOffset(),position=v*bar.rect.height()-7;
      if(dragStarted[0]!=downAt){dragStarted[0]=downAt;float thumb=maximum>0?replayOffset/maximum*travel:0;grip[0]=Math.abs(position-thumb)<=7?position-thumb:0;}
      replayOffset=Math.max(0,Math.min(maximum,(position-grip[0])/travel*maximum));
    };
    buttons.add(bar);
  }

  private NativeArt.Font replayFont(boolean large) {
    NativeArt.Font source=page.equals("results")&&results!=null?(large?results.large:results.small)
        :hud!=null?(large?hud.large:hud.small):glue==null?null:(large?glue.large:glue.small);
    if(source==null)return null;
    if(large){if(replayLargeSource!=source){replayLargeSource=source;replayLargeGreen=source.withShadedColor(0xff76b948);}return replayLargeGreen;}
    if(replaySmallSource!=source){replaySmallSource=source;replaySmallGreen=source.withShadedColor(0xff76b948);}return replaySmallGreen;
  }

  private void replayPanel(Canvas c,float x,float y,int width,int height) {
    paint.setColor(Color.BLACK);c.drawRect(x,y,x+width,y+height,paint);
    redBorder(c,x,y,width,height,false);redBorder(c,x+3,y+3,width-6,height-6,false);
  }

  private void replayMessage(String message) {replayNotice=message==null?"":message;modal="replay-notice";cancelGesture();}

  private boolean drawReplayDialog(Canvas c) {
    if(!modal.equals("replay-delete")&&!modal.equals("replay-notice"))return false;
    boolean deleting=modal.equals("replay-delete");float x=(logicalWidth-416)/2f;
    paint.setColor(0x98000000);c.drawRect(0,0,logicalWidth,480,paint);replayPanel(c,x,126,416,222);
    NativeArt.Font heading=replayFont(true),small=replayFont(false);
    if(deleting&&heading!=null)uiLine(c,"Delete replay?",heading,x+16,142,384,26,1,2);
    String message=deleting?(selectedReplay==null?"":NativeReplays.label(selectedReplay)):replayNotice;
    c.save();c.clipRect(x+28,178,x+388,262);
    NativeArt.Font messageFont=deleting?small:heading;
    if(messageFont!=null&&messageFont.lines(message,360).size()>4)messageFont=small;
    if(messageFont!=null)messageFont.draw(c,message,x+28,178,360,deleting?0:1,2,20);else fallback(c,message,x+28,178,14,0xffc5cce3);
    c.restore();
    if(deleting){
      replayButton(c,"Delete",x+28,282,160,28,()->{try{replays.delete(selectedReplay);modal="";refreshReplays();}catch(Exception e){replayMessage(e.getMessage());}},selectedReplay!=null);
      replayButton(c,"Cancel",x+228,282,160,28,()->modal="",true);
    }else replayButton(c,"OK",x+128,282,160,28,()->modal="",true);
    return true;
  }

  void replayImported(String issue){enqueue(()->{if(page.equals("replays")){refreshReplays();if(issue!=null)replayMessage(issue);}});}

  private void saveCurrentReplay() {
    try{
      byte[] bytes=NativeBridge.replay();replaySaveScreen=results;final long request=++replaySaveRequest;
      activity.runOnUiThread(()->activity.recordReplay(bytes,this,request));
    }catch(Exception e){replayMessage("Replay could not be saved: "+e.getMessage());}
  }

  void replayRecorded(long request,String message) {
    enqueue(()->{if(request==replaySaveRequest&&page.equals("results")&&results==replaySaveScreen){
      final NativeResults savedScreen=replaySaveScreen;
      replayAcknowledgement=()->{if(page.equals("results")&&results==savedScreen)replayMessage(message);};
    }});
  }

  void replayExported(long request,String message) {
    enqueue(()->{replayAcknowledgement=()->{if(page.equals("replays")&&request==replayBrowserGeneration)replayMessage(message);};});
  }

  private void presentReplayAcknowledgement() {
    if(replayAcknowledgement!=null&&modal.isEmpty()){Runnable notice=replayAcknowledgement;replayAcknowledgement=null;notice.run();}
  }

  private void startReplay(File file) {
    if(file==null)return;
    try {
      byte[] bytes=replays.read(file);selectReplay(file);if(!replayValid)return;
      if(!replayGame)replayReturnExpansion=expansion;
      closeGlue();closeNearby();closeBriefMovies();if(hud!=null){hud.close();hud=null;}
      NativeBridge.replayOpen(bytes);replayGame=true;activeGame=true;replayPaused=false;replayRate=1;replaySeeking=false;
      playingReplay=file;expansion=replayExpansion;campaign=null;currentMission=null;missionCode=0;
      missionRace=replayRaces.isEmpty()?1:replayRaces.get(0);multiplayerGame=false;multiplayerSaves=false;lastOutcome=0;errorSeq=-1;commandError="";
      releaseSavedMap();activeMapSource=null;
      hud=new NativeHud(art,missionRace,commandMaps);page="game";modal="";startupTips=false;instant=false;
      resizeWorld();syncControls();NativeBridge.frame(world,0,clock);NativeBridge.sound(null,62,false);
      music("game");accumulator=0;lastTime=0;cancelGesture();
    }catch(Exception e){showReplays();replayMessage(e.getMessage());}
  }

  private void replayMenuAction(Canvas c,int id,String label,Runnable action,boolean enabled) {
    NativeArt.Widget widget=glue.layout.get(id);
    if(widget==null||!glue.labelVisible(id))return;
    // Keep the original OK/Cancel artwork, hit box and font metrics intact.
    boolean available=enabled&&action!=null&&pendingScreen==null;
    queueMenuButtonVisual(widget.hit(),action,available,enabled&&action!=null,MenuLabels.forWidget(glue.name,id,widget,label),
        shown->glue.labelCaption(c,id,shown,enabled&&action!=null));
  }

  private void replayButton(Canvas c,String label,float x,float y,int width,int height,Runnable action,boolean enabled) {
    replayButton(c,label,x,y,width,height,action,enabled,true);
  }

  private void replayActionButton(Canvas c,String label,float x,float y,int width,int height,Runnable action,boolean enabled) {
    replayButton(c,label,x,y,width,height,action,enabled,false);
  }

  private void replayButton(Canvas c,String label,float x,float y,int width,int height,Runnable action,boolean enabled,boolean navigation) {
    if(page.equals("replays")&&glue!=null){
      // Transparent menu controls use the original wide button font. Long
      // translations fit exactly as native menu labels do, without smaller fonts.
      redBorder(c,x,y,width,height,false);
      NativeArt.Font font=glue.button;
      queueMenuButton(new RectF(x,y,x+width,y+height),action,enabled,label,navigation,shown->{
        int layoutWidth=NativeGlue.labelWidth(font,shown,width-8,height);
        int textHeight=font.lines(shown,layoutWidth).size()*font.height;
        c.save();c.clipRect(x+4,y,x+width-4,y+height);
        c.translate(x+4,y+(height-textHeight)/2f);c.scale((width-8)/(float)layoutWidth,1);
        font.draw(c,shown,0,0,layoutWidth,1,menuColor(x,y,width,height,enabled),0);c.restore();
      });return;
    }
    paint.setColor(Color.BLACK);c.drawRect(x,y,x+width,y+height,paint);
    boolean hot=pressed!=null&&pressed.rect.contains(x+width/2f,y+height/2f);
    redBorder(c,x,y,width,height,hot&&enabled);
    NativeArt.Font large=replayFont(true),small=replayFont(false);
    final NativeArt.Font font=large!=null&&large.measure(label)>width-8?small:large;
    queueMenuButton(new RectF(x,y,x+width,y+height),action,enabled,label,navigation,
        shown->{if(font!=null)uiLine(c,shown,font,x+4,y,width-8,height,1,enabled?hot?4:3:5);
          else fallbackMenu(c,shown,x+4,y,width-8,height,enabled?3:5);});
  }

  private void seekReplay(float value) {
    if(!replayGame)return;
    NativeBridge.replaySeek(Math.round(Math.max(0,Math.min(1,value))*NativeBridge.value(26,0)));
    replaySeeking=NativeBridge.value(28,0)!=0;accumulator=0;
  }

  private void drawReplayControls(Canvas c) {
    float x=logicalWidth-156;int frame=NativeBridge.value(8,0),end=NativeBridge.value(26,0);
    boolean seeking=NativeBridge.value(28,0)!=0;int target=seeking?NativeBridge.value(29,0):frame;
    replayPanel(c,x,348,154,130);
    uiLine(c,seeking?PortText.text("Seeking…")+" "+replayTime(frame)+" / "+replayTime(target):replayTime(frame)+" / "+replayTime(end),replayFont(false),x+6,352,142,18,1,2);
    // Pixel rail and hollow grip; its endpoints are identical to the touch scale.
    float left=x+13,right=x+141,center=left+(end>0?target/(float)end:0)*(right-left);
    paint.setColor(0xff861414);c.drawRect(left,377,right,378,paint);c.drawRect(left,382,right,383,paint);
    c.drawRect(left,377,left+1,383,paint);c.drawRect(right-1,377,right,383,paint);
    paint.setColor(0xffd92b24);c.drawRect(left,379,left+(end>0?frame/(float)end:0)*(right-left),381,paint);
    paint.setColor(Color.BLACK);c.drawRect(center-5,375,center+6,385,paint);
    int cx=Math.round(center);paint.setColor(0xffd92b24);
    c.drawRect(cx-3,373,cx+4,374,paint);c.drawRect(cx-3,386,cx+4,387,paint);
    for(int row=-6;row<=5;row++){int inset=Math.abs(row)>=5?2:Math.abs(row)>=3?1:0;
      c.drawRect(cx-6+inset,380+row,cx-5+inset,381+row,paint);c.drawRect(cx+6-inset,380+row,cx+7-inset,381+row,paint);}
    Button seek=new Button(new RectF(left,370,right,390),()->{},end>0);seek.slider=this::seekReplay;buttons.add(seek);
    replayActionButton(c,replayPaused?"Play":"Pause",x+6,398,104,21,()->{if(NativeBridge.value(8,0)<NativeBridge.value(26,0)||NativeBridge.value(28,0)!=0)replayPaused=!replayPaused;},true);
    replayActionButton(c,replayRate+"x",x+116,398,32,21,()->{replayRate=replayRate==8?1:replayRate*2;accumulator=0;},true);
    int owner=NativeBridge.value(27,0);
    replayActionButton(c,owner<0?"Everyone":"Player "+(owner+1),x+6,425,142,21,()->{
      int at=replayPlayers.indexOf(NativeBridge.value(27,0))+1;
      NativeBridge.command(31,at>=replayPlayers.size()?-1:replayPlayers.get(at),0,0,0);
      if(at<replayRaces.size()&&missionRace!=replayRaces.get(at)){missionRace=replayRaces.get(at);hud.close();hud=new NativeHud(art,missionRace,commandMaps);music("game");}
    },true);
    replayButton(c,"Restart",x+6,452,142,21,()->startReplay(playingReplay),true);
  }

  private void showCampaign() {
    if(replayGame)expansion=replayReturnExpansion;replayGame=false;
    multiplayerSaves=false;multiplayerGame=false;
    closeNearby();
    NativeBridge.endGame();
    activeGame = false;
    campaign = null;
    closeBriefMovies();
    if (hud != null) {
      hud.close();
      hud = null;
    }
    music("title");
    screen("campaign", expansion ? "gluexpcmpgn" : "glucmpgn", "palcs", true);
    sfx("swishin");
  }

  private void startScoreCount() {
    results.progress=0;results.counting=true;results.countElapsed=0;
    try {results.audioCounting=NativeBridge.scoreSound(art.read("sound/glue/scorefill.wav"),art.read("sound/glue/scorefillend.wav"),NativeResults.COUNT_MS)>0;}
    catch(Exception e){results.audioCounting=false;failure="Score sound: "+e.getMessage();}
  }

  private void sfx(String name) {
    try {
      byte[] b =
          art.read(
              "sound/"
                  + (name.equals("button") || name.equals("youwin") || name.equals("youlose")
                      ? "misc/"
                      : "glue/")
                  + name
                  + ".wav");
      if (b != null) NativeBridge.sound(b,
          name.equals("youwin") || name.equals("youlose") ? 59
          : name.equals("swishlock") ? 60 : name.startsWith("swish") ? 58 : name.equals("scorefill") ? 57 : 61, false);
    } catch (Exception e) {
      failure = "Sound: " + e.getMessage();
    }
  }

  private void music(String phase) {
    if (!musicPhase.equals(phase)) musicIndex = 0;
    musicPhase = phase;
    // Classic BroodWar.mpq stores the expansion theme followed by the original
    // first theme in race1.wav for ALL three races. Playing each complete file
    // before race2/race3 gives four themes per loop in campaigns, custom games
    // and Nearby matches; adding StarCraft race1 again would duplicate it.
    String race = new String[] {"zerg", "terran", "protoss"}[missionRace],
        prefix = new String[] {"z", "t", "p"}[missionRace];
    String name =
        "music/"
            + (phase.equals("title")
                ? "title"
                : phase.equals("briefing")
                    ? prefix + "rdyroom"
                    : phase.equals("victory")
                        ? prefix + "vict"
                        : phase.equals("defeat") ? prefix + "defeat" : race + (musicIndex % 3 + 1))
            + ".wav";
    // The title track continues across front-end expansion changes. Other
    // phases still reload the matching game's archive, even for the same path.
    if (name.equals(currentMusicPath) && (phase.equals("title") || expansion == currentMusicExpansion)
        && NativeBridge.playing(63)) return;
    currentMusicPath = name;
    currentMusicExpansion = expansion;
    musicThemeBoundary = 0;
    try {
      byte[] b = expansion ? NativeBridge.asset(6, name) : null;
      if (b != null && phase.equals("game") && name.matches("music/(terran|zerg|protoss)1\\.wav")) {
        Integer boundary = musicThemeStarts.get(name);
        if (boundary == null) {
          try { boundary = MusicThemes.appendedThemeMs(b, NativeBridge.asset(5, name)); }
          catch (Exception ignored) { boundary = 0; } // A reference file must not block valid music.
          musicThemeStarts.put(name, boundary);
        }
        musicThemeBoundary = boundary;
      }
      if (b == null) b = NativeBridge.asset(5, name);
      if (b != null) NativeBridge.sound(b, 63, !phase.equals("game") && !phase.equals("victory") && !phase.equals("defeat"));
      else NativeBridge.sound(null, 63, false);
    } catch (Exception e) {
      failure = "Music: " + e.getMessage();
    }
  }

  private void advanceMusic() {
    if (musicPhase.equals("game") && music > 0 && !NativeBridge.playing(63)) {
      musicIndex++;
      music("game");
    }
  }

  private boolean canNextMusicTrack() {
    return musicPhase.equals("game") && !muted && music > 0
        && NativeBridge.playing(63);
  }

  private void nextMusicTrack() {
    if (!canNextMusicTrack()) return;
    // Brood War's first WAV contains two complete themes. Skip to the verified
    // second theme before moving to race2/race3; natural playback stays intact.
    double position = NativeBridge.soundPosition(63);
    if (musicThemeBoundary > 0 && position >= 0 && position < musicThemeBoundary)
      NativeBridge.seekSound(63, musicThemeBoundary);
    else { musicIndex++; music("game"); }
  }

  private void drawSoundSettings(Canvas c, float x, float y) {
    toggle(c, "Mute all sound", muted, x + 26, y + 60, () -> {muted = !muted; saveSettings();});
    slider(c, "Effects", effects, x + 26, y + 120, v -> {effects = v; saveSettings();});
    slider(c, "Music", music, x + 26, y + 180, v -> {music = v; saveSettings();});
    menuActionButton(c, "Next track", x + 26, y + 240, 320, 28, this::nextMusicTrack, canNextMusicTrack());
  }

  private void tick(double dt) {
    float targetGain = campaign != null && NativeBridge.playing(62) ? .35f : 1;
    speechMusicGain += (targetGain - speechMusicGain) * (float)(1 - Math.exp(-dt / (targetGain < speechMusicGain ? 90 : 240)));
    syncAudio();
    advanceLists(dt);
    advanceGamepad(dt);
    advanceDesktop(dt);
    if ((modal.startsWith("gamepad") || modal.equals("control-desktop")) && !page.equals("game")) {
      if (page.equals("movie")) cinematicStart += dt;
      return;
    }
    if (page.equals("movie")
        && cinematic != null
        && clock - cinematicStart >= cinematic.frames * (double) cinematic.interval) {
      finishCinematic();
      return;
    }
    if (pendingScreen != null && (page.equals("results") && results != null
        ? results.exiting && results.animationElapsed >= NativeGlue.EXIT_MS
        : glue != null && glue.elapsed >= NativeGlue.EXIT_MS)) {
      Runnable next = pendingScreen;
      pendingScreen = null;
      next.run();
      return;
    }
    if(page.equals("results") && results!=null && results.counting && results.audioCounting) {
      double position=NativeBridge.soundPosition(57);results.progress=position<0?1:Math.min(1,position/NativeResults.COUNT_MS);
    }
    if (page.equals("results") && results != null && results.ready() && !slideLocked) {
      slideLocked = true; sfx("swishlock"); startScoreCount();
    }
    if (glue != null && glue.slide && !glue.exiting && !slideLocked && glue.elapsed >= NativeGlue.ENTER_MS) {
      slideLocked = true;
      sfx("swishlock");
    }
    if (page.equals("briefing") && campaign != null && modal.isEmpty() && pendingScreen == null
        && (glue == null || glue.elapsed >= NativeGlue.ENTER_MS)) campaign.tick(dt);
    if (page.equals("story")) {
      storyTime += dt;
      if (storyIndex < story.size() && storyTime > story.get(storyIndex).duration + 800)
        nextStory();
    }
    if(nearby!=null&&nearby.playing&&!page.equals("game"))nearby.tick(dt);
    if (activeGame && page.equals("game")) {
      boolean paused = !modal.isEmpty() || replayGame && replayPaused;
      NativeBridge.command(6, paused ? 1 : 0, 0, 0, 0);
      int steps = 0;
      if (nearby!=null) {nearby.tick(dt);} else if (!paused) {
        accumulator += dt;
        double interval=replayGame?42.0/replayRate:speed;
        while (accumulator >= interval && steps < 32) {
          accumulator -= interval;
          steps++;
        }
      } else accumulator = 0;
      advanceCamera(dt);
      try {NativeBridge.frame(world, steps, clock);}
      catch(RuntimeException playbackFailure){if(!replayGame)throw playbackFailure;showReplays();replayMessage("Replay could not be played: "+playbackFailure.getMessage());return;}
      if (campaign != null && !paused) campaign.poll();
      String[] error = NativeBridge.text(8).split("\t", 2);
      if (error.length > 1) {
        int n = Integer.parseInt(error[0]);
        if (n != errorSeq) {
          errorSeq = n;
          commandError = error[1];
          commandErrorUntil = clock + 3500;
          hint = ""; hintUntil = 0;
        }
      }
      if(replayGame){boolean seeking=NativeBridge.value(28,0)!=0;if(replaySeeking&&!seeking)accumulator=0;replaySeeking=seeking;
        if(!seeking&&NativeBridge.value(8,0)>=NativeBridge.value(26,0))replayPaused=true;}
      int outcome = replayGame?0:NativeBridge.value(7, 0);
      if (outcome != 0 && lastOutcome == 0) {
        lastOutcome = outcome;
        recordCampaignCompletion(outcome);
        if(nearby!=null)nearby.finish();
        modal = "outcome";
        NativeBridge.sound(null, 63, false);
        musicPhase = "outcome";
        sfx(outcome == 1 ? "youwin" : "youlose");
        cancelGesture();
      }
      advanceMusic();
    }
  }

  private void drawCommandFeedback(Canvas c) {
    if (!modal.isEmpty()) return;
    NativeHud.Hit hover = commandHover.tooltip(hud.hits, android.os.SystemClock.uptimeMillis());
    int targeting = NativeBridge.value(0, 0);
    String status = !commandError.isEmpty() && clock < commandErrorUntil ? commandError
        : targeting == 1 || targeting == 4 ? "Select target" : "";
    float tooltipBottom = Float.POSITIVE_INFINITY;
    if (!status.isEmpty()) {
      hud.commandError(c, status, logicalWidth);
      // Keep both messages visible: the status keeps its normal position and
      // inspection moves above it instead of replacing or covering it.
      tooltipBottom = hud.commandErrorTop(status, logicalWidth) - 8;
    }
    if (hover != null) {
      hud.tooltip(c, hud.hint(hover), logicalWidth,
          hover.fields == null ? hud.race : NativeHud.number(hover.fields, 15, hud.race), tooltipBottom);
    } else if (!hint.isEmpty() && clock < hintUntil) {
      hud.tooltip(c, hint, logicalWidth, hintSupplyRace < 0 ? hud.race : hintSupplyRace, tooltipBottom);
    }
  }

  private void renderCanvas(Canvas c, double dt) {
    c.drawColor(Color.BLACK);
    float scale = Math.min(c.getWidth() / (float)logicalWidth, c.getHeight() / 480f);
    int save = c.save();
    c.translate((c.getWidth()-logicalWidth*scale)/2, (c.getHeight()-480*scale)/2);
    c.scale(scale,scale);
    c.clipRect(0,0,logicalWidth,480);
    buttons.clear();
    menuCaptions.clear();
    menuBehindModal=!modal.isEmpty();
    try {draw(c,dt);flushMenuButtons();drawGamepadCursor(c);if(desktopMouseActive&&desktopInside)drawPointer(c,desktopX,desktopY,desktopPrimary.equals("world")||gamepadWorld(desktopX,desktopY));} finally {c.restoreToCount(save);}
  }

  private void draw(Canvas c, double dt) {
    presentReplayAcknowledgement();
    menuBehindModal=!modal.isEmpty();
    placementButtonRow = null;
    if (page.equals("results") && results != null) {
      results.canSave = NativeBridge.value(15, 0) != 0;
      results.hot = hoverButton;
      results.draw(c, !modal.isEmpty() ? 0 : dt);
      if (modal.startsWith("gamepad")||modal.equals("control-desktop")) {buttons.clear();menuBehindModal=false; drawGamepadSettings(c); return;}
      if(modal.startsWith("replay-")){buttons.clear();menuBehindModal=false;drawReplayDialog(c);return;}
      if (!results.ready() || pendingScreen != null) return;
      NativeArt.Widget save = results.layout.get(61);
      if (save != null)
        addMenuButton(
            save.hit(),
            this::saveCurrentReplay,
            results.canSave,results.buttonCaption(61));
      for (int i = 0; i < 4; i++) {
        final int tab = i;
        NativeArt.Widget w = results.layout.get(i + 3);
        if (w != null)
          addMenuButton(
              w.hit(),
              () -> {
                results.tab = tab;
                results.progress = 0;
                startScoreCount();
              },
              true,results.buttonCaption(i+3));
      }
      NativeArt.Widget ok = results.layout.get(7);
      if (ok != null) addMenuButton(ok.hit(), () -> navigate(this::nextMission), true,results.buttonCaption(7));
      return;
    }
    if (page.equals("game")) {
      c.save();
      c.clipRect(0, 0, logicalWidth, NativeHud.WORLD_BOTTOM);
      c.drawBitmap(world, null, new RectF(0, 0, logicalWidth, 480), NativeArt.PIXELS);
      c.restore();
      hud.transmissionType =
          campaign != null && campaign.talking >= 0 ? campaign.portraits[campaign.talking] : -1;
      hud.transmissionTalk = NativeBridge.playing(62);
      hud.paused = !modal.isEmpty();
      hud.pressedCommand = commandHover.pressed != null ? commandHover.pressed.rect
          : desktopHudHit != null ? desktopHudHit.rect : null;
      hud.pressedButton = pressed == null ? null : pressed.rect;
      hud.resourceScale = resourcePercent / 100f;
      hud.commandScale = commandPercent / 100f;
      hud.descriptionScale = descriptionPercent / 100f;
      hud.gridHotkeys = desktopSettings.gridHotkeys;
      hud.draw(c, world, logicalWidth, zoom, clock);
      if (modal.isEmpty()) {if(replayGame){drawReplayControls(c);flushMenuButtons();}else{drawSelectionButton(c);drawCommandModeButton(c);drawControlGroups(c);}}
      add(new RectF(logicalWidth - 230, 387, logicalWidth - 164, 408), this::openMenu, true);
      if (campaign != null) {
        if (!campaign.message.isEmpty()) {
          float scale = descriptionPercent / 100f;
          int textWidth = Math.round(624 / scale);
          float height = hud.small.lines(campaign.message, textWidth).size() * 16 * scale;
          c.save(); c.clipRect(8 + (logicalWidth - 640) / 2f, 120, 632 + (logicalWidth - 640) / 2f, 290);
          c.translate((logicalWidth - textWidth * scale) / 2f, Math.max(120, 290 - height));
          c.scale(scale, scale);
          hud.small.draw(c, campaign.message, 0, 0, textWidth, 0, 2, 16);
          c.restore();
        }
        String timer = NativeBridge.text(7);
        if (!timer.equals("0:00") || missionCode == 3)
          hud.large.draw(c, timer, (logicalWidth - 128) / 2f,
              controls.controlGroups ? Math.round(6+(controlGroupRows()*32-2)*controlGroupScale()) : resourcePercent > 125 ? 36 : 3, 128, 1, 2, 0);
      }
      if (placement && NativeBridge.value(0, 0) != 2) placement = false;
      drawCommandFeedback(c);
    } else if (page.equals("movie")) {
      cinematic.draw(
          c,
          0,
          (480 - cinematic.bitmap.getHeight()) / 2f,
          640,
          cinematic.bitmap.getHeight(),
          clock - cinematicStart,
          false,
          NativeArt.PIXELS);
      add(new RectF(0, 0, 640, 480), this::finishCinematic, true);
    } else if (page.equals("title")) {
      c.drawBitmap(titleArtwork, null, new RectF(0, 0, 640, 480), NativeArt.PIXELS);
      if (glue != null) glue.small.draw(c, "Tap to continue", 160, 446, 320, 1, 2, 0);
      add(new RectF(0, 0, 640, 480), this::showMain, true);
    } else if (page.equals("story")) {
      drawStory(c);
    } else {
      if (glue != null) glue.draw(c, dt);
      else {
        paint.setColor(0xff081329);
        c.drawRect(0, 0, 640, 480, paint);
        fallback(c, "StarDroid", 220, 22, 28, 0xffc5cce3);
        fallback(c, "Menu artwork could not load. Use App Options to re-import.", 30, 70, 14, 0xffe1cf47);
      }
      if (page.equals("main")) {
        glueButton(c, 3, null, () -> modal = "choose");
        glueButton(c, 5, "\u0004A\u0001pp Options", () -> activity.runOnUiThread(activity::requestAppOptions));
        glueButton(c, 2, null, () -> activity.runOnUiThread(activity::requestExit));
        glueButton(c, 9, null, () -> activity.runOnUiThread(activity::requestDiagnostics));
        glueButton(c, 4, null, () -> {registryForMultiplayer=true;navigate(this::showRegistry);});
        glueButton(c, 8, null, this::playIntroduction);
      }
      if (page.equals("registry")) drawRegistry(c);
      if (page.equals("custom")) drawCustom(c);
      if (page.equals("replays")) drawReplayBrowser(c);
      if (page.equals("nearby")) drawNearby(c);
      if (page.equals("campaign")) {
        glueButton(c, 6, null, () -> missionList(2));
        glueButton(c, 7, null, () -> missionList(1));
        glueButton(c, 8, null, () -> missionList(0));
        glueButton(c, 9, null, () -> navigate(this::showRegistry));
        glueButton(c, 5, null, () -> openSaves(false));
        glueButton(c, 11, null, () -> navigate(this::showReplays));
        glueButton(c, 10, null, () -> navigate(()->{multiplayerSetup=false;multiplayerSaves=false;showCustom();}));

      }
      if (page.equals("briefing")) drawBriefing(c);
      if (glue == null && (page.equals("main") || page.equals("campaign"))) {
        button(c, "App Options", 220, 438, 200, 30,
            () -> activity.runOnUiThread(activity::requestAppOptions), true);
      }
    }
    if(page.equals("game") && nearby!=null && !nearby.status.equals("Connected") && lastOutcome==0)
      hud.small.draw(c,nearby.status,logicalWidth/2f-180,52,360,1,2,0);
    if(page.equals("game") && nearby!=null && networkStats && lastOutcome==0) {
      float networkY=controls.controlGroups?Math.max(70,6+(controlGroupRows()*32-2)*controlGroupScale()):70;
      paint.setColor(0xb0000000);c.drawRect(12,networkY,logicalWidth-12,networkY+36,paint);
      hud.small.draw(c,nearby.summary(),20,networkY+3,logicalWidth-40,0,2,0);
      hud.small.draw(c,String.format(Locale.ROOT,"Display %.0f FPS | Draw %.1f ms/frame",displayFps,drawMs),20,networkY+19,logicalWidth-40,0,2,0);
    }
    if (page.equals("game") && placement && modal.isEmpty()) drawPlacementButtons(c);
    if (!modal.isEmpty()) {
      flushMenuButtons();
      buttons.clear();
      menuBehindModal=false;
      drawModal(c);
    }
  }

  private void fallback(Canvas c, String s, float x, float y, int size, int color) {
    paint.setColor(color);
    paint.setTextSize(size);
    paint.setTypeface(Typeface.SANS_SERIF);
    for (String l : NativeArt.clean(PortText.text(s)).split("\n")) {
      c.drawText(l, x, y + size, paint);
      y += size + 5;
    }
  }

  private void fallbackMenu(Canvas c,String label,float x,float y,int width,int height,int color){
    paint.setTextSize(16);paint.setTypeface(Typeface.SANS_SERIF);
    String shown=PortText.text(label),plain=NativeArt.clean(shown).replace('\n',' ');
    float measured=paint.measureText(plain),scale=Math.min(1,width/Math.max(1,measured));
    int normal=color==3?0xffe1cf47:color==5?0xff73798b:0xffc5cce3;
    c.save();c.clipRect(x,y,x+width,y+height);c.translate(x+(width-measured*scale)/2,y+(height-18)/2f);c.scale(scale,1);
    float left=0;int tint=normal;
    for(int i=0;i<shown.length();i++){
      char ch=shown.charAt(i);if(ch<32){if(ch==4)tint=color==3?0xfff0f4ff:0xffffec73;else if(ch==1)tint=normal;continue;}
      String glyph=String.valueOf(ch);paint.setColor(tint);c.drawText(glyph,left,16,paint);left+=paint.measureText(glyph);
    }
    c.restore();
  }

  private NativeArt.Font font() {
    return page.equals("game") && hud != null ? hud.large : glue != null ? glue.large : null;
  }

  private void text(Canvas c, String s, float x, float y, int width, int align, int color) {
    NativeArt.Font f = font();
    if (f != null) f.draw(c, s, x, y, width, align, color, 0);
    else fallback(c, s, x, y, 16, color == 3 ? 0xffe1cf47 : 0xffc5cce3);
  }

  private void panel(Canvas c, float x, float y, float w, float h) {
    if (!page.equals("game") && glue != null) {
      c.drawBitmap(glue.panel(Math.round(w), Math.round(h), false), x, y, NativeArt.PIXELS);
      return;
    }
    if (page.equals("game") && hud != null) {
      c.drawBitmap(hud.panel(Math.round(w), Math.round(h), false, true), x, y, NativeArt.PIXELS);
      return;
    }
    paint.setColor(0xf309173b);
    c.drawRect(x, y, x + w, y + h, paint);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(1);
    paint.setColor(0xff4059ad);
    c.drawRect(x + .5f, y + .5f, x + w - .5f, y + h - .5f, paint);
    paint.setColor(0xff172b6f);
    c.drawRect(x + 3.5f, y + 3.5f, x + w - 3.5f, y + h - 3.5f, paint);
    paint.setStyle(Paint.Style.FILL);
  }

  private void button(
      Canvas c,
      String label,
      float x,
      float y,
      float w,
      float h,
      Runnable action,
      boolean enabled) {
    button(c,label,x,y,w,h,action,enabled,true);
  }

  private void button(Canvas c,String label,float x,float y,float w,float h,
      Runnable action,boolean enabled,boolean navigation) {
    if (page.equals("game") && hud != null)
      c.drawBitmap(hud.panel(Math.round(w), Math.round(h), true, enabled), x, y, NativeArt.PIXELS);
    else if (glue != null) c.drawBitmap(glue.panel(Math.round(w), Math.round(h), true), x, y, NativeArt.PIXELS);
    else panel(c, x, y, w, h);
    NativeArt.Font f = font();
    queueMenuButton(new RectF(x,y,x+w,y+h),action,enabled,label,navigation,
        shown->{if(f!=null)uiLine(c,shown,f,x+3,y,(int)w-6,(int)h,1,enabled?3:5);
          else fallbackMenu(c,shown,x+3,y,(int)w-6,(int)h,enabled?3:5);});
  }

  private void add(RectF rect, Runnable run, boolean enabled) {
    buttons.add(new Button(rect, run, enabled));
  }

  private void addMenuButton(RectF rect, Runnable run, boolean enabled, String shown) {
    Button button=new Button(rect,run,enabled&&run!=null);
    button.key=button.enabled?MenuLabels.mnemonic(shown):0;
    button.page=page;button.modal=modal;
    buttons.add(button);
  }

  private void queueMenuButton(RectF rect,Runnable run,boolean enabled,String label,
      java.util.function.Consumer<String> drawCaption) {
    queueMenuButton(rect,run,enabled,label,true,drawCaption);
  }

  private void queueMenuButton(RectF rect,Runnable run,boolean enabled,String label,
      boolean navigation,java.util.function.Consumer<String> drawCaption) {
    queueMenuButtonVisual(rect,run,enabled,enabled,label,navigation,drawCaption);
  }

  private void queueMenuButtonVisual(RectF rect,Runnable run,boolean enabled,boolean highlight,String label,
      java.util.function.Consumer<String> drawCaption) {
    queueMenuButtonVisual(rect,run,enabled,highlight,label,true,drawCaption);
  }

  private void queueMenuButtonVisual(RectF rect,Runnable run,boolean enabled,boolean highlight,String label,
      boolean navigation,java.util.function.Consumer<String> drawCaption) {
    if(run==null){drawCaption.accept(NativeArt.clean(PortText.text(label)));return;}
    if(page.equals("game")&&modal.isEmpty()&&!replayGame){
      drawCaption.accept(NativeArt.clean(PortText.text(label)));
      add(rect,run,enabled);return;
    }
    Button button=new Button(rect,run,enabled);button.page=page;button.modal=modal;
    buttons.add(button);
    String id=Math.round(rect.left)+":"+Math.round(rect.top)+":"+Math.round(rect.width())+":"+Math.round(rect.height());
    menuCaptions.add(new MenuCaption(button,new MenuHotkeys.Entry(id,label),highlight,navigation,drawCaption));
  }

  private void flushMenuButtons() {
    if(menuCaptions.isEmpty())return;
    ArrayList<MenuHotkeys.Entry> entries=new ArrayList<>();
    // Only navigation participates in mnemonic allocation. In-place settings and
    // actions keep normal text and must not reserve or respond to letter keys.
    for(MenuCaption caption:menuCaptions)if(caption.navigation&&buttons.contains(caption.button))entries.add(caption.entry);
    (menuBehindModal?backgroundMenuHotkeys:menuHotkeys).assign(page+"|"+(menuBehindModal?"":modal),entries);
    for(MenuCaption caption:menuCaptions)if(buttons.contains(caption.button)){
      caption.button.key=caption.navigation&&caption.button.enabled?caption.entry.key:0;
      String shown=caption.navigation?caption.entry.shown:MenuHotkeys.decorate(caption.entry.label,0);
      caption.draw.accept(caption.highlight?shown:NativeArt.clean(shown));
    }
    menuCaptions.clear();
  }

  private void glueButton(Canvas c, int id, String label, Runnable run) {
    glueButton(c,id,label,run,true);
  }

  private void glueButton(Canvas c, int id, String label, Runnable run, boolean navigation) {
    if (glue == null) {
      int n = buttons.size();
      button(c, label == null ? MenuLabels.forButton(page, id) : label,
          205, 120 + n * 42, 230, 32, run, true,navigation);
      return;
    }
    NativeArt.Widget w = glue.layout.get(id);
    if (w == null || !glue.labelVisible(id)) return;
    boolean enabled=run!=null&&pendingScreen==null&&(glue.elapsed>=NativeGlue.ENTER_MS||!glue.slide);
    RectF r = w.hit();
    r.offset(glue.shift(w), 0);
    queueMenuButtonVisual(r,run,enabled,run!=null,MenuLabels.forWidget(glue.name,id,w,label),navigation,
        shown->glue.labelCaption(c,id,shown,run!=null));
  }

  private void message(String text) {
    hint = text;
    modal = "message";
  }

  private void missionList(int race) {
    listRace = race;
    listScroll = 0;
    missionOffset = 0;
    listVelocity = 0;
    visibleMissions.clear();
    for (int i = 0; i < missions.length(); i++) {
      JSONObject m = missions.optJSONObject(i);
      if (m.optString("game").equals(expansion ? "broodwar" : "starcraft")
          && m.optInt("campaignRace", m.optInt("race")) == race
          && !m.optBoolean("hidden")) {
        visibleMissions.add(m);
        CinematicCatalog.Entry film = CinematicCatalog.after(m.optInt("code"));
        if (film != null) visibleMissions.add(CinematicCatalog.row(film));
      }
    }
    chosenMission = visibleMissions.isEmpty() ? -1 : 0;
    modal = "missions";
  }

  private boolean registryForMultiplayer;
  private float menuHoverX=-1,menuHoverY=-1;

  private void showRegistry() {
    profileOffset = Math.min(profileOffset, Math.max(0, profiles.size() - 9) * 23);
    listVelocity = 0;
    screen("registry", "glulogin", "palnl", true);
    music("title");
    sfx("swishin");
  }

  private void saveProfiles() {
    campaignProgress.synchronize(profiles);
    prefs.edit().putString("profiles", new JSONArray(profiles).toString())
        .putString("profile", selectedProfile)
        .putString(CampaignProgress.KEY, campaignProgress.serialize()).apply();
  }

  private void saveCampaignProgress() {
    prefs.edit().putString(CampaignProgress.KEY, campaignProgress.serialize()).apply();
  }

  private void recordCampaignCompletion(int outcome) {
    String id=currentMission==null||currentMission.has("cinematic")?"":currentMission.optString("id");
    if(campaignProgress.record(campaignProfile,id,outcome,campaign!=null,replayGame,multiplayerGame))
      saveCampaignProgress();
  }

  private boolean missionCompleted(JSONObject mission) {
    return !mission.has("cinematic")&&campaignProgress.completed(selectedProfile,mission.optString("id"));
  }

  private void drawMissionCheck(Canvas c,float x,float y) {
    // A plain bitmap-style tick matches the menu fonts without introducing
    // an antialiased circle or changing completion's per-mission-ID state.
    Paint pixel=new Paint();pixel.setColor(0xff7cdb58);
    int left=Math.round(x),top=Math.round(y);
    for(int column=-5;column<=4;column++) {
      int row=column<-2?column+4:-column;
      c.drawRect(left+column,top+row,left+column+1,top+row+3,pixel);
    }
  }

  private void newProfile() {
    profileDraft="";modal="profile-new";profileEditing=true;
    activity.runOnUiThread(()->{profileInput.clear();showTextKeyboard(profileInput);});
  }
  private void deleteProfile() {
    if(selectedProfile.isEmpty())return;
    profileDelete=selectedProfile;modal="profile-delete";
  }
  private void cancelProfileDialog(){profileEditing=false;closeChat();modal="";}
  private void confirmProfileDialog(){
    if(modal.equals("profile-new")) {
      String name=NativeArt.clean(profileDraft).replace('\n',' ').trim();if(name.isEmpty())return;
      String existing=null;for(String value:profiles)if(value.equalsIgnoreCase(name))existing=value;
      if(existing==null){if(profiles.size()>=32)return;profiles.add(name);}
      selectedProfile=existing==null?name:existing;
    }else if(modal.equals("profile-delete")) {
      profiles.remove(profileDelete);selectedProfile=profiles.isEmpty()?"":profiles.get(0);
      profileOffset=Math.min(profileOffset,Math.max(0,profiles.size()-9)*23);
    }else return;
    saveProfiles();cancelProfileDialog();
  }
  private void drawProfileDialog(Canvas c){
    paint.setColor(0x60000000);c.drawRect(0,0,logicalWidth,480,paint);
    float x=(logicalWidth-380)/2f,y=140;
    paint.setColor(0xdb080b10);c.drawRoundRect(new RectF(x,y,x+380,y+192),6,6,paint);redBorder(c,x,y,380,192,false);
    boolean create=modal.equals("profile-new");
    if (glue == null) {
      text(c, create ? "New Player ID" : "Delete Player ID", x+18, y+16, 344, 1, 2);
      body(c, create ? profileDraft + (profileEditing ? "|" : "") : "Delete " + profileDelete + "?",
          x+24, y+65, 332, 54);
      if (create) add(new RectF(x+24,y+65,x+356,y+119),
          () -> {profileEditing=true;activity.runOnUiThread(()->showTextKeyboard(profileInput));}, true);
      button(c, create ? "OK" : "Delete", x+24, y+142, 154, 30, this::confirmProfileDialog,
          !create || !profileDraft.trim().isEmpty() && (profiles.size()<32 || profiles.contains(profileDraft.trim())));
      button(c, "Cancel", x+202, y+142, 154, 30, this::cancelProfileDialog, true);
      return;
    }
    uiLine(c,create?"New Player ID":"Delete Player ID",glue.large,x+18,y+16,344,30,1,2);
    if(create){
      redBorder(c,x+24,y+65,332,32,profileEditing);
      String draft=profileDraft+(profileEditing?"|":"");while(glue.dialogue.measure(draft)>316&&draft.length()>1)draft=draft.substring(1);
      uiLine(c,draft,glue.dialogue,x+32,y+65,316,32,0,3);
      add(new RectF(x+24,y+65,x+356,y+97),()->{profileEditing=true;activity.runOnUiThread(()->showTextKeyboard(profileInput));},true);
      uiLine(c,profiles.size()>=32?"32 IDs saved. Delete one to add another.":"Enter a name (up to 24 characters).",glue.small,x+24,y+102,332,22,0,2);
    }else body(c,"Delete "+profileDelete+"?",x+24,y+65,332,54);
    redNavigationButton(c,create?"OK":"Delete",x+24,y+142,154,30,this::confirmProfileDialog,!create||!profileDraft.trim().isEmpty()&&(profiles.size()<32||profiles.contains(profileDraft.trim())));
    redNavigationButton(c,"Cancel",x+202,y+142,154,30,this::cancelProfileDialog,true);
  }

  private void drawRegistry(Canvas c) {
    if (glue == null) {
      int first = Math.min(profileScroll, Math.max(0, profiles.size()-6));
      for (int i=first; i<Math.min(profiles.size(), first+6); i++) {
        final String name = profiles.get(i);
        float rowY=112+(i-first)*34;panel(c,120,rowY,400,28);
        fallbackMenu(c,NativeArt.clean((name.equals(selectedProfile)?"> ":"")+name),123,rowY,394,28,3);
        add(new RectF(120,rowY,520,rowY+28),()->{selectedProfile=name;saveProfiles();},true);
      }
      button(c, "Up", 120, 320, 94, 28, () -> profileScroll=Math.max(0,first-6), first>0,false);
      button(c, "Down", 226, 320, 94, 28, () -> profileScroll=first+6, first+6<profiles.size(),false);
      button(c, "New ID", 120, 360, 190, 32, this::newProfile, profiles.size()<32);
      button(c, "Delete ID", 330, 360, 190, 32, this::deleteProfile, profiles.contains(selectedProfile));
      button(c, "OK", 120, 402, 190, 32, () -> {
        if(registryForMultiplayer) modal="multiplayer-choose"; else navigate(this::showCampaign);
      }, profiles.contains(selectedProfile));
      button(c, "Back", 330, 402, 190, 32, () -> navigate(this::showMain), true);
      return;
    }
    glue.label(c, 9, null, true);
    NativeArt.Widget box = glue.layout.get(8);
    float offset = glue.shift(box);
    profileViewport.set(box.x + offset, box.y, box.x + offset + box.w, box.y + box.h);
    profileOffset = Math.min(profileOffset, Math.max(0, profiles.size() - 9) * 23);
    profileScroll = (int)(profileOffset / 23);
    c.save(); c.clipRect(box.x + offset, box.y, box.x + offset + box.w, box.y + box.h);
    for (int i = profileScroll; i < Math.min(profiles.size(), profileScroll + 10); i++) {
      final String name = profiles.get(i);
      float y = box.y + i * 23 - profileOffset;
      glue.large.draw(c, name, box.x + offset + 4, y + 2, box.w - 8, 0,
          name.equals(selectedProfile) ? 4 : 2, 0);
      RectF row = new RectF(box.x + offset, y, box.x + offset + box.w, y + 23);
      if (row.intersect(profileViewport)) add(row,
          () -> {selectedProfile = name; saveProfiles();}, pendingScreen == null && glue.elapsed >= NativeGlue.ENTER_MS);
    }
    c.restore();
    glueButton(c, 4, null, () -> {if (profiles.contains(selectedProfile)) {if(registryForMultiplayer)modal="multiplayer-choose";else navigate(this::showCampaign);}});
    glueButton(c, 5, null, () -> navigate(this::showMain));
    glueButton(c, 6, null, this::newProfile);
    glueButton(c, 7, null, this::deleteProfile);
  }

  private float missionMaxOffset() {return Math.max(0, visibleMissions.size() * 19 - 162);}
  private String historyLabel(JSONObject m) {
    return (m.has("cinematic") ? "Cinematic: " : m.optInt("code") == 50 ? "Tutorial: " : m.optInt("number") == 0 ? "" : m.optInt("number") + ". ") + m.optString("title");
  }

  private boolean drawHistory(Canvas c) {
    if (glue == null) return false;
    try {
      NativeArt.PCX background = art.pcx("glue/campaign/phist.pcx", true, 138);
      float x = 130, y = 114;
      c.drawBitmap(background.image, x, y, NativeArt.PIXELS);
      NativeArt.Dialog history = dialog("gluhist");
      NativeArt.Widget heading = history.get(65535), box = history.get(6);
      // Reserve a wider independent rail so labels never sit under its targets.
      missionViewport.set(x + box.x, y + box.y, x + box.x + box.w - 36, y + box.y + box.h);
      missionOffset = Math.min(missionOffset, missionMaxOffset());
      listScroll = (int)(missionOffset / 19);
      glue.small.draw(c, heading.text, x + heading.x, y + heading.y, heading.w, 1, 2, 0);
      c.save(); c.clipRect(missionViewport);
      for (int i = listScroll; i < Math.min(visibleMissions.size(), listScroll + 9); i++) {
        final int index = i;
        JSONObject m = visibleMissions.get(i);
        String label = historyLabel(m);
        float row = y + box.y + i * 19 - missionOffset;
        if(missionCompleted(m))drawMissionCheck(c,x+box.x+10,row+9.5f);
        uiLine(c,label,glue.large,x+box.x+22,row,(int)missionViewport.width()-26,19,0,
            !installed(m) ? 5 : i == chosenMission ? 4 : 3);
        RectF hit = new RectF(missionViewport.left, row, missionViewport.right, row + 19);
        if (hit.intersect(missionViewport)) add(hit, () -> chosenMission = index, installed(m));
      }
      c.restore();
      // Original arrows and circular grip, widened together with their targets.
      if (visibleMissions.size() > 8) {
        drawMissionScrollbar(c,new RectF(missionViewport.right,missionViewport.top,x+box.x+box.w,missionViewport.bottom));
      }
      NativeArt.Widget ok = history.get(1), cancel = history.get(3);
      // phist.pcx contains the old narrow button outlines. Clear that interior
      // strip before drawing the wider controls so neither outline remains.
      paint.setColor(Color.BLACK);c.drawRect(x+12,y+202,x+368,y+246,paint);
      button(c, "OK", x + 20, y + ok.y, 164, 34, () -> {
        if (chosenMission >= 0) {JSONObject m = visibleMissions.get(chosenMission); navigate(() -> chooseMission(m));}
      }, chosenMission >= 0 && installed(visibleMissions.get(chosenMission)));
      button(c, "Cancel", x + 196, y + cancel.y, 164, 34, () -> modal = "", true);
      return true;
    } catch (Exception e) {return false;}
  }

  private void drawMissionScrollbar(Canvas c,RectF rail) {
    int originalHeight=Math.round(rail.height()/1.75f);
    float scale=rail.height()/originalHeight;
    c.save();c.translate(rail.left+(rail.width()-16*scale)/2,rail.top);c.scale(scale,scale);
    glue.scrollbar(c,0,0,originalHeight,missionMaxOffset()==0?0:missionOffset/missionMaxOffset());c.restore();
    add(new RectF(rail.left,rail.top,rail.right,rail.top+32),
        ()->{missionOffset=Math.max(0,missionOffset-19);listVelocity=0;},missionOffset>0);
    add(new RectF(rail.left,rail.bottom-32,rail.right,rail.bottom),
        ()->{missionOffset=Math.min(missionMaxOffset(),missionOffset+19);listVelocity=0;},missionOffset<missionMaxOffset());
    Button bar=new Button(new RectF(rail.left,rail.top+32,rail.right,rail.bottom-32),()->{},missionMaxOffset()>0);
    bar.vertical=true;
    final float inset=23*scale-32, travel=(originalHeight-46)*scale;
    final long[] dragStarted={Long.MIN_VALUE};final float[] grip={0};
    bar.slider=v->{
      float maximum=missionMaxOffset(),position=v*bar.rect.height()-inset;
      if(dragStarted[0]!=downAt) {
        dragStarted[0]=downAt;float thumb=maximum>0?missionOffset/maximum*travel:0;
        grip[0]=Math.abs(position-thumb)<=9*scale?position-thumb:0;
      }
      missionOffset=Math.max(0,Math.min(maximum,(position-grip[0])/travel*maximum));listVelocity=0;
    };
    buttons.add(bar);
  }

  private void inspectMap(File map) throws Exception {
    if (map.length() > 64 * 1024 * 1024) throw new IOException("Map is too large.");
    NativeBridge.open(3, map.getPath());
    byte[] chk = NativeBridge.asset(3, "staredit/scenario.chk");
    if (chk == null) throw new IOException("No scenario in this map.");
    Map<String, byte[]> chunks = new HashMap<String, byte[]>();
    for (int p = 0; p + 8 <= chk.length;) {
      int n = NativeArt.u32(chk, p + 4);
      if (n < 0 || n > chk.length - p - 8) throw new IOException("Truncated map.");
      chunks.put(new String(chk, p, 4, NativeArt.CP), Arrays.copyOfRange(chk, p + 8, p + 8 + n));
      p += n + 8;
    }
    byte[] dim = chunks.get("DIM "), units = chunks.get("UNIT"), prop = chunks.get("SPRP"), era = chunks.get("ERA ");
    if (dim == null || dim.length != 4) throw new IOException("Missing map dimensions.");
    int w = NativeArt.u16(dim, 0), h = NativeArt.u16(dim, 2), starts = 0;
    if (w < 1 || h < 1 || w > 256 || h > 256) throw new IOException("Unsupported map size.");
    if (units != null) for (int p = 0; p + 36 <= units.length; p += 36)
      if (NativeArt.u16(units, p + 8) == 214) starts++;
    if (starts < 1 || starts > 8) throw new IOException("This map needs 1–8 start locations for Melee.");
    String[] strings = NativeArt.tbl(chunks.get("STR "));
    int title = prop != null && prop.length >= 4 ? NativeArt.u16(prop, 0) - 1 : -1,
        desc = prop != null && prop.length >= 4 ? NativeArt.u16(prop, 2) - 1 : -1;
    customName = title >= 0 && title < strings.length ? NativeArt.clean(strings[title]) : map.getName();
    customDescription = desc >= 0 && desc < strings.length ? NativeArt.clean(strings[desc]) : "";
    customWidth = w; customHeight = h; customStarts = starts;
    customSetup.limit(starts);nearbySetup.limit(starts);
    if(mapThumbnail!=null)mapThumbnail.recycle();mapThumbnail=null;mapPreviewError="";
    try {mapThumbnail=mapPreviews.create(chunks,w,h);}catch(Exception e){mapPreviewError="Preview unavailable";}

    customTileset = new String[] {"Badlands", "Space Platform", "Installation", "Ashworld", "Jungle", "Desert", "Ice", "Twilight"}[era == null ? 0 : era[0] & 7];
  }

  private void closeNearby() {
    closeChat();movingSlot=-1;
    if(nearby!=null)lastNetworkDiagnostics=nearby.diagnostics()+"\n"+(nearbyRadio==null?"":nearbyRadio.diagnostics());
    if(nearby!=null)nearby.leave();
    if(nearbyRadio!=null)nearbyRadio.close();
    nearbyGeneration++;
    nearby=null;nearbyRadio=null;nearbyGames.clear();nearbyPage=0;nearbyTransport="";
  }

  private void showNearby() {
    closeNearby();nearbyResume=null;nearbyResuming=false;multiplayerSetup=false;multiplayerSaves=true;
    NativeBridge.endGame();activeGame=false;multiplayerGame=false;campaign=null;currentMission=null;lastOutcome=0;
    nearbySetup=new MatchSetup(true);customMaps=NativeMaps.list(gameFiles);if(customRace<0)customRace=1;
    nearbyStage="connection";screen("nearby","gluconn","palnl",true);sfx("swishin");
    nearbyStatus="Choose a connection, then host or join a game.";
  }
  private void showNearbyLobby() {
    if(page.equals("nearby") && nearbyStage.equals("lobby") && glue!=null && !glue.exiting)return;
    nearbyStage="lobby";screen("nearby","gluchat","palnl",true);sfx("swishin");
  }
  private void hostCustom() {
    multiplayerSetup=true;multiplayerSaves=true;nearbyResume=null;nearbyResuming=false;showCustom();
  }
  private void joinNearby() {
    nearbyStage="join";selectedNearby="";screen("nearby","glujoin","palnl",true);sfx("swishin");openNearby(false);
  }
  private void createNearby() {
    if(customStarts<2){hint="Multiplayer needs at least two starting locations.";modal="map-error";return;}
    for(int i=1;i<8;i++)nearbySetup.controllers[i]=i<customStarts?1:0;
    nearbySetup.races[0]=customRace<0?new Random().nextInt(3):customRace;customRace=nearbySetup.races[0];
    showNearbyLobby();openNearby(true);
  }

  private File nearbySourceMap() throws Exception {
    if(customMap!=null)return customMap;
    File map=new File(cache,"weave.scx");cache.mkdirs();
    if(!map.exists())try(InputStream in=activity.getAssets().open("native/weave.scx");FileOutputStream out=new FileOutputStream(map)) {
      out.write(NativeData.read(in,NearbyProtocol.MAX_MAP));
    }
    return map;
  }

  private void openNearby(boolean hosting) {
    closeNearby();final int generation=nearbyGeneration;
    nearby=new RoomMatch(hosting,customRace,selectedProfile,new RoomMatch.Engine(){
      public byte[] snapshot(){return NativeBridge.multiplayerSave();}
      public void restore(byte[] state){NativeBridge.multiplayerRestore(state);}
      public void countdown(int seconds){if(seconds>0)sfx("countdown");}
      public void reloading(){activeGame=false;lastOutcome=0;modal="";movingSlot=-1;showNearbyLobby();}

      public long[] prepare(byte[] chk,MatchSetup settings,int slot,boolean bw) throws Exception {
        nearbySetup=settings;
        NativeBridge.endGame();NativeBridge.open(3,"");
        File map=NativeData.nearbyMap(chk,cache);inspectMap(map);
        if(customStarts<2)throw new IOException("This map needs at least two starting locations");
        multiplayerGame=true;multiplayerSaves=true;missionCode=0;missionRace=settings.races[slot];expansion=bw;campaign=null;currentMission=null;
        releaseSavedMap();activeMapSource=map;
        long[] info=NativeBridge.configuredInit(slot,nearbySetup.races,nearbySetup.controllers,nearbySetup.difficulty,bw,nearbySetup.freeForAll,true,nearbySetup.teams,nearbySetup.teamGame,nearbySetup.heroMode);
        speed=nearbySetup.frameMs();nearbyExpansion=bw;
        if(!nearbyStage.equals("lobby"))showNearbyLobby();
        activeGame=false;lastOutcome=0;instant=false;commandError="";errorSeq=-1;
        if(hud!=null)hud.close();hud=new NativeHud(art,missionRace,commandMaps);
        return info;
      }
      public byte[] collect(){return NativeBridge.multiplayerCollect();}
      public long hash(){return NativeBridge.multiplayerHash();}
      public void apply(byte[][] commands){NativeBridge.multiplayerApplyAll(commands);}
      public void step(){NativeBridge.multiplayerStep();}
      public void started(){closeChat();activeGame=true;startMission();}
      public void stopped(String reason,boolean surrender){
        if(generation!=nearbyGeneration)return;
        if(lastOutcome!=0 || activeGame && NativeBridge.value(7,0)!=0)return;
        nearbyStatus=reason;
        if(surrender && activeGame && page.equals("game")) {
          NativeBridge.multiplayerPeerLeft();nearby=null;modal="";hint="Opponent left the match";hintUntil=clock+4000;return;
        }
        modal="nearby-error";cancelGesture();
      }
    },new RoomMatch.Link(){
      public void send(int peer,byte[] bytes){if(generation==nearbyGeneration && nearbyRadio!=null)nearbyRadio.send(peer,bytes);}
      public void drop(int peer){if(generation==nearbyGeneration && nearbyRadio!=null)nearbyRadio.drop(peer);}
      public void close(){if(generation==nearbyGeneration && nearbyRadio!=null)nearbyRadio.close();}
    },new NearbyMapCache(new File(cache,"nearby-maps")),()->System.nanoTime()/1000000);
    final RoomMatch match=nearby;
    try {
      if(hosting) {
        File source=nearbySourceMap();inspectMap(source);
        if(customStarts<2)throw new IOException("This map needs at least two starting locations");
        match.hostSettings(nearbySetup,nearbyResume);nearbyResume=null;
        match.hostMap(NativeBridge.asset(3,"staredit/scenario.chk"),customName,nearbyExpansion);
      }
      nearbyRadio=new NativeNearby(activity,new NativeNearby.Listener(){
        public void status(String s){enqueue(()->{if(generation==nearbyGeneration){nearbyStatus=s;nearbyTransport=s;if(!match.connected)match.status=s;}});}
        public void found(String id,String name){enqueue(()->{if(generation==nearbyGeneration && (nearbyGames.size()<64 || nearbyGames.containsKey(id)))nearbyGames.put(id,name);});}
        public void lost(String id){enqueue(()->{if(generation==nearbyGeneration)nearbyGames.remove(id);});}
        public void connected(int peer,String name){enqueue(()->{if(generation==nearbyGeneration)match.connected(peer,name);});}
        public void received(int peer,byte[] data){enqueue(()->{if(generation==nearbyGeneration)match.receive(peer,data);});}
        public void disconnected(int peer){enqueue(()->{if(generation==nearbyGeneration)match.disconnected(peer);});}
        public void failed(String s){enqueue(()->{if(generation==nearbyGeneration)match.fail(s);});}
      });
      nearbyRadio.useWifi(nearbyWifi);nearbyRadio.start(hosting,selectedProfile);
    }catch(Exception e){match.fail(e.getMessage());}
  }

  private void drawNearby(Canvas c) {
    if (glue == null) { drawMenuUnavailable(c, this::showMain); return; }
    if(glue==null||glue.exiting||glue.elapsed<NativeGlue.ENTER_MS)return;
    if(nearbyStage.equals("connection")) {
      customLabel(c,65535,"Select Connection",glue.large,0);
      redButton(c,"Bluetooth + Wi-Fi Direct",60,102,264,34,()->nearbyWifi=true,true);
      redButton(c,"Bluetooth",60,146,264,34,()->nearbyWifi=false,true);
      uiLine(c,nearbyWifi?"Bluetooth + Wi-Fi Direct":"Bluetooth",glue.dialogue,380,90,224,36,1,4);
      body(c,nearbyWifi?"Find nearby devices over Bluetooth. Each connection switches to Wi-Fi Direct when available.":"Connect nearby devices directly over Bluetooth.",380,140,222,110);
      customLabel(c,14,"Multiplayer",glue.large,0);
      redNavigationButton(c,"Host Game",58,284,262,30,()->navigate(this::hostCustom),true);
      redNavigationButton(c,"Join Game",58,322,262,30,()->navigate(this::joinNearby),true);
      glueButton(c,9,"Load Saved",()->{multiplayerSaves=true;openSaves(false);});
      glueButton(c,10,null,()->navigate(this::showMain));return;
    }
    if(nearbyStage.equals("join")) {
      customLabel(c,65535,"Join Game",glue.large,0);
      int row=0,index=0;nearbyPage=Math.min(nearbyPage,Math.max(0,(nearbyGames.size()-1)/7));
      for(Map.Entry<String,String> entry:nearbyGames.entrySet()) {
        if(index++<nearbyPage*7)continue;if(row>=7)break;final String id=entry.getKey();
        float rowY=94+row++*30;redBorder(c,64,rowY,270,26,false);
        uiLine(c,entry.getValue(),glue.dialogue,68,rowY,262,26,1,menuColor(64,rowY,270,26,true));
        add(new RectF(64,rowY,334,rowY+26),()->selectedNearby=id,true);
      }
      body(c,nearby==null?nearbyStatus:nearby.status,410,95,190,108);
      uiLine(c,nearbyGames.getOrDefault(selectedNearby,"Select a host"),glue.dialogue,410,210,190,32,1,4);
      redButton(c,"Search Again",64,320,270,26,()->nearbyRadio.refresh(),nearbyRadio!=null);
      if(nearbyGames.size()>7){redButton(c,"Previous",64,350,130,24,()->nearbyPage--,nearbyPage>0);redButton(c,"Next",204,350,130,24,()->nearbyPage++,nearbyGames.size()>(nearbyPage+1)*7);}
      glueButton(c,13,"Join",()->{if(nearbyRadio!=null&&!selectedNearby.isEmpty())nearbyRadio.join(selectedNearby);});
      glueButton(c,14,null,()->navigate(this::showNearby));return;
    }
    MatchSetup setup=nearby==null?nearbySetup:nearby.setup;
    boolean host=nearby==null||nearby.host,editable=nearby!=null&&!nearby.closed&&nearby.countdown==0;
    for(int i=0;i<setup.starts;i++) {
      final int slot=i;float y=34+i*26;
      String player=setup.controllers[i]==0?"Closed":setup.controllers[i]==2?"Computer":nearby!=null&&nearby.occupants[i]>=0?nearby.names[i]:"Player (Open)";
      uiLine(c,Integer.toString(i+1),glue.dialogue,29,y,21,24,0,2);
      redBorder(c,52,y,setup.teamGame?141:184,24,movingSlot==i);
      queueMenuButton(new RectF(52,y,setup.teamGame?193:236,y+24),()->lobbySlot(slot),editable&&host,player,false,
          shown->uiLine(c,shown,glue.dialogue,57,y,setup.teamGame?131:174,24,0,editable&&host?menuColor(52,y,setup.teamGame?141:184,24,true):2));
      float raceX=setup.teamGame?201:244;int raceW=setup.teamGame?85:112;
      redBorder(c,raceX,y,raceW,24,false);
      boolean raceEnabled=editable&&!nearby.resuming&&setup.controllers[i]!=0&&(host||i==nearby.localSlot);
      queueMenuButton(new RectF(raceX,y,raceX+raceW,y+24),()->{try{nearby.changeRace(slot);}catch(Exception e){nearby.fail(e.getMessage());}},raceEnabled,
          setup.controllers[i]==0?"":MatchSetup.RACES[setup.races[i]],false,shown->uiLine(c,shown,glue.dialogue,raceX+5,y,raceW-10,24,0,menuColor(raceX,y,raceW,24,raceEnabled)));
      if(setup.teamGame)redButton(c,"T"+setup.teams[i],294,y,62,24,()->{try{nearby.setup.teams[slot]=nearby.setup.teams[slot]%8+1;nearby.settingsChanged();}catch(Exception e){nearby.fail(e.getMessage());}},editable&&host&&!nearby.resuming&&setup.controllers[i]!=0);
    }
    redButton(c,"Hero Mode: "+(setup.heroMode?"On":"Off"),410,57,190,20,()->changeLobbySetting(3),editable&&host&&!nearby.resuming);
    uiLine(c,customName,glue.dialogue,410,80,190,24,1,2);
    drawMapThumbnail(c,441,108,128,128);
    uiLine(c,customWidth+" x "+customHeight+" · "+(nearbyExpansion?"Brood War":"StarCraft"),glue.tiny,410,238,190,14,1,2);
    redButton(c,setup.mode(),410,255,190,20,()->changeLobbySetting(0),editable&&host&&!nearby.resuming);
    redButton(c,"Difficulty: "+new String[]{"Easy","Medium","Hard"}[setup.difficulty],410,279,190,20,()->changeLobbySetting(1),editable&&host&&!nearby.resuming);
    redButton(c,"Speed: "+MatchSetup.SPEEDS[setup.speed],410,303,190,20,()->changeLobbySetting(2),editable&&host&&!nearby.resuming);
    body(c,movingSlot>=0?"Tap a destination slot to move "+nearby.names[movingSlot]+".":host?(setup.teamGame?"Tap names to move players; tap T1-T8 to set teams.":"Tap a player to move them. Tap open slots or races to change them."):"Tap your race to change it.",30,250,328,42);
    body(c,nearby==null?nearbyStatus:nearby.countdown>0?"Starting in "+nearby.countdown+"…":nearby.status,30,290,328,34);
    ArrayList<String> lines=new ArrayList<String>();if(nearby!=null)for(String line:nearby.chat)lines.addAll(glue.small.lines(line,324));
    c.save();c.clipRect(30,332,358,434);int lineHeight=Math.max(13,glue.small.height+2),shown=100/lineHeight;
    for(int i=Math.max(0,lines.size()-shown),row=0;i<lines.size();i++,row++)glue.small.draw(c,lines.get(i),32,333+row*lineHeight,0,0,2,0);c.restore();
    redBorder(c,30,455,277,18,chatEditing);
    String draft=chatDraft.isEmpty()?chatEditing?"|":"Type a message…":chatDraft+(chatEditing?"|":"");
    while(glue.small.measure(draft)>265&&draft.length()>1)draft=draft.substring(1);
    uiLine(c,draft,glue.small,35,455,267,18,0,chatEditing?4:2);
    add(new RectF(30,455,307,473),this::openChat,nearby!=null&&!nearby.closed);
    redButton(c,"Send",313,455,45,18,this::sendChat,nearby!=null&&!nearby.closed&&!chatDraft.trim().isEmpty());
    if(host) {
      NativeArt.Widget w=glue.layout.get(7);boolean enabled=nearby!=null&&nearby.ready&&nearby.countdown==0&&!nearby.closed;
      if(w!=null)queueMenuButton(w.hit(),()->nearby.start(),enabled,MenuLabels.forWidget(glue.name,7,w,"Start Game"),caption->glue.labelCaption(c,7,caption,enabled));
    }else customLabel(c,7,"Waiting for host",glue.dialogue,1);
    glueButton(c,8,null,()->navigate(this::showNearby));
  }
  private void lobbySlot(int slot) {
    if(nearby==null||!nearby.host)return;
    try{
      if(movingSlot>=0){int from=movingSlot;movingSlot=-1;nearby.move(from,slot);}
      else if(nearby.occupants[slot]>=0)movingSlot=slot;
      else if(!nearby.resuming)nearby.setSlot(slot,(nearby.setup.controllers[slot]+1)%3);
    }catch(Exception e){nearby.fail(e.getMessage());}
  }
  private void changeLobbySetting(int field){
    if(nearby==null||!nearby.host||nearby.resuming)return;
    try{if(field==0)nearby.setup.cycleMode();else if(field==3)nearby.setup.heroMode=!nearby.setup.heroMode;else if(field==1)nearby.setup.difficulty=(nearby.setup.difficulty+1)%3;else nearby.setup.speed=(nearby.setup.speed+1)%7;nearby.settingsChanged();}
    catch(Exception e){nearby.fail(e.getMessage());}
  }
  private void uiLine(Canvas c,String label,NativeArt.Font font,float x,float y,int width,int height,int align,int color){
    c.save();c.clipRect(x,y,x+width,y+height);font.draw(c,MenuHotkeys.paintCaption(font,MenuHotkeys.fit(font,label,width),color),x,y+Math.max(0,(height-font.height)/2f),width,align,color,0);c.restore();
  }
  private boolean menuHot(float x,float y,float w,float h){return pendingScreen==null && (modal.isEmpty()||modal.startsWith("profile-")) && menuHoverX>=x && menuHoverX<x+w && menuHoverY>=y && menuHoverY<y+h;}
  private int menuColor(float x,float y,float w,float h,boolean enabled){return !enabled?5:menuHot(x,y,w,h)?4:3;}
  private void redBorder(Canvas c,float x,float y,float w,float h,boolean selected){
    paint.setColor(selected?0xffd92b24:0xff861414);
    // Three-pixel inset corners match the original fine red menu outlines.
    c.drawRect(x+3,y,x+w-3,y+1,paint);c.drawRect(x+3,y+h-1,x+w-3,y+h,paint);
    c.drawRect(x,y+3,x+1,y+h-3,paint);c.drawRect(x+w-1,y+3,x+w,y+h-3,paint);
    for(int i=1;i<3;i++){c.drawRect(x+3-i,y+i,x+4-i,y+i+1,paint);c.drawRect(x+w-4+i,y+i,x+w-3+i,y+i+1,paint);c.drawRect(x+3-i,y+h-i-1,x+4-i,y+h-i,paint);c.drawRect(x+w-4+i,y+h-i-1,x+w-3+i,y+h-i,paint);}
  }
  private void redButton(Canvas c,String label,float x,float y,int w,int h,Runnable action,boolean enabled){
    redButton(c,label,x,y,w,h,action,enabled,false);
  }

  private void redNavigationButton(Canvas c,String label,float x,float y,int w,int h,Runnable action,boolean enabled){
    redButton(c,label,x,y,w,h,action,enabled,true);
  }

  private void redButton(Canvas c,String label,float x,float y,int w,int h,Runnable action,boolean enabled,boolean navigation){
    NativeArt.Font preferred=h<28?glue.dialogue:glue.large;
    final NativeArt.Font font=preferred.measure(label)>w-8?glue.small:preferred;
    redBorder(c,x,y,w,h,false);
    queueMenuButton(new RectF(x,y,x+w,y+h),action,enabled,label,navigation,
        shown->uiLine(c,shown,font,x+4,y,w-8,h,1,menuColor(x,y,w,h,enabled)));
  }

  private void showCustom() {
    screen("custom", "glucustm", "palnl", true);
    sfx("swishin");
    customMaps = NativeMaps.list(gameFiles); mapOffset = 0; mapImportStatus = "";
    customBotSlot = 1;
    if(multiplayerSetup)nearbySetup=new MatchSetup(true);else customSetup=new MatchSetup(false);
    String last = prefs.getString("custom-map", "");
    File selected = null;
    for (File file : customMaps) if (file != null && file.getName().equals(last)) selected = file;
    selectCustomMap(selected);
    mapOffset = Math.min(mapMaxOffset(),Math.max(0,customMaps.indexOf(customMap))*22);
  }

  private void selectCustomMap(File map) {
    try {
      if(map==null){File old=customMap;customMap=null;try{inspectMap(nearbySourceMap());}finally{customMap=old;}}
      else inspectMap(map);
      customMap = map;
      prefs.edit().putString("custom-map", map == null ? "" : map.getName()).apply();
    } catch (Exception e) { hint = e.getMessage(); modal = "map-error"; }
  }

  private void customLabel(Canvas c, int id, String label, NativeArt.Font font, int align) {
    NativeArt.Widget w = glue.layout.get(id);
    if (w != null) uiLine(c,label,font,w.x,w.y,w.w,w.h,align,2);
  }

  private void drawMapThumbnail(Canvas c,float x,float y,int width,int height) {
    paint.setColor(0xff05080c);c.drawRect(x-2,y-2,x+width+2,y+height+2,paint);
    if(mapThumbnail!=null) {
      float scale=Math.min(width/(float)mapThumbnail.getWidth(),height/(float)mapThumbnail.getHeight());
      float w=mapThumbnail.getWidth()*scale,h=mapThumbnail.getHeight()*scale;
      c.drawBitmap(mapThumbnail,null,new RectF(x+(width-w)/2,y+(height-h)/2,x+(width+w)/2,y+(height+h)/2),NativeArt.PIXELS);
    } else text(c,mapPreviewError,x,y+height/2,width,1,2);
  }
  private float mapMaxOffset(){return Math.max(0,customMaps.size()*22-mapViewport.height());}
  private void drawMapBrowser(Canvas c) {
    mapOffset=Math.max(0,Math.min(mapMaxOffset(),mapOffset));
    c.save();c.clipRect(mapViewport);
    int first=(int)(mapOffset/22);
    for(int i=first;i<customMaps.size()&&i*22-mapOffset<mapViewport.height();i++) {
      final File map=customMaps.get(i);float y=mapViewport.top+i*22-mapOffset;
      glue.small.draw(c,NativeResults.fit(glue.small,NativeMaps.label(map),260),64,y+3,260,0,Objects.equals(map,customMap)?4:2,0);
      add(new RectF(60,Math.max(mapViewport.top,y),328,Math.min(mapViewport.bottom,y+22)),()->selectCustomMap(map),true);
    }
    c.restore();
    glue.scrollbar(c,332,130,110,mapMaxOffset()==0?0:mapOffset/mapMaxOffset());
    add(new RectF(328,126,352,146),()->mapOffset=Math.max(0,mapOffset-22),mapOffset>0);
    add(new RectF(328,224,352,244),()->mapOffset=Math.min(mapMaxOffset(),mapOffset+22),mapOffset<mapMaxOffset());
    Button bar=new Button(new RectF(328,146,352,224),()->{},mapMaxOffset()>0);bar.vertical=true;
    final long[] dragStarted={Long.MIN_VALUE};final float[] grip={0};
    bar.slider=v->{
      float maximum=mapMaxOffset(),position=v*bar.rect.height()-7,travel=bar.rect.height()-14;
      if(dragStarted[0]!=downAt){
        dragStarted[0]=downAt;float thumb=maximum>0?mapOffset/maximum*travel:0;
        grip[0]=Math.abs(position-thumb)<=9?position-thumb:0;
      }
      // Preserve where the finger grabbed the circle. Track taps jump directly;
      // dragging reaches both ends without changing the selected map.
      mapOffset=(v<=0?0:v>=1?1:Math.max(0,Math.min(1,(position-grip[0])/travel)))*maximum;
    };
    buttons.add(bar);
    redButton(c,"Import maps/ZIP",206,98,146,26,()->activity.runOnUiThread(activity::pickNativeMap),!importingMaps);
  }
  private void drawCustom(Canvas c) {
    if (glue == null) { drawMenuUnavailable(c, multiplayerSetup ? this::showNearby : this::showCampaign); return; }
    if(glue==null||glue.exiting||glue.elapsed<NativeGlue.ENTER_MS)return;
    MatchSetup setup=multiplayerSetup?nearbySetup:customSetup;boolean bw=multiplayerSetup?nearbyExpansion:expansion;
    glue.label(c,65535,bw?"Brood War":"StarCraft",true);

    customLabel(c,6,"Maps",glue.large,0);
    redButton(c,"Hero Mode: "+(setup.heroMode?"On":"Off"),410,57,190,20,()->setup.heroMode=!setup.heroMode,true);
    uiLine(c,customName,glue.dialogue,410,80,190,24,1,2);
    drawMapThumbnail(c,441,108,128,128);
    customLabel(c,10,"Size: "+customWidth+" x "+customHeight,glue.tiny,0);
    customLabel(c,11,"Tileset: "+customTileset,glue.tiny,0);
    drawMapBrowser(c);
    uiLine(c,"Game Type",glue.small,58,250,112,18,0,2);
    redButton(c,setup.mode(),180,250,172,18,setup::cycleMode,true);
    if(multiplayerSetup){
      uiLine(c,"Starting locations: "+customStarts,glue.large,40,320,312,28,0,2);
      body(c,""+(customStarts-1)+" open slots for other players. Set players, computers and positions in the lobby.",40,355,312,68);
    }else{
    for(int i=0;i<customStarts;i++) {
      final int slot=i;float y=315+i*19;boolean active=setup.controllers[i]!=0;
      String name=i==0?selectedProfile:active?"Computer":"Closed";
      redButton(c,name,33,y,setup.teamGame?134:150,17,i==0?null:()->setup.controllers[slot]=setup.controllers[slot]==2?0:2,true);
      float raceX=setup.teamGame?175:193;int raceW=setup.teamGame?96:150;
      redButton(c,active?(i==0&&customRace<0?"Random":MatchSetup.RACES[i==0?customRace:setup.races[i]]):"",raceX,y,raceW,17,()->{if(slot==0)customRace=customRace==2?-1:customRace+1;else setup.races[slot]=(setup.races[slot]+1)%3;},active);
      if(setup.teamGame)redButton(c,"T"+setup.teams[i],279,y,64,17,()->setup.teams[slot]=setup.teams[slot]%8+1,active);
    }
    }
    uiLine(c,"Difficulty",glue.small,58,272,112,18,0,2);
    redButton(c,new String[]{"Easy","Medium","Hard"}[setup.difficulty],180,272,172,18,()->setup.difficulty=(setup.difficulty+1)%3,true);
    redButton(c,"Speed: "+MatchSetup.SPEEDS[setup.speed],410,282,190,22,()->setup.speed=(setup.speed+1)%7,true);
    uiLine(c,!mapImportStatus.isEmpty()?mapImportStatus:setup.teamGame?"Same team shares vision and victory.":multiplayerSetup?"Choose settings, then create game.":"Tap slots and settings to change.",glue.tiny,410,309,190,18,0,2);
    glueButton(c,12,multiplayerSetup?"Create Game":null,()->{if(!multiplayerSetup&&customSetup.teamGame&&!customSetup.hasOpponents()){hint="Choose at least two opposing teams.";modal="map-error";return;}navigate(()->{if(multiplayerSetup)createNearby();else startSkirmish(customRace<0?new Random().nextInt(3):customRace,customMap);});});
    glueButton(c,13,null,()->navigate(multiplayerSetup?this::showNearby:this::showCampaign));
  }

  private void drawMenuUnavailable(Canvas c, Runnable back) {
    body(c, "This screen needs readable original menu files.\nRe-import StarDat.mpq, BrooDat.mpq and Patch_rt.mpq\nfrom the same classic installation. For campaign artwork,\nre-import StarCraft.mpq or the original StarCraft Install.exe.", 45, 130, 550, 120);
    button(c, "App Options", 120, 280, 400, 36,
        () -> activity.runOnUiThread(activity::requestAppOptions), true);
    button(c, "Back", 120, 380, 400, 36, () -> navigate(back), true);
  }

  private void customSelect(Canvas c, int id, String label, Runnable action) {
    NativeArt.Widget w = glue.layout.get(id);
    if (w == null) return;
    redBorder(c,w.x,w.y,w.w,w.h,false);
    queueMenuButton(w.rect(),action,true,label,false,
        shown->uiLine(c,shown,glue.dialogue,w.x+3,w.y,w.w-6,w.h,0,action==null?2:menuColor(w.x,w.y,w.w,w.h,true)));
  }

  private boolean installed(JSONObject m) {
    if (m.has("cinematic")) return availableMovies.contains(m.optString("cinematic"));
    return ProjectFiles.existing(gameFiles, CampaignData.filename(m.optString("id"))).isFile();
  }

  private void chooseGame(int option) {
    boolean introduction = modal.equals("intro-choose"), multiplayer=modal.equals("multiplayer-choose");
    modal = "";
    if (option == 2) return;
    boolean broodwar = option == 1;
    if(multiplayer){nearbyExpansion=broodwar;navigate(this::showNearby);}
    else if (introduction) {
      playCinematic(broodwar ? "smk/starxintr.smk" : "smk/starintr.smk", broodwar, this::showMain, true);
    } else {
      expansion = broodwar;
      prefs.edit().putBoolean("expansion", expansion).apply();
      registryForMultiplayer=false;showRegistry();
    }
  }

  private boolean drawGameChooser(Canvas c) {
    try {
      NativeArt.PCX bg = art.pcx("glue/palmm/retail_ex.pcx", false, -1);
      NativeArt.Dialog layout = art.dialog("glugamemode");
      paint.setColor(0x80000000);
      c.drawRect(0, 0, 640, 480, paint);
      float left = (640 - bg.width) / 2f, top = (480 - bg.height) / 2f;
      c.drawBitmap(bg.image, left, top, NativeArt.PIXELS);
      int[] ids = {6, 7, 3};
      String[] labels = {"Original", "Expansion", "Cancel"};
      for (int i = 0; i < ids.length; i++) {
        NativeArt.Widget w = layout.get(ids[i]);
        if (w == null) continue;
        final int option = i;
        RectF rect = w.hit();
        rect.offset(left, top);
        // Original/Expansion are bottom-aligned labels below the two logos.
        float labelY=top+w.y+(i<2?w.h-font().height:(w.h-font().height)/2f);
        queueMenuButton(rect,()->chooseGame(option),true,MenuLabels.forWidget("glugamemode",ids[i],w,labels[i]),
            shown->text(c,shown,left+w.x,labelY,w.w,1,3));
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private void drawModal(Canvas c) {
    menuBehindModal=false;
    if(modal.equals("nearby-error")) {
      float x=(logicalWidth-440)/2f;panel(c,x,100,440,280);
      text(c,"Nearby Multiplayer",x+20,120,400,1,2);body(c,nearbyStatus,x+24,166,392,138);
      button(c,"Return to menu",x+110,328,220,32,this::showMain,true);return;
    }
    buttons.clear();
    if(modal.startsWith("profile-")){drawProfileDialog(c);return;}
    if ((modal.equals("choose") || modal.equals("intro-choose") || modal.equals("multiplayer-choose")) && drawGameChooser(c)) return;
    if (modal.equals("missions") && drawHistory(c)) return;
    if (drawGamepadSettings(c)) return;
    if (drawReplayDialog(c)) return;
    if (drawSaves(c)) return;
    if (drawClassicMenu(c)) return;
    paint.setColor(0x60000000);
    c.drawRect(0, 0, logicalWidth, 480, paint);
    float x = (logicalWidth - 368) / 2f;
    panel(c, x, 56, 368, 368);
    switch (modal) {
      case "choose":
      case "intro-choose":
      case "multiplayer-choose":
        text(c, modal.equals("intro-choose") ? "Select Introduction" : "Select Game", x + 16, 77, 336, 1, 2);
        button(
            c,
            "StarCraft — Original",
            x + 28,
            137,
            312,
            48,
            () -> chooseGame(0),
            true);
        button(
            c,
            "Brood War — Expansion",
            x + 28,
            205,
            312,
            48,
            () -> chooseGame(1),
            true);
        button(c, "Cancel", x + 80, 366, 208, 30, () -> modal = "", true);
        break;
      case "missions":
        text(
            c,
            new String[] {"Zerg", "Terran", "Protoss"}[listRace] + " Campaign",
            x + 12,
            72,
            344,
            1,
            2);
        int end = Math.min(visibleMissions.size(), listScroll + 7);
        for (int i = listScroll; i < end; i++) {
          JSONObject m = visibleMissions.get(i);
          String title =
              (m.optInt("number") == 0 ? "" : m.optInt("number") + ". ") + m.optString("title");
          button(
              c,
              "",
              x + 20,
              109 + (i - listScroll) * 34,
              328,
              30,
              () -> chooseMission(m),
              installed(m));
          float row=109+(i-listScroll)*34;
          if(missionCompleted(m))drawMissionCheck(c,x+31,row+15);
          c.save();c.clipRect(x+44,row+2,x+344,row+29);
          text(c,title,x+44,row+6,296,0,installed(m)?3:5);c.restore();
        }
        button(
            c,
            "Up",
            x + 20,
            353,
            45,
            30,
            () -> listScroll = Math.max(0, listScroll - 5),
            listScroll > 0,false);
        button(
            c,
            "Down",
            x + 75,
            353,
            45,
            30,
            () -> listScroll = Math.min(Math.max(0, visibleMissions.size() - 7), listScroll + 5),
            end < visibleMissions.size(),false);
        button(c, "Back", x + 212, 353, 136, 30, () -> modal = "", true);
        break;
      case "skirmish":
        text(c, "Custom Game", x + 18, 75, 332, 1, 2);
        button(c, "Terran", x + 35, 126, 298, 32, () -> startSkirmish(1, null), true);
        button(c, "Zerg", x + 35, 172, 298, 32, () -> startSkirmish(0, null), true);
        button(c, "Protoss", x + 35, 218, 298, 32, () -> startSkirmish(2, null), true);
        button(
            c,
            "Import a map",
            x + 35,
            264,
            298,
            32,
            () -> activity.runOnUiThread(activity::pickNativeMap),
            true,false);
        button(c, "Back", x + 80, 365, 208, 30, () -> modal = "", true);
        break;
      default:
        text(c, modal.equals("error") ? "Unable to continue" : "StarDroid", x + 18, 75, 332, 1, 2);
        body(c, hint, x + 24, 118, 320, 212);
        button(
            c,
            "App Options",
            x + 28,
            365,
            152,
            30,
            () -> activity.runOnUiThread(activity::requestAppOptions),
            true);
        button(
            c,
            "Close",
            x + 188,
            365,
            152,
            30,
            () -> {
              boolean mapError = modal.equals("map-error");
              modal = "";
              if (!activeGame && !mapError) showMain();
            },
            true);
        break;
    }
  }

  private void body(Canvas c, String s, float x, float y, int width, int height) {
    c.save();
    c.clipRect(x, y, x + width, y + height);
    NativeArt.Font f = hud != null ? hud.small : glue != null ? glue.small : null;
    if (f != null) f.draw(c, s, x, y, width, 0, 2, 16);
    else fallback(c, s, x, y, 14, 0xffc5cce3);
    c.restore();
  }

  private void volume(Canvas c, float x, String name, float value, float y, boolean isMusic) {
    text(c, name + ": " + Math.round(value * 100) + "%", x + 24, y, 320, 1, 2);
    button(
        c,
        "-",
        x + 50,
        y + 35,
        90,
        32,
        () -> {
          if (isMusic) music = Math.max(0, music - .1f);
          else effects = Math.max(0, effects - .1f);
          saveSettings();
        },
        value > 0);
    button(
        c,
        "+",
        x + 228,
        y + 35,
        90,
        32,
        () -> {
          if (isMusic) music = Math.min(1, music + .1f);
          else effects = Math.min(1, effects + .1f);
          saveSettings();
        },
        value < 1);
  }

  private final Map<String, NativeArt.Dialog> menuLayouts = new HashMap<String, NativeArt.Dialog>();

  private NativeArt.Dialog dialog(String name) {
    if (!menuLayouts.containsKey(name))
      menuLayouts.put(name, art.dialog(name));
    return menuLayouts.get(name);
  }

  private void syncAudio() {
    boolean settingsPause = (modal.startsWith("gamepad") || modal.equals("control-desktop"))
        && Arrays.asList("movie", "story", "briefing", "results").contains(page);
    NativeBridge.audio(!foreground || settingsPause || (audio != null && !audio.canPlay()), muted ? 0 : effects,
        muted ? 0 : music * speechMusicGain);
  }

  private void navigate(Runnable next) {
    if (pendingScreen != null) return;
    if (page.equals("results") && results != null) {
      if (!results.ready()) return;
      results.exiting = true; results.animationElapsed = 0;
      pendingScreen = next; sfx("swishout");
    } else if (glue != null && glue.slide) {
      modal = "";
      glue.exiting = true;
      glue.elapsed = 0;
      pendingScreen = next;
      NativeBridge.sound(null, 62, false);
      sfx("swishout");
    } else next.run();
  }

  private void controlGroup(int group, boolean assign) {
    cancelGesture();
    // An empty slot is useful immediately: tap assigns the current selection.
    // Existing slots still recall; holding replaces an assignment.
    boolean setting=assign || NativeBridge.value(21,group)==0;
    NativeBridge.command(25,group,setting?1:0,0,0);
    int count=NativeBridge.value(21,group);
    if(setting) {
      hint=count>0?"Group "+group+": "+count+(count==1?" unit":" units")
          :"Select units, then tap an empty group or hold a group to assign";
      hintUntil=clock+2600;
    }
    padGroup=group;
  }

  private float controlGroupScale() { return 1.2f * groupPercent / 100f; }

  private int controlGroupRows() {
    return 8+316*controlGroupScale() > logicalWidth-204*resourcePercent/100f-8 ? 2 : 1;
  }

  private void drawControlGroups(Canvas c) {
    if (!controls.controlGroups) return;
    float scale=controlGroupScale();
    int columns=controlGroupRows()==1?10:5;
    for (int i = 0; i < 10; i++) {
      final int group = (i + 1) % 10;
      float x=8+(i%columns)*32*scale,y=2+(i/columns)*32*scale;
      RectF rect = new RectF(x,y,x+28*scale,y+30*scale);
      int count=NativeBridge.value(21,group);
      boolean hot=pressed!=null&&pressed.rect.equals(rect);
      c.save();c.translate(rect.left,rect.top);c.scale(scale,scale);
      c.drawBitmap(hud.panel(28,30,true,true),0,0,NativeArt.PIXELS);
      hud.large.draw(c,Integer.toString(group),0,count>0?1:5,28,1,hot?4:count>0?7:5,0);
      if(count>0)hud.small.draw(c,Integer.toString(count),0,19,28,1,2,0);
      c.restore();
      Button button=new Button(rect,()->controlGroup(group,false),true);
      button.longAction=()->controlGroup(group,true);
      button.controlGroup=true;
      buttons.add(button);
    }
  }

  private void drawSelectionButton(Canvas c) {
    if (controls.selectionButton == 0) return;
    float size = 44 * touchPercent / 100f;
    float x = controls.selectionButton == 1 ? 8 : logicalWidth - size - 8, y = 218;
    RectF rect = new RectF(x, y, x + size, y + size);
    hud.selectionButton(c, rect, oneShotSelect);
    add(rect, () -> {
      boolean next = !oneShotSelect;
      cancelGesture(); oneShotSelect = next;
      hint = next ? "Drag once to select units" : "Selection mode cancelled";
      hintUntil = clock + 1600;
    }, true);
  }

  private void drawCommandModeButton(Canvas c) {
    if (controls.selectionButton == 0 || !controls.commandModeButton) return;
    float size = 44 * touchPercent / 100f;
    float x = controls.selectionButton == 1 ? 8 : logicalWidth - size - 8;
    RectF rect = new RectF(x, 218 - 16 - size, x + size, 202);
    hud.commandModeButton(c, rect, controls.attackTap);
    add(rect, () -> {
      cancelGesture();
      changeControls(() -> controls.attackTap = !controls.attackTap);
      hint = (controls.attackTap ? "Attack-move taps" : "Move taps")
          + (controls.groundOrders ? "" : " — ground taps are disabled in Controls");
      hintUntil = clock + 2600;
    }, true);
  }

  private void cancelGesture() {
    cameraPan.reset();
    gesture = "";
    oneShotSelect = false;
    pressed = null;
    commandHover.cancel();
    placement = false;
    lastTapAt = 0;
    listVelocity = 0;
    scrollingList = "";
    if (activeGame) {
      NativeBridge.command(5, 0, 0, 0, 0);
      NativeBridge.command(0, -100, -100, 0, 0);
    }
  }

  private void openMenu() {
    cancelGesture();
    modal = "menu";
  }

  private void menuButton(Canvas c, String label, float x, float y, float w, float h,
      Runnable action, boolean enabled) {
    menuButton(c,label,x,y,w,h,action,enabled,true);
  }

  private void menuActionButton(Canvas c, String label, float x, float y, float w, float h,
      Runnable action, boolean enabled) {
    menuButton(c,label,x,y,w,h,action,enabled,false);
  }

  private void menuButton(Canvas c, String label, float x, float y, float w, float h,
      Runnable action, boolean enabled, boolean navigation) {
    c.drawBitmap(hud.panel(Math.round(w), Math.round(h), true, enabled), x, y, NativeArt.PIXELS);
    boolean hot = pressed != null && pressed.rect.contains(x + w / 2, y + h / 2);
    queueMenuButton(new RectF(x,y,x+w,y+h),action,enabled,label,navigation,
        shown->uiLine(c,shown,hud.large,x+4,y,(int)w-8,(int)h,1,enabled?hot?2:3:5));
  }

  private void originalMenuButton(Canvas c, String layout, int id, String label,
      float x, float y, Runnable action, boolean enabled) {
    NativeArt.Widget w = dialog(layout).get(id);
    if (w != null) menuButton(c, MenuLabels.forWidget(layout,id,w,label),
        x + w.x, y + w.y, w.w, w.h, action, enabled);
  }

  private void option(Canvas c, String label, float x, float y, float width, Runnable action) {
    option(c,label,x,y,width,action,false);
  }

  private void navigationOption(Canvas c, String label, float x, float y, float width, Runnable action) {
    option(c,label,x,y,width,action,true);
  }

  private void option(Canvas c, String label, float x, float y, float width, Runnable action, boolean navigation) {
    c.drawBitmap(hud.panel(Math.round(width), 28, true, true), x, y, NativeArt.PIXELS);
    boolean hot = pressed != null && pressed.rect.contains(x + width / 2, y + 14);
    boolean selected = label.startsWith("> ");
    queueMenuButton(new RectF(x,y,x+width,y+28),action,true,selected?label.substring(2):label,navigation,
        shown->uiLine(c,shown,hud.small,x+8,y,(int)width-16,28,1,hot?4:selected?7:2));
  }

  private void setWideMode(int mode) {
    wide = mode != 0;
    overCamera = mode == 2;
    saveSettings();
    resizeWorld();
    activity.runOnUiThread(activity::refreshDisplayArea);
  }

  private void radio(Canvas c, String label, boolean on, float x, float y, int width, Runnable action) {
    paint.setColor(on ? 0xffd1be56 : 0xff6282bc);
    paint.setStyle(Paint.Style.STROKE);
    c.drawCircle(x + 7, y + 10, 6, paint);
    paint.setStyle(Paint.Style.FILL);
    if (on) c.drawCircle(x + 7, y + 10, 3, paint);
    uiLine(c, label, hud.small, x + 20, y, width - 20, 23, 0, on ? 7 : 2);
    add(new RectF(x - 4, y - 4, x + width, y + 27), action, true);
  }

  private void toggle(Canvas c, String label, boolean on, float x, float y, Runnable action) {
    boolean hot = pressed != null && pressed.rect.contains(x + 100, y + 8);
    if (hot) {paint.setColor(0x552f4780); c.drawRect(x - 4, y - 4, x + 324, y + 23, paint);}
    paint.setColor(0xff040e26);
    c.drawRect(x, y + 2, x + 14, y + 16, paint);
    paint.setColor(on ? 0xffd1be56 : 0xff6282bc);
    paint.setStyle(Paint.Style.STROKE);
    c.drawRect(x, y + 2, x + 14, y + 16, paint);
    if (on) {
      paint.setStrokeWidth(2);
      c.drawLine(x + 2, y + 9, x + 6, y + 13, paint);
      c.drawLine(x + 6, y + 13, x + 12, y + 5, paint);
      paint.setStrokeWidth(1);
    }
    paint.setStyle(Paint.Style.FILL);
    uiLine(c, label, hud.small, x + 24, y, 296, 23, 0, hot ? 4 : 2);
    add(new RectF(x - 4, y - 4, x + 324, y + 23), action, true);
  }

  private void slider(Canvas c, String label, float value, float x, float y,
      java.util.function.Consumer<Float> setter) {
    hud.small.draw(c, label + " " + Math.round(value * 100) + "%", x, y + 3, 150, 0, 2, 0);
    float left = x + 160, width = 160;
    paint.setColor(0xff52698d);
    c.drawRect(left - 1, y + 7, left + width + 1, y + 14, paint);
    paint.setColor(0xff040e26);
    c.drawRect(left, y + 8, left + width, y + 13, paint);
    paint.setColor(0xff91aedc);
    c.drawRect(left, y + 8, left + width * value, y + 13, paint);
    float knob = left + width * value;
    boolean hot = pressed != null && pressed.slider != null && pressed.rect.contains(left + width / 2, y + 10);
    paint.setColor(hot ? 0xffd1be56 : 0xff91aedc);
    c.drawRect(knob - 5, y + 3, knob + 5, y + 18, paint);
    paint.setColor(0xff182749);
    c.drawRect(knob - 2, y + 5, knob + 2, y + 16, paint);
    Button b = new Button(new RectF(left, y - 8, left + width, y + 28), () -> {}, true);
    b.slider = setter;
    buttons.add(b);
  }

  private boolean drawClassicMenu(Canvas c) {
    if (!page.equals("game") || hud == null) return false;
    if (modal.equals("tips") || modal.equals("objectives")) {
      float x = (logicalWidth - 392) / 2f, y = 32;
      panel(c, x, y, 392, 256);
      hud.large.draw(c, modal.equals("tips") ? "StarCraft Tips" : "Mission Objectives", x + 6, y + 9, 380, 1, 2, 0);
      c.save(); c.clipRect(x + 6, y + 34, x + 386, y + 190);
      String value = modal.equals("tips") ? tipText() : campaign == null ? "" : campaign.objectives;
      hud.large.draw(c, value, x + 6, y + 34, 380, 0, 2, 22);
      c.restore();
      if (modal.equals("tips")) {
        toggle(c, "Show Tips at Startup", prefs.getBoolean("show-tips", true), x + 20, y + 192,
            () -> prefs.edit().putBoolean("show-tips", !prefs.getBoolean("show-tips", true)).apply());
        menuButton(c, "OK", x + 20, y + 216, 104, 28, () -> {modal = startupTips ? "" : "menu"; startupTips = false;}, true);
        menuActionButton(c, "Previous Tip", x + 140, y + 216, 104, 28, () -> tipIndex = (tipIndex + tips.length - 1) % tips.length, tips.length > 0);
        menuActionButton(c, "Next Tip", x + 260, y + 216, 104, 28, () -> tipIndex = (tipIndex + 1) % tips.length, tips.length > 0);
      } else menuButton(c, "Previous (Esc)", x + 80, y + 216, 224, 28, this::modalBack, true);
      return true;
    }
    boolean sheet = Arrays.asList("sound", "speed", "video", "ui-sizes", "network", "misc", "testing", "controls", "control-select", "control-timing", "control-camera", "control-orders", "control-help", "objectives").contains(modal);
    if (!sheet && !Arrays.asList("menu", "options", "end", "quit", "restart", "exit", "outcome").contains(modal)) return false;
    float x = (logicalWidth - (sheet ? 368 : 264)) / 2f - (sheet ? 0 : 4), y = 32;
    if (modal.equals("outcome")) {
      x = (logicalWidth - 288) / 2f;
      panel(c, x, 144, 288, 128);
      text(c, lastOutcome == 1 ? "Congratulations!\nYou are victorious!" : "You have been defeated.", x + 12, 152, 264, 1, 2);
      menuButton(c, lastOutcome == 1 ? "Victory" : "Defeat", x + 32, 202, 224, 28, this::showResults, true);
      return true;
    }
    panel(c, x, y, sheet ? 368 : 264, sheet ? 330 : 288);
    String title = modal.equals("menu") ? "Game Menu" : modal.equals("options") ? "Game Options"
        : modal.equals("sound") ? "Sound & Music" : modal.equals("speed") ? "Game Speed"
        : modal.equals("ui-sizes") ? "Interface Sizes" : modal.equals("network") ? "Network Diagnostics" : modal.equals("video") ? "Video Options" : modal.equals("misc") ? "Miscellaneous" : modal.equals("testing") ? "Cheats"
        : modal.equals("control-select") ? "Selection Settings" : modal.equals("control-timing") ? "Timing & accuracy" : modal.equals("control-camera") ? "Camera Settings"
        : modal.equals("control-orders") ? "Command Settings" : modal.startsWith("control") ? "Controls" : modal.equals("objectives") ? "Mission Objectives"
        : modal.equals("restart") ? "Restart Mission" : "End Mission";
    text(c, title, x, y + 8, sheet ? 368 : 264, 1, 2);
    final float left = x;
    if (modal.equals("menu") && replayGame) {
      menuButton(c,"Resume Replay",x+20,y+48,224,28,()->modal="",true);
      menuButton(c,"Restart",x+20,y+90,224,28,()->startReplay(playingReplay),true);
      menuButton(c,"Game Options",x+20,y+132,224,28,()->modal="options",true);
      menuButton(c,"Exit Replay",x+20,y+216,224,28,this::showReplays,true);
    } else if (modal.equals("menu")) {
      originalMenuButton(c, "gamemenu", 1, null, x, y, () -> openSaves(true), lastOutcome == 0 && (!multiplayerGame||nearby!=null&&nearby.host&&!nearby.closed));
      originalMenuButton(c, "gamemenu", 2, null, x, y, () -> openSaves(false), !multiplayerGame||nearby!=null&&nearby.host&&!nearby.closed);
      originalMenuButton(c, "gamemenu", 3, null, x, y, () -> modal = "options", true);
      originalMenuButton(c, "gamemenu", 4, null, x, y, () -> {startupTips = false; modal = "tips";}, true);
      if (campaign != null) originalMenuButton(c, "gamemenu", 5, null, x, y, () -> modal = "objectives", true);
      originalMenuButton(c, "gamemenu", 6, null, x, y, () -> modal = "end", true);
      originalMenuButton(c, "gamemenu", 65533, null, x, y, () -> modal = "", true);
    } else if (modal.equals("options")) {
      originalMenuButton(c, "options", 1, null, x, y, () -> modal = "speed", nearby==null&&!replayGame);
      originalMenuButton(c, "options", 2, null, x, y, () -> modal = "sound", true);
      originalMenuButton(c, "options", 3, null, x, y, () -> modal = "video", true);
      originalMenuButton(c, "options", 4, "Controls", x, y, () -> modal = "controls", true);
      menuButton(c, nearby==null?"Miscellaneous":"Network", x + 20, y + 172, 224, 28, () -> modal = nearby==null?"misc":"network", !replayGame);
      hud.small.draw(c, "Sound, speed and display preferences", x + 20, y + 215, 224, 1, 2, 0);
    } else if (modal.equals("end")) {
      originalMenuButton(c, "abrtmenu", 1, null, x, y, () -> modal = "restart", currentMission != null);
      originalMenuButton(c, "abrtmenu", 2, null, x, y, () -> modal = "quit", true);
      originalMenuButton(c, "abrtmenu", 3, null, x, y, () -> modal = "exit", true);
    } else if (modal.equals("sound")) {
      drawSoundSettings(c, x, y);
    } else if (modal.equals("speed")) {
      double[] values = {167, 111, 83, 67, 56, 48, 42, 21, 10.5, 5.25, 2.625};
      String[] labels = {"Slowest", "Slower", "Slow", "Normal", "Fast", "Faster", "Fastest (1x)", "2x Fastest", "4x Fastest", "8x Fastest", "16x Fastest"};
      for (int i = 0; i < values.length; i++) {
        final double value = values[i];
        option(c, (speed == value ? "> " : "") + labels[i], x + 22 + (i % 2) * 165,
            y + 48 + (i / 2) * 34, 158, () -> {speed = value; accumulator = 0; saveSettings();});
      }
      hud.small.draw(c, "Actual speed depends on your device.", x + 24, y + 260, 320, 1, 2, 0);
    } else if (modal.equals("video")) {
      toggle(c, "Show order & rally lines", orderLines, x + 26, y + 48, () -> {orderLines = !orderLines; NativeBridge.command(19, orderLines ? 1 : 0, 0, 0, 0); saveSettings();});
      hud.small.draw(c, "Widescreen", x + 26, y + 79, 320, 0, 2, 0);
      radio(c, "Off", !wide, x + 26, y + 100, 62, () -> setWideMode(0));
      radio(c, "On", wide && !overCamera, x + 98, y + 100, 62, () -> setWideMode(1));
      radio(c, "+ over camera area", wide && overCamera, x + 170, y + 100, 176, () -> setWideMode(2));
      toggle(c, "Allow zoom out", zoomOut, x + 26, y + 132, () -> {zoomOut = !zoomOut; if (!zoomOut) zoom = Math.max(1, zoom); resizeWorld(); saveSettings();});
      option(c, "View zoom: " + Math.round(zoom * 100) + "%", x + 26, y + 165, 320, () -> {
        float[] levels = {.5f, .67f, 1, 1.25f, 1.5f, 2}; float next = zoomOut ? .5f : 1;
        for (float v : levels) if (v > zoom + .005f && (zoomOut || v >= 1)) {next = v; break;}
        zoom = next; resizeWorld(); saveSettings();
      });
      option(c, "Frame rate: " + fps, x + 26, y + 198, 320, () -> {fps = fps == 30 ? 60 : fps == 60 ? 90 : fps == 90 ? 120 : 30; saveSettings();});
      navigationOption(c, "Interface sizes", x + 26, y + 264, 320, () -> modal = "ui-sizes");
      toggle(c, "Smooth graphics (game + UI)", smoothGraphics, x + 26, y + 231, () -> {smoothGraphics = !smoothGraphics; NativeArt.setSmoothGraphics(smoothGraphics); saveSettings();});
    } else if (modal.equals("network")) {
      toggle(c,"Show network overlay",networkStats,x+26,y+48,()->{networkStats=!networkStats;saveSettings();});
      body(c,(nearby==null?"No active match":nearby.summary())+"\n"+String.format(Locale.ROOT,"Display: %.1f FPS\nDrawing: %.2f ms/frame\nTotal frame work: %.2f ms/frame",displayFps,drawMs,frameMs)+"\nInput buffer: "+(nearby==null?168:nearby.setup.frameMs()*4)+" ms.\nWait: blocked on other devices.\nValues retain the last active sample.",x+26,y+88,320,142);
      navigationOption(c,"View / copy diagnostics",x+26,y+240,320,()->activity.runOnUiThread(activity::requestDiagnostics));
    } else if (modal.equals("ui-sizes")) {
      option(c, "Resources: " + resourcePercent + "%", x + 26, y + 48, 320, () -> {resourcePercent = resourcePercent >= 150 ? 100 : resourcePercent + 25; saveSettings();});
      option(c, "Command buttons: " + commandPercent + "%", x + 26, y + 88, 320, () -> {commandPercent = commandPercent >= 120 ? 100 : commandPercent + 10; saveSettings();});
      option(c, "Control groups: " + groupPercent + "%", x + 26, y + 128, 320, () -> {groupPercent = groupPercent >= 150 ? 75 : Math.min(150, groupPercent + 25); saveSettings();});
      option(c, "Descriptions & messages: " + descriptionPercent + "%", x + 26, y + 168, 320, () -> {descriptionPercent = descriptionPercent >= 150 ? 100 : descriptionPercent + 25; saveSettings();});
      option(c, "Selection & move buttons: " + touchPercent + "%", x + 26, y + 208, 320, () -> {touchPercent = touchPercent >= 150 ? 100 : touchPercent + 25; saveSettings();});
      option(c, "Reset interface sizes", x + 26, y + 256, 320, () -> {resourcePercent = commandPercent = groupPercent = descriptionPercent = touchPercent = 100; saveSettings();});
    } else if (modal.equals("misc")) {
      toggle(c, "Nearby enemies defend their base", nearbyDefenders, x + 22, y + 52, () -> {
        nearbyDefenders = !nearbyDefenders; syncControls(); saveSettings();
      });
      body(c, "When enabled, attacked bases call nearby idle combat units to help.\n\nScripted mission attacks still run.", x + 22, y + 93, 324, 100);
      navigationOption(c, "Cheats", x + 22, y + 207, 324, () -> modal = "testing");
    } else if (modal.equals("testing")) {
      option(c, "+10,000 minerals and gas", x + 26, y + 46, 320, () -> NativeBridge.command(9, 0, 0, 0, 0));
      toggle(c, "Instant production", instant, x + 26, y + 84, () -> {instant = !instant; NativeBridge.command(10, instant ? 1 : 0, 0, 0, 0);});
      option(c, "Reveal map", x + 26, y + 116, 320, () -> NativeBridge.command(11, 0, 0, 0, 0));
      navigationOption(c, "Force victory (end match)", x + 26, y + 158, 320, () -> {NativeBridge.command(13, 1, 0, 0, 0); modal = "";});
      toggle(c,"Invincibility",NativeBridge.value(22,0)!=0,x+26,y+200,
          ()->NativeBridge.command(29,NativeBridge.value(22,0)==0?1:0,0,0,0));
      toggle(c,"Unlimited supply (200)",NativeBridge.value(24,0)!=0,x+26,y+238,
          ()->NativeBridge.command(30,NativeBridge.value(24,0)==0?1:0,0,0,0));
    } else if (modal.equals("control-help")) {
      body(c, "Tap a friendly unit to select it.\n"
          + (controls.doubleTap ? "Double tap to select its type.\n" : "Double tap selection is off.\n")
          + (controls.groundOrders ? "Army ground taps: " + (controls.attackTap ? "attack move.\n" : "move.\n") : "Ground-tap orders are off. Use command buttons.\n")
          + "Workers move, gather and build.\n"
          + (controls.dragMode == 1 ? "Drag to select; use two fingers to pan.\n" : "Drag to pan; hold, then drag to select.\n")
          + (controls.pinchZoom ? "Pinch with two fingers to zoom.\n" : "Pinch zoom is off.\n")
          + "Tap or drag the minimap to move the camera.\n"
          + (controls.confirmBuild ? "Position a building, then tap Build.\n" : "Release your finger to place a building.\n")
          + (controls.tooltips ? "Hold a command to read its details.\n" : "Command descriptions are off.\n")
          + (controls.controlGroups ? "Groups: tap empty to assign; hold replaces; tap recalls; double tap centers.\n" : "")
          + "Tap a production icon to cancel it.\nBack cancels targeting or opens this menu.", x + 26, y + 47, 320, 232);
    } else if (modal.equals("controls") || modal.startsWith("control-")) {
      drawControls(c, x, y);
    } else if (modal.equals("objectives")) {
      if (campaign != null) body(c, campaign.objectives, x + 26, y + 47, 320, 232);
    } else {
      final String action = modal;
      body(c, "Current progress will be lost.\nContinue?", x + 20, y + 55, 224, 95);
      menuButton(c, "Confirm", x + 20, y + 180, 224, 28, () -> {
        if (action.equals("restart")) loadMission(currentMission);
        else if (action.equals("exit")) {closeNearby();activity.runOnUiThread(activity::requestExit);}
        else showMain();
      }, true);
      menuButton(c, "Cancel", x + 20, y + 220, 224, 28, () -> modal = "end", true);
      return true;
    }
    if (!modal.equals("menu")) menuButton(c, "Previous", left + (sheet ? 72 : 20), y + (sheet ? 290 : 252), 224, 28, this::modalBack, true);
    return true;
  }

  private void modalBack() {
    if(modal.equals("control-desktop")){desktopSettingsBack();return;}
    if (modal.equals("replay-delete")||modal.equals("replay-notice")) {modal="";return;}
    if (modal.startsWith("gamepad")) { gamepadSettingsBack(); return; }
    if (modal.equals("control-timing")) { modal = "control-select"; return; }
    if(modal.equals("nearby-error")){showMain();return;}
    if (modal.equals("save") || modal.equals("load")) {modal=activeGame&&page.equals("game")?"menu":"";return;}
    if (modal.startsWith("save-")) {modal=saveMode;return;}
    if (modal.equals("outcome")) {showResults(); return;}
    if (modal.equals("tips")) {modal = startupTips ? "" : "menu"; startupTips = false;}
    else if (modal.startsWith("control-")) modal = "controls";
    else if (modal.equals("ui-sizes")) modal = "video";
    else if (modal.equals("testing")) modal = "misc";
    else if (Arrays.asList("sound", "speed", "video", "network", "misc", "controls").contains(modal)) modal = "options";
    else if (Arrays.asList("quit", "restart", "exit").contains(modal)) modal = "end";
    else if (Arrays.asList("options", "end", "controls", "objectives").contains(modal)) modal = "menu";
    else modal = "";
  }

  private void changeControls(Runnable update) {
    update.run(); controls.preset = "Custom"; applyControls();
  }

  /** Controller settings are available only through the in-game Controls menu. */
  void openGamepadSettings() {
    enqueue(() -> {
      if (!activeGame || !page.equals("game") || modal.startsWith("gamepad")) return;
      resetGamepad();
      cancelGesture();
      gamepad.load(prefs);
      gamepadReturnModal = modal;
      modal = "gamepad";
    });
  }

  private void gamepadSettingsBack() {
    resetGamepad();
    if (modal.equals("gamepad-action")) modal = "gamepad-bindings";
    else if (!modal.equals("gamepad")) modal = "gamepad";
    else {
      modal = gamepadReturnModal;
      gamepadReturnModal = "";
    }
  }

  private void changeGamepad(Runnable update) {
    // Cancel any held controller input before a binding or enable state changes.
    resetGamepad();
    update.run();
    gamepad.save(prefs);
  }

  private void gamepadLine(Canvas c, String label, float x, float y, int width,
      int height, int align, int color) {
    NativeArt.Font f = hud != null && page.equals("game") ? hud.small
        : glue != null ? glue.small : null;
    if (f != null) uiLine(c, label, f, x, y, width, height, align, color);
    else {
      c.save(); c.clipRect(x, y, x + width, y + height);
      fallbackMenu(c,label,x,y,width,height,color);
      c.restore();
    }
  }

  private void gamepadOption(Canvas c, String label, String detail, float x, float y,
      int width, int height, boolean selected, Runnable action) {
    gamepadOption(c,label,detail,x,y,width,height,selected,action,true);
  }

  private void gamepadChoice(Canvas c, String label, String detail, float x, float y,
      int width, int height, boolean selected, Runnable action) {
    gamepadOption(c,label,detail,x,y,width,height,selected,action,false);
  }

  private void gamepadOption(Canvas c, String label, String detail, float x, float y,
      int width, int height, boolean selected, Runnable action, boolean navigation) {
    if (page.equals("game") && hud != null)
      c.drawBitmap(hud.panel(width, height, true, true), x, y, NativeArt.PIXELS);
    else if (glue != null) c.drawBitmap(glue.panel(width, height, true), x, y, NativeArt.PIXELS);
    else panel(c, x, y, width, height);
    boolean hot = pressed != null && pressed.rect.contains(x + width / 2f, y + height / 2f);
    int color = hot ? 4 : selected ? 7 : 2;
    if (detail != null) {
      gamepadLine(c, detail, x + 8, y + 20, width - 16, height - 22, 1, color);
    }
    queueMenuButton(new RectF(x,y,x+width,y+height),action,true,label,navigation,
        shown->gamepadLine(c,shown,x+8,detail==null?y:y+2,width-16,detail==null?height:18,1,detail==null?color:2));
  }

  private void gamepadToggle(Canvas c, String label, boolean on, float x, float y,
      Runnable action) {
    paint.setColor(0xff040e26); c.drawRect(x, y + 5, x + 14, y + 19, paint);
    paint.setStyle(Paint.Style.STROKE);
    paint.setColor(on ? 0xffd1be56 : 0xff6282bc);
    c.drawRect(x, y + 5, x + 14, y + 19, paint);
    if (on) {
      paint.setStrokeWidth(2);
      c.drawLine(x + 2, y + 12, x + 6, y + 16, paint);
      c.drawLine(x + 6, y + 16, x + 12, y + 8, paint);
      paint.setStrokeWidth(1);
    }
    paint.setStyle(Paint.Style.FILL);
    gamepadLine(c, label, x + 24, y, 368, 26, 0, 2);
    add(new RectF(x - 4, y - 3, x + 396, y + 29), action, true);
  }

  private void gamepadSlider(Canvas c, String label, int value, int min, int max,
      float x, float y, java.util.function.Consumer<Integer> setter) {
    gamepadLine(c, PortText.text(label) + ": " + value + "%", x, y, 198, 28, 0, 2);
    float left = x + 208, width = 184, fraction = (value - min) / (float)(max - min);
    paint.setColor(0xff52698d); c.drawRect(left - 1, y + 10, left + width + 1, y + 17, paint);
    paint.setColor(0xff040e26); c.drawRect(left, y + 11, left + width, y + 16, paint);
    paint.setColor(0xff91aedc); c.drawRect(left, y + 11, left + width * fraction, y + 16, paint);
    float knob = left + width * fraction;
    paint.setColor(0xffd1be56); c.drawRect(knob - 5, y + 6, knob + 5, y + 22, paint);
    paint.setColor(0xff182749); c.drawRect(knob - 2, y + 8, knob + 2, y + 20, paint);
    Button b = new Button(new RectF(left, y - 4, left + width, y + 32), () -> {}, true);
    b.slider = v -> setter.accept(GamepadSettings.sliderPercent(v, min, max));
    buttons.add(b);
  }

  private boolean drawGamepadSettings(Canvas c) {
    if(drawDesktopSettings(c))return true;
    if (!modal.startsWith("gamepad")) return false;
    float x = (logicalWidth - 440) / 2f, y = 40;
    paint.setColor(0x80000000); c.drawRect(0, 0, logicalWidth, 480, paint);
    panel(c, x, y, 440, 400);
    String title = modal.equals("gamepad-bindings") ? "Button assignments"
        : modal.equals("gamepad-action") ? GamepadControls.BUTTON_LABELS[gamepadBinding]
        : modal.equals("gamepad-reset") ? "Reset to defaults" : "Gamepad controls";
    text(c, title, x + 12, y + 12, 416, 1, 2);
    if (modal.equals("gamepad")) {
      gamepadToggle(c, "Enable gamepad", gamepad.enabled, x + 24, y + 52,
          () -> changeGamepad(() -> gamepad.enabled = !gamepad.enabled));
      gamepadToggle(c, "Swap pointer and camera sticks", gamepad.swapSticks, x + 24, y + 86,
          () -> changeGamepad(() -> gamepad.swapSticks = !gamepad.swapSticks));
      gamepadSlider(c, "Pointer speed", Math.round(gamepad.pointerSpeed * 100), 25, 300, x + 24, y + 120,
          value -> { gamepad.pointerSpeed = value / 100f; gamepad.save(prefs); });
      gamepadSlider(c, "Camera speed", Math.round(gamepad.cameraSpeed * 100), 25, 300, x + 24, y + 158,
          value -> { gamepad.cameraSpeed = value / 100f; gamepad.save(prefs); });
      gamepadSlider(c, "Stick dead zone", Math.round(gamepad.deadZone * 100), 5, 50, x + 24, y + 196,
          value -> { gamepad.deadZone = value / 100f; gamepad.save(prefs); });
      gamepadOption(c, "Button assignments", null, x + 24, y + 238, 192, 32, false,
          () -> modal = "gamepad-bindings");
      gamepadOption(c, "Reset to defaults", null, x + 224, y + 238, 192, 32, false,
          () -> modal = "gamepad-reset");
      gamepadOption(c, "Controller guide", null, x + 24, y + 276, 392, 32, false,
          () -> modal = "gamepad-help");
    } else if (modal.equals("gamepad-bindings")) {
      for (int i = 0; i < GamepadControls.BUTTONS.length; i++) {
        final int binding = i;
        gamepadOption(c, GamepadControls.BUTTON_LABELS[i], GamepadControls.ACTION_LABELS[gamepad.bindings[i]],
            x + 24 + (i % 2) * 200, y + 46 + (i / 2) * 46, 192, 42, false,
            () -> { gamepadBinding = binding; modal = "gamepad-action"; });
      }
    } else if (modal.equals("gamepad-action")) {
      for (int i = 0; i < GamepadControls.ACTION_LABELS.length; i++) {
        final int action = i;
        gamepadChoice(c, GamepadControls.ACTION_LABELS[i], null,
            x + 24 + (i % 2) * 200, y + 46 + (i / 2) * 35, 192, 32,
            gamepad.bindings[gamepadBinding] == action,
            () -> { changeGamepad(() -> gamepad.bindings[gamepadBinding] = action); modal = "gamepad-bindings"; });
      }
    } else if (modal.equals("gamepad-reset")) {
      body(c, "Reset all gamepad controls to their defaults?", x + 24, y + 75, 392, 120);
      gamepadOption(c, "Reset to defaults", null, x + 24, y + 238, 192, 32, false,
          () -> { changeGamepad(gamepad::reset); modal = "gamepad"; });
      gamepadOption(c, "Cancel", null, x + 224, y + 238, 192, 32, false, this::gamepadSettingsBack);
      return true;
    } else {
      body(c, GamepadSettings.POINTER_GUIDE,
          x + 24, y + 55, 392, 140);
      body(c, GamepadSettings.MENU_GUIDE,
          x + 24, y + 205, 392, 120);
    }
    gamepadOption(c, "Previous", null, x + 112, y + 350, 216, 32, false, this::gamepadSettingsBack);
    return true;
  }

  private void applyControls() {
    controls.save(prefs);
    syncControls();
  }

  private void syncControls() {
    commandHover.holdMs = controls.tooltipMs;
    commandHover.enabled = controls.tooltips;
    NativeBridge.command(22, controls.radius, controls.attackTap ? 1 : 0, controls.groundOrders ? 1 : 0, 0);
    NativeBridge.command(23, nearbyDefenders ? 1 : 0, 0, 0, 0);
    NativeBridge.command(26, 1, 0, 0, 0); // This host owns timed edge scrolling.
    NativeBridge.command(27, controls.originalLarvae ? 1 : 0, 0, 0, 0);
    NativeBridge.command(28, controls.singleLarvaMorph ? 1 : 0, 0, 0, 0);
    if (controls.selectionButton == 0) oneShotSelect = false;
  }

  private void controlOption(Canvas c, String label, float x, float y, Runnable update) {
    option(c, label, x + 22, y, 324, () -> changeControls(update));
  }

  private void drawControls(Canvas c, float x, float y) {
    if (modal.equals("controls")) {
      navigationOption(c, "Selection settings", x + 22, y + 48, 324, () -> modal = "control-select");
      navigationOption(c, "Camera settings", x + 22, y + 88, 324, () -> modal = "control-camera");
      navigationOption(c, "Command settings", x + 22, y + 128, 324, () -> modal = "control-orders");
      navigationOption(c, "Gesture guide", x + 22, y + 168, 324, () -> modal = "control-help");
      navigationOption(c,"Gamepad controls",x+22,y+208,324,this::openGamepadSettings);
      navigationOption(c,"Mouse & keyboard",x+22,y+248,324,this::openDesktopSettings);
    } else if (modal.equals("control-select")) {
      toggle(c, "Original larvae selection", controls.originalLarvae, x + 22, y + 44, () -> changeControls(() -> controls.originalLarvae = !controls.originalLarvae));
      toggle(c, "One larva morph per press", controls.singleLarvaMorph, x + 22, y + 78, () -> changeControls(() -> controls.singleLarvaMorph = !controls.singleLarvaMorph));
      toggle(c, "Hold tap always selects", controls.holdSelect, x + 22, y + 112, () -> changeControls(() -> controls.holdSelect = !controls.holdSelect));
      toggle(c, "Double tap selects same type", controls.doubleTap, x + 22, y + 146, () -> changeControls(() -> controls.doubleTap = !controls.doubleTap));
      controlOption(c, "Selection button: " + new String[]{"Off", "Left", "Right"}[controls.selectionButton], x, y + 180, () -> controls.selectionButton = (controls.selectionButton + 1) % 3);
      toggle(c, "Show control group buttons", controls.controlGroups, x + 22, y + 214, () -> changeControls(() -> controls.controlGroups = !controls.controlGroups));
      navigationOption(c, "Timing & accuracy", x + 22, y + 248, 324, () -> modal = "control-timing");
    } else if (modal.equals("control-timing")) {
      controlOption(c, "Drag: " + (controls.dragMode == 0 ? "Pan / hold to select" : "Select / two fingers pan"), x, y + 45, () -> controls.dragMode = 1 - controls.dragMode);
      controlOption(c, "Selection hold: " + controls.holdMs + " ms", x, y + 76, () -> controls.holdMs = controls.holdMs >= 500 ? 100 : controls.holdMs + 50);
      controlOption(c, "Tap tolerance: " + (controls.radius == 0 ? "Exact" : controls.radius), x, y + 107, () -> controls.radius = controls.radius >= 40 ? 0 : Math.min(40, controls.radius + 4));
      controlOption(c, "Double tap window: " + controls.doubleTapMs + " ms", x, y + 138, () -> controls.doubleTapMs = controls.doubleTapMs >= 500 ? 200 : controls.doubleTapMs + 50);
    } else if (modal.equals("control-camera")) {
      controlOption(c, "Pan speed: " + Math.round(controls.panSpeed * 100) + "%", x, y + 40, () -> controls.panSpeed = controls.panSpeed >= 2.5f ? .5f : controls.panSpeed + .25f);
      controlOption(c, "Pinch sensitivity: " + Math.round(controls.pinchSpeed * 100) + "%", x, y + 72, () -> controls.pinchSpeed = controls.pinchSpeed >= 2.5f ? .5f : controls.pinchSpeed + .25f);
      toggle(c, "Pinch zoom", controls.pinchZoom, x + 22, y + 104, () -> changeControls(() -> controls.pinchZoom = !controls.pinchZoom));
      toggle(c, "Reverse camera drag", controls.reversePan, x + 22, y + 136, () -> changeControls(() -> controls.reversePan = !controls.reversePan));
      toggle(c, "Scroll while placing", controls.placementScroll, x + 22, y + 168, () -> changeControls(() -> controls.placementScroll = !controls.placementScroll));
      toggle(c, "Minimap hold issues orders", controls.minimapOrders, x + 22, y + 200, () -> changeControls(() -> controls.minimapOrders = !controls.minimapOrders));
      controlOption(c, "Minimap order hold: " + controls.miniHoldMs + " ms", x, y + 232, () -> controls.miniHoldMs = controls.miniHoldMs >= 800 ? 250 : controls.miniHoldMs + 50);
      hud.small.draw(c, "Two fingers always pan, even with zoom off.", x + 22, y + 264, 324, 0, 2, 14);
    } else if (modal.equals("control-orders")) {
      toggle(c, "Disable ground-tap orders", !controls.groundOrders, x + 22, y + 40, () -> changeControls(() -> controls.groundOrders = !controls.groundOrders));
      controlOption(c, "Enabled army taps: " + (controls.attackTap ? "Attack move" : "Move"), x, y + 72, () -> controls.attackTap = !controls.attackTap);
      toggle(c, "Hover and hold descriptions", controls.tooltips, x + 22, y + 104, () -> changeControls(() -> controls.tooltips = !controls.tooltips));
      controlOption(c, "Description hold: " + controls.tooltipMs + " ms", x, y + 136, () -> controls.tooltipMs = controls.tooltipMs >= 800 ? 150 : controls.tooltipMs + 50);
      toggle(c, "Confirm building placement", controls.confirmBuild, x + 22, y + 168, () -> changeControls(() -> controls.confirmBuild = !controls.confirmBuild));
      toggle(c, "Build buttons follow placement", controls.placementButtonsFollow, x + 22, y + 200, () -> changeControls(() -> controls.placementButtonsFollow = !controls.placementButtonsFollow));
      controlOption(c, "Building finger offset: " + controls.placementOffset, x, y + 232, () -> controls.placementOffset = controls.placementOffset >= 96 ? 0 : controls.placementOffset + 16);
      toggle(c, "Show attack / move toggle", controls.commandModeButton, x + 22, y + 264, () -> changeControls(() -> controls.commandModeButton = !controls.commandModeButton));
    }
  }

  private String tipText() {
    if (tips.length == 0) {
      byte[] original = art.read("rez/tips.tbl");
      if (original != null) tips = NativeArt.tbl(original);
    }
    return tips.length == 0 ? "Build workers to gather minerals and Vespene Gas. Keep training units while you explore." : tips[tipIndex % tips.length];
  }

  private float briefScrollLimit(NativeArt.Widget box) {
    int height = glue.dialogue.lines(campaign.message, box.w).size() * 19;
    return Math.max(0, height - box.h);
  }

  private void loadAudioSettings() {
    muted = prefs.getBoolean("muted", false);
    effects = prefs.getFloat("effects", 1);
    music = prefs.getFloat("music", .65f);
    // Preserve the old off setting as zero volume once. After migration, raising
    // the slider must work without an invisible switch turning music off again.
    if (prefs.contains("music-enabled")) {
      if (!prefs.getBoolean("music-enabled", true)) music = 0;
      prefs.edit().putFloat("music", music).remove("music-enabled").apply();
    }
  }

  private void saveSettings() {
    syncAudio();
    prefs
        .edit()
        .putInt("fps", fps)
        .putInt("resource-percent", resourcePercent)
        .putInt("command-percent", commandPercent)
        .putInt("group-percent", groupPercent)
        .putBoolean("smooth-graphics", smoothGraphics)
        .putInt("description-percent", descriptionPercent)
        .putInt("touch-percent", touchPercent)
        .putFloat("frame-ms", (float) speed)
        .putBoolean("muted", muted)
        .remove("music-enabled")
        .putBoolean("order-lines", orderLines)
        .putBoolean("nearby-defenders", nearbyDefenders)
        .putBoolean("network-stats", networkStats)
        .remove("music-track")
        .putFloat("music", music)
        .putFloat("effects", effects)
        .putFloat("zoom", zoom)
        .putBoolean("wide", wide)
        .putBoolean("wide-over-camera", overCamera)
        .putBoolean("zoom-out", zoomOut)
        .apply();
  }

  private void resizeWorld() {
    cameraPan.reset();
    logicalWidth =
        wide
            ? Math.max(
                640, Math.min(1600, Math.round(physicalWidth * 480f / Math.max(1, physicalHeight))))
            : 640;
    int w = Math.max(320, Math.round(logicalWidth / zoom)),
        h = Math.max(160, Math.round(480 / zoom));
    NativeBridge.resize(w, h, zoom);
    syncControls();
    NativeBridge.command(21, Math.round((480 - NativeHud.WORLD_BOTTOM) / zoom), 0, 0, 0);
    world = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    accumulator = 0;
  }

  private void loadMission(JSONObject mission) {
    try {
      closeBriefMovies();
      if (hud != null) {
        hud.close();
        hud = null;
      }
      closeGlue();
      currentMission = mission;
      campaignProfile = campaignProgress.playerId(selectedProfile);
      missionCode = mission.getInt("code");
      missionRace = mission.getInt("race");
      expansion = mission.optString("game").equals("broodwar");
      File mpq =
          NativeData.prepare(
              ProjectFiles.existing(gameFiles, CampaignData.filename(mission.getString("id"))), cache, "mission");
      NativeBridge.open(3, mpq.getPath());
      releaseSavedMap(); activeMapSource = mpq;
      NativeBridge.init(640, 480, missionCode, missionRace);
      activeGame = true;
      resizeWorld();
      NativeBridge.frame(world, 0, clock);
      hud = new NativeHud(art, missionRace, commandMaps);
      campaign = new NativeCampaign(NativeBridge.chk(), mission.getInt("player"));
      lastOutcome = 0;
      nextScenario = "";
      errorSeq = -1;
      instant = false;
      music("briefing");
      loadStory(mission);
      if (story.isEmpty()) showBriefing();
      else {
        page = "story";
        logicalWidth = 640;
        modal = "";
        storyIndex = 0;
        storyTime = 0;
      }
    } catch (Exception e) {
      activeGame = false;
      error(e);
    }
  }

  private void showBriefing() {
    screen(
        "briefing",
        "glurdy" + new String[] {"z", "t", "p"}[missionRace],
        "palr" + new String[] {"z", "t", "p"}[missionRace],
        true);
    campaign.replay();
    lastBriefRevision = -1;
    sfx("swishin");
  }

  private void startMission() {
    closeBriefMovies();
    closeGlue();
    if (campaign != null) campaign.enterMission();
    hint = "";
    if (hud != null) {hud.transmissionType = -1; hud.transmissionTalk = false;}
    page = "game";
    startupTips = nearby==null && prefs.getBoolean("show-tips", true);
    modal = startupTips ? "tips" : "";
    resizeWorld();
    NativeBridge.command(19, orderLines ? 1 : 0, 0, 0, 0);
    NativeBridge.sound(null, 62, false);
    music("game");
    accumulator = 0;
    lastTime = 0;
  }

  private void drawBriefing(Canvas c) {
    if (glue == null) {
      text(c, campaign.message, 30, 100, 580, 0, 2);
      button(c, "Start Mission", 210, 380, 220, 40, this::startMission, true);
      return;
    }
    NativeArt.Widget textBox = glue.layout.get(65526), goals = glue.layout.get(65525);
    if (campaign.messageRevision != lastBriefRevision) {
      lastBriefRevision = campaign.messageRevision;
      transmissionStart = clock;
      transmissionLength = campaign.messageDuration;
      briefManual = -1;
      briefScroll = 0;
    }
    if (textBox != null) {
      c.save();
      c.translate(glue.shift(textBox), 0);
      c.clipRect(textBox.rect());
      List<String> lines = glue.dialogue.lines(campaign.message, textBox.w);
      double position = campaign.transmission ? campaign.transmissionPosition : clock - transmissionStart;
      double speech = campaign.transmission && campaign.spokenLength > 0 ? campaign.spokenLength : transmissionLength;
      float automatic = BriefingText.scroll(lines, position, speech, textBox.h);
      double elapsed = Math.max(0, Math.min(100, clock - briefDrawTime));
      briefDrawTime = clock;
      String shown = campaign.message;
      if (briefManual >= 0 && clock < briefManualUntil) briefScroll = briefManual;
      else {
        briefManual = -1;
        briefScroll += (automatic - briefScroll) * (float)(1 - Math.exp(-elapsed / 125));
      }
      glue.dialogue.draw(c, shown, textBox.x, textBox.y - briefScroll, textBox.w, 0, 2, 19);
      c.restore();
    }
    if (goals != null) {
      c.save();
      c.translate(glue.shift(goals), 0);
      c.clipRect(goals.rect());
      glue.small.draw(c, campaign.objectives, goals.x, goals.y, goals.w, 0, 2, 0);
      c.restore();
    }
    glue.label(c, 65535, null, true);
    for (int i = 0; i < 4; i++) {
      int type = campaign.portraits[i];
      boolean talk = campaign.talking == i && campaign.remaining > 0;
      if (briefMovies.get(i) == null)
        briefMovies.set(i, new NativePortrait(art, hud.portraitData, hud.portraitNames));
      NativeArt.Widget w = glue.layout.get(15 + i);
      NativePortrait m = briefMovies.get(i);
      m.target(type, talk);
      if (w != null && type >= 0) {
        float x = w.x + glue.shift(w);
        int layer = c.saveLayer(x, w.y, x + w.w, w.y + w.h, null);
        m.draw(c, x, w.y, w.w, w.h, clock, !modal.isEmpty(), true);
        if (glue.portraitMasks[i] != null) c.drawBitmap(glue.portraitMasks[i], x, w.y, NativeArt.bitmapPaint(glue.maskPaint));
        c.restoreToCount(layer);
        if (glue.portraitFrames[i] != null) c.drawBitmap(glue.portraitFrames[i], x, w.y, NativeArt.PIXELS);
        if (talk && glue.speakerFrames[i] != null) c.drawBitmap(glue.speakerFrames[i], x, w.y, NativeArt.PIXELS);
      }
    }
    glueButton(c, 13, null, () -> {glue.selectedStart = true; navigate(this::startMission);});
    glueButton(c, 14, null, () -> navigate(this::showCampaign));
    glueButton(
        c,
        20,
        null,
        () -> {
          campaign.replay();
          lastBriefRevision = -1;
        });
  }

  private int lastBriefRevision = -1;
  private double transmissionStart = 0, transmissionLength = 0;
  private float briefScroll = 0, briefManual = -1;
  private double briefManualUntil, briefDrawTime;

  private void loadStory(JSONObject mission) {
    story.clear();
    String opening = mission.optString("opening");
    if (opening.isEmpty()) return;
    int archive = expansion ? 8 : 7;
    try {
      byte[] data = NativeBridge.asset(archive, opening);
      if (data == null) return;
      String bg = "", tint = "", position = "left";
      int duration = 5000;
      StringBuilder lines = new StringBuilder();
      for (String line : GameLanguage.decode(data).replace("\r", "").split("\n")) {
        if (!line.startsWith("</")) {
          lines.append(line).append('\n');
          continue;
        }
        String tag = line.substring(2, line.length() - 1);
        int space = tag.indexOf(' ');
        String key = space < 0 ? tag : tag.substring(0, space),
            value = space < 0 ? "" : tag.substring(space + 1);
        if (key.equals("BACKGROUND")) bg = value;
        else if (key.equals("FONTCOLOR")) tint = value;
        else if (key.equals("DISPLAYTIME")) duration = Integer.parseInt(value);
        else if (key.startsWith("SCREEN")) position = key;
        else if (key.equals("PAGE")) {
          if (lines.toString().trim().length() > 0) {
            StoryPage p = new StoryPage();
            NativeArt.PCX image = new NativeArt.PCX(NativeBridge.asset(archive, bg), false, -1),
                color = new NativeArt.PCX(NativeBridge.asset(archive, tint), false, -1);
            p.background = image.image;
            // Keep the original glyph shading while using the requested green.
            p.font = art.font("16", color, image.palette, 1).withShadedColor(0xff30e050);
            p.text = lines.toString().trim();
            p.duration = Math.max(1000, Math.min(60000, duration));
            p.lower = position.equals("SCREENLOWERLEFT");
            story.add(p);
          }
          lines.setLength(0);
        }
      }
    } catch (Exception e) {
      story.clear();
      failure = "Optional mission opening: " + e.getMessage();
    }
  }

  private void drawStory(Canvas c) {
    if (storyIndex >= story.size()) return;
    StoryPage p = story.get(storyIndex);
    float alpha =
        (float) Math.min(1, Math.min(storyTime / 400, (p.duration + 800 - storyTime) / 400));
    int layer = c.saveLayerAlpha(0, 0, 640, 480, Math.max(0, Math.min(255, (int) (alpha * 255))));
    c.drawBitmap(p.background, 0, 0, NativeArt.PIXELS);
    if (p.layout == null) p.layout = StoryLayout.fit(p.font, p.text, p.lower);
    StoryLayout layout = p.layout;
    c.save(); c.translate(32, layout.top);
    c.scale(layout.scale, layout.scale);
    p.font.draw(c, p.text, 0, 0, layout.width, 0, 2, layout.lineHeight);
    c.restore();
    c.restoreToCount(layer);
    add(new RectF(0, 0, 640, 480), this::nextStory, true);
  }

  private void nextStory() {
    storyTime = 0;
    if (++storyIndex >= story.size()) {
      story.clear();
      showBriefing();
    }
  }

  private void showResults() {
    try {
      closeGlue(); closeBriefMovies();
      results = new NativeResults(art, missionRace, lastOutcome == 1, NativeBridge.text(9));
      results.playerName = selectedProfile;
      if(nearby!=null)for(int i=0;i<8;i++)if(nearby.occupants[i]>=0)results.slotNames[i]=nearby.names[i];
      music(lastOutcome == 1 ? "victory" : "defeat");
      page = "results";
      modal = "";
      logicalWidth = 640;
      pendingScreen = null; lastTime = 0; slideLocked = false; hoverButton = -1;
      sfx("swishin");
    } catch (Exception e) {
      error(e);
    }
  }

  private void nextMission() {
    if (lastOutcome == 1 && currentMission != null) {
      CinematicCatalog.Entry film = CinematicCatalog.after(currentMission.optInt("code"));
      if (film != null && playCinematic(film.file, film.expansion(), this::advanceMission, false)) return;
    }
    advanceMission();
  }

  private void advanceMission() {
    if (multiplayerGame) { showMain(); return; }
    if (lastOutcome == 1 && currentMission != null) {
      String next = campaign == null ? "" : campaign.next;
      String basename = next.substring(next.lastIndexOf('/') + 1).replaceAll("\\.(scm|scx)$", "");
      int code = currentMission.optInt("next");
      for (int i = 0; i < missions.length(); i++) {
        JSONObject m = missions.optJSONObject(i);
        String prefix = m.optString("prefix");
        String[] parts = prefix.split("/");
        if (m.optString("game").equals(currentMission.optString("game"))
            && !m.optString("id").equals(currentMission.optString("id"))
            && parts.length > 0
            && parts[parts.length - 1].equals(basename)) {
          code = m.optInt("code");
          break;
        }
      }
      for (int i = 0; i < missions.length(); i++) {
        JSONObject m = missions.optJSONObject(i);
        if (m.optInt("code") == code && installed(m)) {
          loadMission(m);
          return;
        }
      }
    }
    showCampaign();
  }

  private boolean importingMaps;
  void mapImportProgress(String status, boolean busy) {
    enqueue(() -> {mapImportStatus = status; importingMaps = busy;});
  }
  void mapsImported(NativeMaps.Imported result, String failure) {
    enqueue(() -> {
      importingMaps = false;
      if(result!=null) {
        customMaps = result.collection;
        if(result.first!=null && page.equals("custom")) {
          selectCustomMap(result.first);mapOffset=Math.min(mapMaxOffset(),Math.max(0,customMaps.indexOf(result.first))*22);
        }
        mapImportStatus = result.accepted + " maps imported" + (result.rejected>0?"; "+result.rejected+" rejected":"") + ".";
      } else mapImportStatus = "";
      if (failure!=null && !failure.isEmpty() || result!=null && result.accepted==0 && result.rejected>0) {
        hint = failure!=null && !failure.isEmpty()?failure:result.failure; modal = "map-error";
      }
    });
  }

  private NativeSaves saveStore() {return new NativeSaves(multiplayerSaves?new File(files,"multiplayer"):files);}

  private void releaseSavedMap() {
    if (activeMapSource!=null && activeMapSource.getName().startsWith("saved-map-")) activeMapSource.delete();
    activeMapSource=null;
  }

  private void openSaves(boolean writing) {
    if(replayGame)return;
    if(nearby!=null&&!nearby.host)return;
    if(page.equals("game"))multiplayerSaves=nearby!=null;
    cancelGesture();saveMode=writing?"save":"load";saveMessage="";
    for(int i=0;i<savedSlots.length;i++) {
      savedSlots[i]=null;savedSlotErrors[i]="";
      try {savedSlots[i]=saveStore().info(i);}
      catch(FileNotFoundException empty) {}
      catch(Exception e) {savedSlotErrors[i]="Unreadable save";}
    }
    modal=saveMode;
  }

  private boolean drawSaves(Canvas c) {
    if(!modal.equals("save")&&!modal.equals("load")&&!modal.startsWith("save-"))return false;
    paint.setColor(0x80000000);c.drawRect(0,0,logicalWidth,480,paint);
    float x=(logicalWidth-440)/2f;panel(c,x,32,440,416);
    text(c,saveMode.equals("save")?"Save Game":"Load Game",x+16,46,408,1,2);
    if(modal.equals("save-wait")){body(c,saveMessage,x+24,110,392,220);return true;}
    if(modal.equals("save-done")) {
      body(c,saveMessage,x+24,110,392,220);
      button(c,"Resume Game",x+112,388,216,32,()->modal="",true);return true;
    }
    if(modal.equals("save-error")) {
      body(c,saveMessage,x+24,110,392,220);
      button(c,"Back",x+112,388,216,32,()->modal=saveMode,true);return true;
    }
    if(modal.equals("save-confirm")) {
      body(c,saveMode.equals("save")?"Replace the game in Slot "+(pendingSaveSlot+1)+"?":"Load Slot "+(pendingSaveSlot+1)+"?\nUnsaved progress in the current game will be lost.",x+24,110,392,190);
      button(c,saveMode.equals("save")?"Replace Save":"Load Game",x+24,340,184,36,()-> {if(saveMode.equals("save"))saveSlot(pendingSaveSlot);else loadSlot(pendingSaveSlot);},true);
      button(c,"Cancel",x+232,340,184,36,()->modal=saveMode,true);return true;
    }
    for(int i=0;i<savedSlots.length;i++) {
      final int slot=i;JSONObject info=savedSlots[i];float y=84+i*48;
      String title="Slot "+(i+1)+" — "+(info==null?(savedSlotErrors[i].isEmpty()?"Empty":savedSlotErrors[i]):info.optString("name","Saved game"));
      if(title.length()>48)title=title.substring(0,45)+"...";
      boolean enabled=saveMode.equals("save")||info!=null;
      paint.setColor(enabled?0xff142540:0xff111820);c.drawRect(x+20,y,x+420,y+43,paint);
      text(c,title,x+28,y+4,384,0,enabled?2:4);
      if(info!=null) {
        long seconds=info.optInt("frame")*42L/1000;
        String date=new java.text.SimpleDateFormat("MMM d, HH:mm",Locale.getDefault()).format(new Date(info.optLong("created")));
        text(c,date+"   •   "+seconds/60+":"+String.format(Locale.ROOT,"%02d",seconds%60),x+28,y+24,384,0,4);
      }
      add(new RectF(x+20,y,x+420,y+43),()-> {
        pendingSaveSlot=slot;
        if((saveMode.equals("save")&&(savedSlots[slot]!=null||!savedSlotErrors[slot].isEmpty()))||(!saveMode.equals("save")&&activeGame))modal="save-confirm";
        else if(saveMode.equals("save"))saveSlot(slot);else loadSlot(slot);
      },enabled);
    }
    button(c,"Cancel",x+112,388,216,32,this::modalBack,true);return true;
  }

  private void saveSlot(int slot) {
    try {
      if(!activeGame||!page.equals("game"))throw new IOException("Start a game before saving.");
      if(nearby!=null) {
        modal="save-wait";saveMessage="Saving game…";
        nearby.save(bytes->{try {
          JSONObject details=new JSONObject();details.put("name",customName);details.put("missionCode",0);details.put("race",missionRace);details.put("frame",NativeBridge.value(8,0));details.put("expansion",expansion);details.put("multiplayer",true);details.put("setup",nearby.savedSetup().json());
          saveStore().write(slot,details,bytes,activeMapSource);saveStore().info(slot);
          saveMessage="Multiplayer game saved in Slot "+(slot+1)+". The host can load it here or from Multiplayer.";modal="save-done";
        }catch(Exception e){saveMessage="Could not save: "+e.getMessage();modal="save-error";}});return;
      }
      JSONObject info=new JSONObject();info.put("name",currentMission!=null?currentMission.optString("title","Campaign"):customName);
      info.put("missionCode",missionCode);info.put("race",missionRace);info.put("frame",NativeBridge.value(8,0));
      info.put("expansion",expansion);info.put("skirmishExpansion",expansion);info.put("botSlot",customBotSlot);info.put("opponentRace",opponentRace);info.put("originalTerranAi",opponentRace==1);info.put("terranAiLevel",terranAiLevel);info.put("profile",selectedProfile);
      if(currentMission!=null)info.put("campaignProfile",campaignProfile);
      info.put("speed",speed);info.put("zoom",zoom);info.put("instant",instant);
      if(missionCode==0)info.put("setup",customSetup.json());
      if(currentMission!=null)info.put("mission",currentMission);
      if(campaign!=null)info.put("dialogue",campaign.save());
      saveStore().write(slot,info,NativeBridge.saveState(),activeMapSource);
      // Recover/read back the committed manifest before reporting success.
      saveStore().info(slot);saveMessage="Game saved in Slot "+(slot+1)+".";modal="save-done";hint="";
    } catch(Exception e) {saveMessage="Could not save: "+e.getMessage();modal="save-error";}
  }

  private void loadSlot(int slot) {
    NativeSaves.Loaded saved=null;boolean replaced=false;
    try {
      // Validate hashes and extract before replacing the current game.
      saved=saveStore().read(slot,cache);JSONObject info=saved.info;
      if(multiplayerSaves) {
        if(!info.optBoolean("multiplayer"))throw new IOException("Not a multiplayer save");
        RoomSnapshot.decode(saved.state);MatchSetup settings=MatchSetup.read(info.getJSONObject("setup"),true);
        NativeBridge.open(3,saved.map.getPath());byte[] chk=NativeBridge.asset(3,"staredit/scenario.chk");
        boolean bw=info.optBoolean("expansion",true);nearbyResuming=true;
        if(nearby!=null&&nearby.host&&!nearby.closed){nearby.reload(chk,info.optString("name","Saved game"),bw,settings,saved.state);}
        else {
          nearbySetup=settings;nearbyResume=saved.state;customRace=settings.races[0];nearbyExpansion=bw;
          customMap=NativeData.nearbyMap(chk,cache);inspectMap(customMap);showNearbyLobby();openNearby(true);
        }
        saved.close();return;
      }
      if(info.optInt("missionCode",0)==0 && info.optInt("botSlot",1)>0 && !info.has("opponentRace") && !info.optBoolean("originalTerranAi",false))
        throw new IOException("This save uses the removed Zerg skirmish AI (legacy external bot). Start a new skirmish; current native AI saves still work.");
      closeBriefMovies();closeGlue();if(hud!=null){hud.close();hud=null;}
      missionCode=info.getInt("missionCode");missionRace=info.getInt("race");expansion=missionCode==0 ? info.optBoolean("skirmishExpansion",true) : info.optBoolean("expansion");
      currentMission=info.optJSONObject("mission");customBotSlot=info.optInt("botSlot",1);opponentRace=Math.max(0,Math.min(2,info.optInt("opponentRace",1)));terranAiLevel=Math.max(0,Math.min(2,info.optInt("terranAiLevel",0)));
      NativeBridge.open(3,saved.map.getPath());replaced=true;
      if(missionCode==0&&info.has("setup")) {
        customSetup=MatchSetup.read(info.getJSONObject("setup"),false);NativeBridge.configuredInit(0,customSetup.races,customSetup.controllers,customSetup.difficulty,expansion,customSetup.freeForAll,false,customSetup.teams,customSetup.teamGame,customSetup.heroMode);
      }else {
        if(missionCode==0){customSetup=new MatchSetup(false);Arrays.fill(customSetup.controllers,0);customSetup.controllers[0]=1;customSetup.races[0]=missionRace;customSetup.starts=Math.max(1,customBotSlot+1);customSetup.difficulty=terranAiLevel;
          if(customBotSlot>0){customSetup.controllers[customBotSlot]=2;customSetup.races[customBotSlot]=opponentRace;}}
        NativeBridge.command(20,customBotSlot,opponentRace,terranAiLevel,expansion?2:1);NativeBridge.init(640,480,missionCode,missionRace);
      }
      NativeBridge.restoreState(saved.state);
      campaignProfile=campaignProgress.savedOwner(info);
      releaseSavedMap();activeMapSource=saved.map;
      campaign=missionCode>0?new NativeCampaign(NativeBridge.chk(),currentMission.getInt("player")):null;
      if(campaign!=null)campaign.restore(info.getJSONObject("dialogue"));
      customName=info.optString("name","Custom Game");instant=info.optBoolean("instant");
      speed=Math.max(2.625,Math.min(167,info.optDouble("speed",42)));zoom=(float)Math.max(zoomOut?.5:1,Math.min(2,info.optDouble("zoom",1)));
      hud=new NativeHud(art,missionRace,commandMaps);activeGame=true;page="game";modal="menu";startupTips=false;
      lastOutcome=NativeBridge.value(7,0);nextScenario="";errorSeq=-1;pendingScreen=null;hint="Game loaded";hintUntil=clock+4000;
      resizeWorld();NativeBridge.command(6,1,0,0,0);NativeBridge.command(19,orderLines?1:0,0,0,0);
      NativeBridge.frame(world,0,clock);music("game");accumulator=0;lastTime=0;
      if(campaign!=null) {
        String owner=campaignProgress.playerName(campaignProfile);
        if(!owner.isEmpty()&&!owner.equals(selectedProfile)){selectedProfile=owner;saveProfiles();}
      }
      recordCampaignCompletion(lastOutcome);
    } catch(Exception e) {
      if(saved!=null)saved.close();
      if(replaced){NativeBridge.endGame();activeGame=false;page="main";logicalWidth=640;campaign=null;releaseSavedMap();}
      saveMessage="Could not load: "+e.getMessage();modal="save-error";
    }
  }

  private void startSkirmish(int race, File map) {
    replayGame=false;
    try {
      if (map == null) {
        map = new File(cache, "weave.scx");
        if (!map.exists())
          try (InputStream in = activity.getAssets().open("native/weave.scx");
              FileOutputStream out = new FileOutputStream(map)) {
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
          }
      }
      closeGlue();
      closeBriefMovies();
      if (hud != null) hud.close();
      missionCode = 0;
      missionRace = race;
      currentMission = null;
      campaign = null;
      NativeBridge.open(3, map.getPath());
      releaseSavedMap(); activeMapSource = map;
      customSetup.races[0]=race;
      NativeBridge.configuredInit(0,customSetup.races,customSetup.controllers,customSetup.difficulty,expansion,customSetup.freeForAll,false,customSetup.teams,customSetup.teamGame,customSetup.heroMode);
      speed=customSetup.frameMs();multiplayerGame=false;multiplayerSaves=false;multiplayerSetup=false;
      activeGame = true;
      resizeWorld();
      NativeBridge.frame(world, 0, clock);
      hud = new NativeHud(art, race, commandMaps);
      lastOutcome = 0; commandError = ""; errorSeq = -1;
      instant = false;
      startMission();
    } catch (Exception e) {
      error(e);
    }
  }

  // Gesture decisions run on the same worker as the engine, eliminating input races.
  private float lastTapX, lastTapY, downX, downY, lastX, lastY, pinchDistance, pinchZoom;
  private long downAt, lastTapAt;
  private String gesture = "";
  private Button pressed;

  private boolean targeting;
  private boolean placement = false;
  private int placeX, placeY;
  private RectF placementButtonRow;
  private final CameraPan cameraPan = new CameraPan();

  private float physicalScale() {
    return Math.min(physicalWidth / (float) logicalWidth, physicalHeight / 480f);
  }

  private float lx(float x) {
    float s = Math.min(physicalWidth / (float) logicalWidth, physicalHeight / 480f);
    return (x - (physicalWidth - logicalWidth * s) / 2) / s;
  }

  private float ly(float y) {
    float s = Math.min(physicalWidth / (float) logicalWidth, physicalHeight / 480f);
    return (y - (physicalHeight - 480 * s) / 2) / s;
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if(handleDesktopMotion(event))return true;
    MotionEvent copy = MotionEvent.obtain(event);
    enqueue(
        () -> {
          try {
            if(copy.getActionMasked()==MotionEvent.ACTION_DOWN){resetDesktop(true);resetGamepad();}
            touch(copy);
          } finally {
            copy.recycle();
          }
        });
    return true;
  }

  private final CommandHover commandHover = new CommandHover();
  private int hoverButton = -1;

  @Override
  public boolean onHoverEvent(MotionEvent event) {
    if(handleDesktopMotion(event))return true;
    final float x = event.getX(), y = event.getY();
    final boolean leave = event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT;
    enqueue(() -> updateHover(lx(x), ly(y), leave));
    return true;
  }

  private void updateHover(float x, float y, boolean leave) {
    menuHoverX=leave?-1:x;menuHoverY=leave?-1:y;
    commandHover.hover(x, y, leave || !page.equals("game") || !modal.isEmpty() || hud == null || placementButtonAt(x, y));
    int hot = -1;
    if (!leave && modal.isEmpty() && pendingScreen == null && glue != null)
      for (NativeArt.Widget w : glue.layout.widgets) if (w.type == 14) {
        RectF r = w.hit(); r.offset(glue.shift(w), 0);
        if (r.contains(x, y)) hot = w.id;
      }
    if (!leave && modal.isEmpty() && pendingScreen == null && page.equals("results") && results != null && results.ready())
      for (NativeArt.Widget w : results.layout.widgets)
        if ((w.id >= 3 && w.id <= 7 || w.id == 61 && results.canSave) && w.hit().contains(x,y)) hot = w.id;
    if (glue != null) glue.hot = hot;
    if(!leave && hot<0 && (modal.isEmpty()||modal.startsWith("profile-")) && pendingScreen==null && glue!=null && !glue.exiting && glue.elapsed>=NativeGlue.ENTER_MS)
      for(int i=0;i<buttons.size();i++)if(buttons.get(i).enabled && buttons.get(i).rect.contains(x,y)){hot=100000+i;break;}
    if (hot != hoverButton && hot >= 0 && !page.equals("game")) sfx("mouseover");
    hoverButton = hot;
  }

  private void touch(MotionEvent e) {
    int action = e.getActionMasked();
    float x = lx(e.getX()), y = ly(e.getY());
    long time = e.getEventTime();
    if (pendingScreen != null) return;
    if (action == MotionEvent.ACTION_CANCEL) {
      cancelGesture();
      if (glue != null) glue.hot = -1;
      if (page.equals("results")) hoverButton = -1;
      return;
    }
    if (blockPlacementTouch(action, x, y)) return;
    if (action == MotionEvent.ACTION_POINTER_DOWN
        && e.getPointerCount() > 1
        && page.equals("game")
        && modal.isEmpty()
        && Arrays.asList("tap", "pan", "box", "place", "aim").contains(gesture)) {
      cameraPan.reset();
      gesture = "pinch";
      oneShotSelect = false;
      pinchDistance = (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
      pinchZoom = zoom;
      lastX = lx((e.getX(0) + e.getX(1)) / 2);
      lastY = ly((e.getY(0) + e.getY(1)) / 2);
      NativeBridge.command(5, 0, 0, 0, 0);
      placement = false;
      return;
    }
    if (gesture.equals("pinch")) {
      if (action == MotionEvent.ACTION_MOVE && e.getPointerCount() > 1) {
        float d = (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
        float midX = lx((e.getX(0) + e.getX(1)) / 2), midY = ly((e.getY(0) + e.getY(1)) / 2);
        panCamera((lastX - midX) * controls.panFactor() / zoom, (lastY - midY) * controls.panFactor() / zoom);
        lastX = midX; lastY = midY;
        float z =
            Math.max(zoomOut ? .5f : 1f, Math.min(3, (float) (pinchZoom * Math.pow(d / Math.max(1, pinchDistance), controls.pinchSpeed))));
        if (controls.pinchZoom && Math.abs(z - zoom) > .01) {
          zoom = z;
          resizeWorld();
        }
      }
      if (action == MotionEvent.ACTION_UP) {
        gesture = "";
        saveSettings();
      }
      return;
    }
    if (action == MotionEvent.ACTION_DOWN) {
      cameraPan.reset();
      commandHover.cancel();
      downX = lastX = x;
      downY = lastY = y;
      downAt = time;
      scrollingList = modal.equals("missions") && missionViewport.contains(x, y) ? "missions"
          : modal.isEmpty() && page.equals("custom") && mapViewport.contains(x,y) ? "maps"
          : modal.isEmpty() && page.equals("registry") && profileViewport.contains(x, y) ? "profiles" : "";
      listVelocity = 0;
      listStart = scrollingList.equals("missions") ? missionOffset : scrollingList.equals("maps") ? mapOffset : profileOffset;
      listMoveTime = time;
      pressed = null;
      if ((glue != null || page.equals("results")) && modal.isEmpty()) updateHover(x, y, false);
      for (int i = buttons.size() - 1; i >= 0; i--) {
        Button b = buttons.get(i);
        if (b.rect.contains(x, y)) {
          pressed = b;
          break;
        }
      }
      if (pressed != null) {
        if (pressed.slider != null) pressed.slider.accept(Math.max(0, Math.min(1, (pressed.vertical?(y-pressed.rect.top)/pressed.rect.height():(x-pressed.rect.left)/pressed.rect.width()))));
        gesture = "button";
        return;
      }
      if (page.equals("briefing") && modal.isEmpty() && glue != null) {
        NativeArt.Widget box = glue.layout.get(65526);
        if (box != null && box.rect().contains(x, y)) {
          gesture = "brief";
          return;
        }
      }
      if (!page.equals("game") || !modal.isEmpty()) {
        gesture = "list";
        return;
      }
      if (hud.minimap.contains(x, y)) {
        gesture = "mini";
        mini(x, y, false);
        return;
      }
      NativeHud.Hit hit = hud.hit(x, y);
      if (hit != null) {
        commandHover.begin(hit, x, y, time);
        gesture = "command";
        return;
      }
      if (y >= NativeHud.WORLD_BOTTOM) {
        gesture = "";
        return;
      }
      if (NativeBridge.value(0, 0) == 2) {
        gesture = "place";
        aim(x, y);
        return;
      }
      targeting = !oneShotSelect && NativeBridge.value(17, ((int) (x / zoom) << 16) | ((int) (y / zoom) & 65535)) == 2;
      gesture = "tap";
    } else if (action == MotionEvent.ACTION_MOVE) {
      float distance = (float) Math.hypot(x - downX, y - downY);
      if (gesture.equals("brief") && glue != null) {
        NativeArt.Widget box = glue.layout.get(65526);
        float limit = briefScrollLimit(box);
        if (distance >= 6) {
          briefManual = Math.max(0, Math.min(limit, briefScroll + lastY - y));
          briefScroll = briefManual;
          briefManualUntil = clock + 2500;
        }
        lastY = y;
        return;
      }
      if (gesture.equals("button")) {
        if (pressed != null && pressed.slider != null) {
          pressed.slider.accept(Math.max(0, Math.min(1, (pressed.vertical?(y-pressed.rect.top)/pressed.rect.height():(x-pressed.rect.left)/pressed.rect.width()))));
          return;
        }
        // Holding a small group button must tolerate normal finger jitter.
        // The previous fixed ten-physical-pixel cutoff silently discarded it
        // even while the finger was still inside the button.
        if (pressed!=null && (pressed.controlGroup ? !pressed.rect.contains(x,y) : distance > 10 / physicalScale())) {
          pressed = null;
          if (!scrollingList.isEmpty()) gesture = "list";
        }
        if (!gesture.equals("list")) return;
      }
      if (gesture.equals("command")) {
        commandHover.move(x, y, time, 10 / physicalScale());
        return;
      }
      if (gesture.equals("mini")) {
        mini(x, y, false);
        return;
      }
      if (gesture.equals("place")) {
        aim(x, y);
        return;
      }
      if (gesture.equals("list") && !scrollingList.isEmpty()) {
        setListOffset(listStart + downY - y);
        listVelocity = .3f * listVelocity + .7f * Math.max(-.75f, Math.min(.75f, (lastY - y) / Math.max(8, time - listMoveTime)));
        lastY = y;
        listMoveTime = time;
        return;
      }
      if (gesture.equals("tap") && distance > 10 / physicalScale()) {
        gesture = oneShotSelect ? "box" : targeting ? "aim" : controls.dragGesture(time - downAt);
        if (gesture.equals("box")) {
          NativeBridge.command(0, (int) (downX / zoom), (int) (downY / zoom), 0, 0);
          NativeBridge.command(1, 1, 1, (int) (downX / zoom), (int) (downY / zoom));
        }
      }
      if (gesture.equals("box") || gesture.equals("aim")) NativeBridge.command(0, (int) (x / zoom), (int) (y / zoom), 0, 0);
      else if (gesture.equals("pan"))
        panCamera((lastX - x) * controls.panFactor() / zoom, (lastY - y) * controls.panFactor() / zoom);
      lastX = x;
      lastY = y;
    } else if (action == MotionEvent.ACTION_UP) {
      String mode = gesture;
      gesture = "";
      if (glue != null) glue.hot = -1;
      if (page.equals("results")) hoverButton = -1;
      hoverButton = -1;
      if (mode.equals("list")) {
        if (time - listMoveTime > 80) listVelocity = 0;
        return;
      }
      if (mode.equals("brief")) {
        briefManualUntil = clock + 2500;
        return;
      }
      if (mode.equals("button")) {
        Button b = pressed;
        pressed = null;
        if (b != null && b.placementButton && !placementButtonAt(x, y)) return;
        if (b != null && b.slider != null) return;
        if (b != null && b.enabled && b.rect.contains(x, y)) {
          if (!page.equals("story") && !page.equals("movie"))
            sfx(page.equals("game") ? "button" : "mousedown2");
          if (b.longAction != null && time - downAt >= 450) b.longAction.run();
          else b.action.run();
        }
        return;
      }
      if (mode.equals("command")) {
        NativeHud.Hit h = commandHover.release(hud.hits, x, y, time);
        if (h != null && h.rect.contains(x, y)) {
          if (h.terrainToggle) { sfx("button"); NativeBridge.command(8, NativeBridge.value(14, 0) == 0 ? 1 : 0, 0, 0, 0); return; }
          if (h.unload >= 0) { NativeBridge.command(24, h.unload, 0, 0, 0); return; }
          if (h.more) {
            hud.page++;
            return;
          }
          if (h.help != null) {
            hint = h.help;
            hintUntil = clock + 3600;
            return;
          }
          if (h.selectUnit >= 0) {
            NativeBridge.command(14, h.selectUnit, 0, 0, 0);
            return;
          }
          sfx("button");
          if (h.cancel >= 0) NativeBridge.command(7, h.cancel, 0, 0, 0);
          else if (h.fields != null) {
            String[] f = h.fields;
            hud.gridHotkeys = desktopSettings.gridHotkeys;
            hint = f[2].equals("0") ? hud.hint(h) : "";
            hintSupplyRace = NativeHud.number(f, 15, hud.race);
            hintUntil = clock + 2400;
            { // Let the engine explain blocked commands, including energy/resources.
              int key = f[0].charAt(0);
              NativeBridge.command(2, key, key == 27 ? 41 : 0, 0, 0);
              // Apply the command's cursor state before another touch can arrive.
              NativeBridge.frame(world, 0, clock);
            }
          }
        }
        return;
      }
      if (mode.equals("place")) {
        aim(x, y);
        if (!controls.confirmBuild) placeBuilding();
        return;
      }
      if (mode.equals("mini")) {
        mini(x, y, controls.minimapOrders && time - downAt >= controls.miniHoldMs);
        return;
      }
      if (mode.equals("box")) NativeBridge.command(1, 0, 1, (int) (x / zoom), (int) (y / zoom));
      else if (mode.equals("tap") || mode.equals("aim")) {
        NativeBridge.command(
            4,
            (int) (x / zoom),
            (int) (y / zoom),
            controls.doubleTap && lastTapAt != 0 && time - lastTapAt < controls.doubleTapMs && Math.hypot(x - lastTapX, y - lastTapY) < 20 / physicalScale() ? 1 : 0,
            oneShotSelect || (controls.holdSelect && !targeting && time - downAt >= controls.holdMs) ? 1 : 0);
        lastTapAt = time;
        lastTapX = x;
        lastTapY = y;
      }
      if (mode.equals("box") || mode.equals("tap")) oneShotSelect = false;
      if (activeGame) NativeBridge.command(0, -100, -100, 0, 0);
    }
  }

  private void setListOffset(float value) {
    if (scrollingList.equals("missions")) missionOffset = Math.max(0, Math.min(missionMaxOffset(), value));
    else if (scrollingList.equals("maps")) mapOffset=Math.max(0,Math.min(mapMaxOffset(),value));
    else if (scrollingList.equals("profiles")) profileOffset = Math.max(0, Math.min(Math.max(0, profiles.size() - 9) * 23, value));
  }

  private void advanceLists(double dt) {
    if (gesture.equals("list") || scrollingList.isEmpty() || Math.abs(listVelocity) < .012f) return;
    if (!(scrollingList.equals("missions") && modal.equals("missions"))
        && !(scrollingList.equals("maps") && page.equals("custom") && modal.isEmpty())
        && !(scrollingList.equals("profiles") && page.equals("registry") && modal.isEmpty())) {listVelocity = 0; return;}
    float before = scrollingList.equals("missions") ? missionOffset : scrollingList.equals("maps") ? mapOffset : profileOffset;
    float decay = (float)Math.exp(-dt / 180);
    setListOffset(before + listVelocity * 180 * (1 - decay));
    float after = scrollingList.equals("missions") ? missionOffset : scrollingList.equals("maps") ? mapOffset : profileOffset;
    listVelocity = after == before ? 0 : listVelocity * decay;
  }

  private void placeBuilding() {
    NativeBridge.command(0, placeX, placeY, 0, 0);
    NativeBridge.command(1, 1, 1, placeX, placeY);
    NativeBridge.command(1, 0, 1, placeX, placeY);
  }

  private void drawPlacementButtons(Canvas c) {
    RectF row = new RectF(logicalWidth / 2f - 107, 268, logicalWidth / 2f + 107, 296);
    if (controls.placementButtonsFollow)
      row = PlacementButtons.position(logicalWidth, placeX * zoom, placeY * zoom, zoom);
    placementButtonRow = row;
    paint.setColor(0xee040e26);
    c.drawRoundRect(row, 3, 3, paint);
    // The entire row owns its touch area, including the gap between the buttons.
    add(row, () -> {}, false);
    buttons.get(buttons.size() - 1).placementButton = true;
    button(c, "Build", row.left, row.top, PlacementButtons.BUTTON, PlacementButtons.HEIGHT, this::placeBuilding, true);
    buttons.get(buttons.size() - 1).placementButton = true;
    button(c, "Cancel", row.left + PlacementButtons.BUTTON + PlacementButtons.GAP, row.top,
        PlacementButtons.BUTTON, PlacementButtons.HEIGHT, () -> {
          NativeBridge.command(2, 27, 41, 0, 0);
          placement = false;
        }, true);
    buttons.get(buttons.size() - 1).placementButton = true;
  }

  private boolean placementButtonAt(float x, float y) {
    return placement && page.equals("game") && modal.isEmpty()
        && placementButtonRow != null && placementButtonRow.contains(x, y);
  }

  private boolean blockPlacementTouch(int action, float x, float y) {
    if (gesture.equals("placement-blocked")) {
      if (action == MotionEvent.ACTION_UP) gesture = "";
      return true;
    }
    // A placement drag keeps its target; only a fresh touch can press Build/Cancel.
    if (action == MotionEvent.ACTION_DOWN || gesture.equals("place")
        || gesture.equals("button") && pressed != null && pressed.placementButton
        || !placementButtonAt(x, y)) return false;
    // A gesture starting on an exposed control cannot activate it by ending
    // beneath this row. Keep consuming it even if the pointer leaves the row.
    if (gesture.equals("box")) NativeBridge.command(5, 0, 0, 0, 0);
    commandHover.cancel();
    pressed = null;
    gesture = action == MotionEvent.ACTION_UP ? "" : "placement-blocked";
    return true;
  }

  private float cameraBottom(float x) {
    float bottom = NativeHud.WORLD_BOTTOM;
    float scale = commandPercent / 100f;
    if (x >= logicalWidth - 144 * scale) bottom = Math.min(bottom, 480 - 126 * scale);
    if (hud != null && x >= hud.minimap.left && x <= hud.minimap.right)
      bottom = Math.min(bottom, hud.minimap.top);
    return bottom;
  }

  private void panCamera(double x, double y) {
    cameraPan.drag(x, y);
    if (cameraPan.dx != 0 || cameraPan.dy != 0)
      NativeBridge.command(3, cameraPan.dx, cameraPan.dy, 0, 0);
  }

  private void advanceCamera(double dt) {
    boolean dragging = gesture.equals("place") && placement && controls.placementScroll && NativeBridge.value(0, 0) == 2
        || gesture.equals("box") || gesture.equals("aim");
    if (!activeGame || !page.equals("game") || !modal.isEmpty() || pendingScreen != null || !dragging) {
      cameraPan.stopEdge();
      return;
    }
    // Use the finger, not its offset preview: holding beneath the HUD keeps
    // scrolling while the preview stays in the visible world.
    cameraPan.edge(dt, lastX, lastY, logicalWidth, cameraBottom(lastX), zoom, controls.panSpeed);
    if (cameraPan.dx != 0 || cameraPan.dy != 0)
      NativeBridge.command(3, cameraPan.dx, cameraPan.dy, 0, 0);
  }

  private void aim(float x, float y) {
    placement = true;
    lastX = x; lastY = y;
    placeX = Math.max(0, Math.min(Math.round(logicalWidth / zoom) - 1, Math.round(x / zoom)));
    int bottom = Math.max(1, Math.round(cameraBottom(x) / zoom) - 1);
    placeY = Math.max(1, Math.min(bottom, Math.round((y - controls.placementOffset / physicalScale()) / zoom)));
    NativeBridge.command(0, placeX, placeY, 0, 0);
  }

  private void mini(float x, float y, boolean right) {
    Rect s = hud.miniSource;
    RectF r = hud.minimap;
    int px = (int) (s.left + (x - r.left) / r.width() * s.width()),
        py = (int) (s.top + (y - r.top) / r.height() * s.height());
    px = Math.max(s.left, Math.min(s.right - 1, px));
    py = Math.max(s.top, Math.min(s.bottom - 1, py));
    NativeBridge.command(0, px, py, 0, 0);
    NativeBridge.command(1, 1, right ? 3 : 1, px, py);
    NativeBridge.command(1, 0, right ? 3 : 1, px, py);
    NativeBridge.command(0, -100, -100, 0, 0);
  }

  void back() {
    enqueue(
        () -> {
          if(profileEditing||modal.startsWith("profile-")){cancelProfileDialog();return;}
          if(chatEditing){closeChat();return;}
          if (pendingScreen != null) return;
          if (!modal.isEmpty()) {
            modalBack();
            return;
          }
          if (page.equals("game")) {
            String card = NativeBridge.text(0);
            cancelGesture();
            if (NativeBridge.value(0, 0) > 0 || card.startsWith("Build\t") || card.startsWith("Advanced Build\t")) {
              NativeBridge.command(2, 27, 41, 0, 0);
              placement = false;
            } else openMenu();
          } else if (page.equals("briefing")) navigate(this::showCampaign);
          else if (page.equals("results")) navigate(this::nextMission);
          else if (page.equals("movie")) finishCinematic();
          else if (page.equals("story")) nextStory();
          else if (page.equals("title")) showMain();
          else if (page.equals("campaign")) navigate(this::showRegistry);
          else if (page.equals("registry")) navigate(this::showMain);
          else if (page.equals("replays")) navigate(this::showCampaign);
          else if (page.equals("custom")) navigate(multiplayerSetup?this::showNearby:this::showCampaign);
          else if (page.equals("nearby")) navigate(this::showMain);
          else activity.runOnUiThread(activity::requestAppOptions);
        });
  }

  private void openChat(){
    if(nearby==null||nearby.closed)return;chatEditing=true;
    activity.runOnUiThread(()->showTextKeyboard(chatInput));
  }
  private void showTextKeyboard(android.text.SpannableStringBuilder input){
    setFocusableInTouchMode(true);requestFocus();android.text.Selection.setSelection(input,input.length());android.view.inputmethod.InputMethodManager ime=activity.getSystemService(android.view.inputmethod.InputMethodManager.class);if(ime!=null){ime.restartInput(this);ime.showSoftInput(this,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);}
  }
  private void closeChat(){chatEditing=false;activity.runOnUiThread(()->{android.view.inputmethod.InputMethodManager ime=activity.getSystemService(android.view.inputmethod.InputMethodManager.class);if(ime!=null)ime.hideSoftInputFromWindow(getWindowToken(),0);});}
  private void publishChatInput(){String text=RoomProtocol.chat(chatInput.toString());enqueue(()->chatDraft=text);}
  private void sendChat(){if(nearby!=null&&!chatDraft.trim().isEmpty()){nearby.say(chatDraft);chatDraft="";activity.runOnUiThread(()->{chatInput.clear();android.view.inputmethod.InputMethodManager ime=activity.getSystemService(android.view.inputmethod.InputMethodManager.class);if(ime!=null)ime.restartInput(this);});}}
  @Override public boolean onCheckIsTextEditor(){return chatEditing||profileEditing;}
  @Override public android.view.inputmethod.InputConnection onCreateInputConnection(android.view.inputmethod.EditorInfo info){
    if(!chatEditing&&!profileEditing)return null;final boolean nameField=profileEditing;final android.text.SpannableStringBuilder input=nameField?profileInput:chatInput;info.inputType=android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    info.imeOptions=(nameField?android.view.inputmethod.EditorInfo.IME_ACTION_DONE:android.view.inputmethod.EditorInfo.IME_ACTION_SEND)|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI;
    info.initialSelStart=info.initialSelEnd=input.length();
    return new android.view.inputmethod.BaseInputConnection(this,true){
      @Override public android.text.Editable getEditable(){return input;}
      private void changed(){int max=nameField?24:180;if(input.length()>max)input.delete(max,input.length());if(nameField){String value=input.toString();enqueue(()->profileDraft=value);}else publishChatInput();}
      @Override public boolean commitText(CharSequence text,int position){boolean done=super.commitText(text,position);changed();return done;}
      @Override public boolean setComposingText(CharSequence text,int position){boolean done=super.setComposingText(text,position);changed();return done;}
      @Override public boolean deleteSurroundingText(int before,int after){boolean done=super.deleteSurroundingText(before,after);changed();return done;}
      @Override public boolean finishComposingText(){boolean done=super.finishComposingText();changed();return done;}
      @Override public boolean performEditorAction(int action){if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEND||action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){enqueue(nameField?NativeSurface.this::confirmProfileDialog:NativeSurface.this::sendChat);return true;}return super.performEditorAction(action);}
      @Override public boolean sendKeyEvent(KeyEvent event){if(event.getAction()==KeyEvent.ACTION_DOWN)return NativeSurface.this.onKeyDown(event.getKeyCode(),event);return true;}
    };
  }

  void resetGamepadInput() { enqueue(this::resetGamepad); }

  // Physical mouse and keyboard events use held native input, never touch tap-to-order.
  private final DesktopControls desktop = new DesktopControls();
  private int desktopMouseDevice=-1,desktopKeyboardDevice=-1,desktopNativeButtons,desktopClicks=1;
  private boolean desktopMouseActive,desktopInside,desktopShift,desktopCtrl,desktopTextEditing;
  private float desktopX,desktopY,desktopLastClickX,desktopLastClickY;
  private long desktopDownAt,desktopLastClickAt;
  private String desktopPrimary="",desktopPage="",desktopModal="";
  private NativeHud.Hit desktopHudHit;
  private String desktopReturnModal="";

  void resetDesktopInput(){enqueue(()->resetDesktop(true));}

  private void resetDesktop(boolean hidePointer){
    boolean hadDesktop=desktopMouseActive||desktopKeyboardDevice!=-1;
    boolean hadInput=desktop.releaseKeys().length>0||desktop.buttons!=0||desktopNativeButtons!=0||desktopShift||desktopCtrl;
    desktop.releaseButtons();
    if(desktopPrimary.equals("ui")){pressed=null;gesture="";scrollingList="";listVelocity=0;}
    desktopPrimary="";desktopHudHit=null;desktopNativeButtons=0;desktopShift=desktopCtrl=false;
    desktopKeyboardDevice=-1;desktopLastClickAt=0;
    if((hadInput||hidePointer&&hadDesktop)&&activeGame)NativeBridge.command(34,0,0,0,0);
    if(hidePointer){desktopMouseActive=desktopInside=false;desktopMouseDevice=-1;updateHover(0,0,true);}
    desktopPage=page;desktopModal=modal;desktopTextEditing=profileEditing||chatEditing;
  }

  private void syncDesktopModifiers(boolean shift,boolean ctrl){
    if(!activeGame||!page.equals("game")||!modal.isEmpty())return;
    if(desktopShift!=shift){NativeBridge.command(32,shift?1:0,0,225,0);desktopShift=shift;}
    if(desktopCtrl!=ctrl){NativeBridge.command(32,ctrl?1:0,0,224,0);desktopCtrl=ctrl;}
  }

  boolean handleDesktopMotion(MotionEvent event){
    if(!DesktopControls.mouseSource(event.getSource())||event.getToolType(0)==MotionEvent.TOOL_TYPE_STYLUS)return false;
    int action=event.getActionMasked();
    if(action!=MotionEvent.ACTION_DOWN&&action!=MotionEvent.ACTION_UP&&action!=MotionEvent.ACTION_MOVE
        &&action!=MotionEvent.ACTION_HOVER_MOVE&&action!=MotionEvent.ACTION_HOVER_ENTER&&action!=MotionEvent.ACTION_HOVER_EXIT
        &&action!=MotionEvent.ACTION_BUTTON_PRESS&&action!=MotionEvent.ACTION_BUTTON_RELEASE
        &&action!=MotionEvent.ACTION_SCROLL&&action!=MotionEvent.ACTION_CANCEL)return false;
    MotionEvent copy=MotionEvent.obtain(event);
    enqueue(()->{try{desktopMotion(copy);}finally{copy.recycle();}});return true;
  }

  private void desktopMotion(MotionEvent event){
    if(padActive)resetGamepad();
    if(desktopMouseDevice!=-1&&desktopMouseDevice!=event.getDeviceId())resetDesktop(true);
    if(!desktopPage.equals(page)||!desktopModal.equals(modal)||desktopTextEditing!=(profileEditing||chatEditing))resetDesktop(false);
    desktopMouseDevice=event.getDeviceId();desktopMouseActive=true;
    int action=event.getActionMasked();
    float oldX=desktopX,oldY=desktopY;
    desktopX=lx(event.getX());desktopY=ly(event.getY());
    desktopInside=desktopX>=0&&desktopX<logicalWidth&&desktopY>=0&&desktopY<480&&action!=MotionEvent.ACTION_HOVER_EXIT;
    if(action==MotionEvent.ACTION_CANCEL){resetDesktop(true);return;}
    updateHover(desktopX,desktopY,!desktopInside);
    if(action==MotionEvent.ACTION_HOVER_EXIT)return; // Also sent just before a mouse press.
    if(!profileEditing&&!chatEditing)syncDesktopModifiers(desktop.shift()||(event.getMetaState()&KeyEvent.META_SHIFT_ON)!=0,desktop.ctrl()||(event.getMetaState()&KeyEvent.META_CTRL_ON)!=0);
    if(action==MotionEvent.ACTION_SCROLL){desktopWheel(event.getAxisValue(MotionEvent.AXIS_VSCROLL));return;}
    int changed=desktop.updateButtons(action,event.getButtonState(),event.getActionButton());
    int released=changed&~desktop.buttons,pressedButtons=changed&desktop.buttons;
    desktopCursor();
    if((released&MotionEvent.BUTTON_PRIMARY)!=0)desktopPrimary(false,event.getEventTime());
    if((released&MotionEvent.BUTTON_SECONDARY)!=0&& (desktopNativeButtons&MotionEvent.BUTTON_SECONDARY)!=0){
      NativeBridge.command(33,0,3,1,0);desktopNativeButtons&=~MotionEvent.BUTTON_SECONDARY;
    }
    if((pressedButtons&MotionEvent.BUTTON_PRIMARY)!=0)desktopPrimary(true,event.getEventTime());
    if((pressedButtons&MotionEvent.BUTTON_SECONDARY)!=0)desktopSecondary();
    if(action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_HOVER_MOVE){
      if(desktopPrimary.equals("world")&&Math.hypot(desktopX-desktopLastClickX,desktopY-desktopLastClickY)>6)desktopLastClickAt=0;
      if(desktopPrimary.equals("ui"))desktopTouch(MotionEvent.ACTION_MOVE,event.getEventTime());
      else if(desktopPrimary.equals("mini"))mini(desktopX,desktopY,false);
      if(desktopSettings.middlePan&&(desktop.buttons&MotionEvent.BUTTON_TERTIARY)!=0&&!profileEditing&&!chatEditing&&activeGame&&page.equals("game")&&modal.isEmpty()&&pendingScreen==null)
        panCamera((oldX-desktopX)*desktopSettings.cameraSpeed/zoom,(oldY-desktopY)*desktopSettings.cameraSpeed/zoom);
    }
  }

  private void desktopCursor(){
    if(!activeGame||!page.equals("game")||!modal.isEmpty())return;
    if(!profileEditing&&!chatEditing&&(desktopPrimary.equals("world")||desktopInside&&gamepadWorld(desktopX,desktopY))){
      float x=Math.max(0,Math.min(logicalWidth-1,desktopX));
      float y=Math.max(0,Math.min(cameraBottom(x)-1,desktopY));
      NativeBridge.command(0,(int)(x/zoom),(int)(y/zoom),0,0);
    }else NativeBridge.command(0,-100,-100,0,0);
  }

  private void desktopPrimary(boolean down,long now){
    if(down){
      if(!desktopInside||pendingScreen!=null)return;
      desktopDownAt=now;desktopPage=page;desktopModal=modal;
      if(!profileEditing&&!chatEditing&&gamepadWorld(desktopX,desktopY)){
        desktopPrimary="world";
        int clicks=desktopLastClickAt>0&&now-desktopLastClickAt<=300&&Math.hypot(desktopX-desktopLastClickX,desktopY-desktopLastClickY)<=6?2:1;
        desktopLastClickAt=clicks==2?0:now;desktopLastClickX=desktopX;desktopLastClickY=desktopY;
        desktopClicks=clicks;NativeBridge.command(33,1,1,clicks,0);desktopNativeButtons|=MotionEvent.BUTTON_PRIMARY;
      }else{
        desktopLastClickAt=0;
        boolean ui=false;for(Button button:buttons)if(button.rect.contains(desktopX,desktopY)){ui=true;break;}
        if(!ui&&(profileEditing||chatEditing)){desktopPrimary="";return;}
        if(!ui&&activeGame&&page.equals("game")&&modal.isEmpty()&&hud!=null){
          if(hud.minimap.contains(desktopX,desktopY)){desktopPrimary="mini";mini(desktopX,desktopY,false);return;}
          desktopHudHit=hud.hit(desktopX,desktopY);
          if(desktopHudHit!=null){desktopPrimary="hud";return;}
          // Blank console area cannot issue battlefield orders.
          desktopPrimary="";return;
        }
        desktopPrimary="ui";desktopTouch(MotionEvent.ACTION_DOWN,now);
      }
    }else{
      if(desktopPrimary.equals("world")&&(desktopNativeButtons&MotionEvent.BUTTON_PRIMARY)!=0){
        NativeBridge.command(33,0,1,desktopClicks,0);desktopNativeButtons&=~MotionEvent.BUTTON_PRIMARY;
      }else if(desktopPrimary.equals("ui"))desktopTouch(MotionEvent.ACTION_UP,now);
      else if(desktopPrimary.equals("hud")&&desktopHudHit!=null&&desktopHudHit.rect.contains(desktopX,desktopY))activateDesktopHud(desktopHudHit);
      desktopPrimary="";desktopHudHit=null;
    }
  }

  private void desktopSecondary(){
    if(profileEditing||chatEditing||!desktopInside||!activeGame||!page.equals("game")||!modal.isEmpty()||pendingScreen!=null)return;
    String card=NativeBridge.text(0);
    if(DesktopControls.targetingCursor(NativeBridge.value(0,0))||card.startsWith("Build\t")||card.startsWith("Advanced Build\t")||placement){
      NativeBridge.command(2,27,41,0,0);placement=false;commandHover.cancel();return;
    }
    if(hud!=null&&hud.minimap.contains(desktopX,desktopY)){mini(desktopX,desktopY,true);return;}
    if(gamepadWorld(desktopX,desktopY)){
      NativeBridge.command(33,1,3,1,0);desktopNativeButtons|=MotionEvent.BUTTON_SECONDARY;
    }
  }

  private void activateDesktopHud(NativeHud.Hit hit){
    if(hit.terrainToggle){sfx("button");NativeBridge.command(8,NativeBridge.value(14,0)==0?1:0,0,0,0);return;}
    if(hit.unload>=0){NativeBridge.command(24,hit.unload,0,0,0);return;}
    if(hit.more){hud.page++;return;}
    if(hit.help!=null){hint=hit.help;hintUntil=clock+3600;return;}
    if(hit.selectUnit>=0){NativeBridge.command(14,hit.selectUnit,0,0,0);return;}
    sfx("button");
    if(hit.cancel>=0)NativeBridge.command(7,hit.cancel,0,0,0);
    else if(hit.fields!=null){
      String[] f=hit.fields;
      hud.gridHotkeys=desktopSettings.gridHotkeys;
      hint=f[2].equals("0")?hud.hint(hit):"";
      hintSupplyRace=NativeHud.number(f,15,hud.race);hintUntil=clock+2400;
      int key=f[0].charAt(0);NativeBridge.command(2,key,key==27?41:0,0,0);NativeBridge.frame(world,0,clock);
    }
  }

  private void desktopTouch(int action,long now){
    float scale=physicalScale();
    MotionEvent event=MotionEvent.obtain(desktopDownAt,now,action,
        desktopX*scale+(physicalWidth-logicalWidth*scale)/2,desktopY*scale+(physicalHeight-480*scale)/2,0);
    try{touch(event);}finally{event.recycle();}
  }

  private void desktopWheel(float amount){
    if(!desktopInside||pendingScreen!=null||amount==0)return;
    listVelocity=0;
    if(modal.equals("missions")&&missionViewport.contains(desktopX,desktopY))missionOffset=Math.max(0,Math.min(missionMaxOffset(),missionOffset-amount*57));
    else if(modal.isEmpty()&&page.equals("custom")&&mapViewport.contains(desktopX,desktopY))mapOffset=Math.max(0,Math.min(mapMaxOffset(),mapOffset-amount*66));
    else if(modal.isEmpty()&&page.equals("replays")&&replayListBounds().contains(desktopX,desktopY))replayOffset=Math.max(0,Math.min(replayMaxOffset(),replayOffset-amount*66));
    else if(modal.isEmpty()&&page.equals("registry")&&profileViewport.contains(desktopX,desktopY))profileOffset=Math.max(0,Math.min(Math.max(0,profiles.size()-9)*23,profileOffset-amount*69));
    else if(modal.isEmpty()&&page.equals("briefing")&&glue!=null){
      NativeArt.Widget box=glue.layout.get(65526);
      if(box!=null&&box.rect().contains(desktopX,desktopY)){briefManual=Math.max(0,Math.min(briefScrollLimit(box),briefScroll-amount*57));briefScroll=briefManual;briefManualUntil=clock+2500;}
    }else if(desktopSettings.wheelZoom&&!profileEditing&&!chatEditing&&gamepadWorld(desktopX,desktopY)&&desktop.buttons==0){
      float next=Math.max(zoomOut?.5f:1f,Math.min(3,zoom+amount*.1f));
      if(next!=zoom){zoom=next;resizeWorld();saveSettings();desktopCursor();}
    }
  }

  boolean handleDesktopKey(KeyEvent event){
    if(DesktopControls.mouseBack(event))return true;
    if(!DesktopControls.keyboard(event)||profileEditing||chatEditing)return false;
    int key=event.getKeyCode(),symbol=DesktopControls.symbol(key,event.getUnicodeChar());
    if(event.getAction()!=KeyEvent.ACTION_DOWN&&event.getAction()!=KeyEvent.ACTION_UP)return false;
    if(symbol==0&&!DesktopControls.modifierKey(key)&&!DesktopControls.cameraKey(key)&&key!=KeyEvent.KEYCODE_F10)return false;
    final boolean down=event.getAction()==KeyEvent.ACTION_DOWN,shift=event.isShiftPressed(),ctrl=event.isCtrlPressed();
    final int repeat=event.getRepeatCount(),device=event.getDeviceId();
    enqueue(()->desktopKey(key,symbol,down,repeat,device,shift,ctrl));return true;
  }

  private void desktopKey(int key,int symbol,boolean down,int repeat,int device,boolean shift,boolean ctrl){
    if(pendingScreen!=null){resetDesktop(false);return;}
    if(profileEditing||chatEditing){resetDesktop(false);return;}
    if(!desktopPage.equals(page)||!desktopModal.equals(modal)||desktopTextEditing!=(profileEditing||chatEditing))resetDesktop(false);
    if(desktopKeyboardDevice!=-1&&desktopKeyboardDevice!=device)resetDesktop(false);
    desktopKeyboardDevice=device;
    if(!desktop.key(key,down,repeat))return;
    // Canonical modifier state also accepts mouse/key metadata when Android omitted a modifier edge.
    boolean modifier=DesktopControls.modifierKey(key);
    syncDesktopModifiers(desktop.shift()||(!modifier&&shift),desktop.ctrl()||(!modifier&&ctrl));
    if(modifier||DesktopControls.cameraKey(key))return;
    if(key==KeyEvent.KEYCODE_F10){if(down&&activeGame&&page.equals("game")){resetDesktop(false);if(modal.isEmpty())openMenu();else modalBack();}return;}
    if(key==KeyEvent.KEYCODE_ESCAPE&&(!activeGame||!page.equals("game")||!modal.isEmpty())){if(down){resetDesktop(false);back();}return;}
    if(symbol==13){if(down&&activeGame&&page.equals("game")&&modal.isEmpty()){resetDesktop(false);openChat();}return;}
    if(!activeGame||!page.equals("game")||!modal.isEmpty()){
      if(down&&!ctrl&&!desktop.ctrl())desktopMenuCommand(symbol);
      return;
    }
    if(replayGame){
      // Replay inspection keeps its native terrain/alert/cancel keys. Only the
      // visible playback buttons consume letter shortcuts; grid orders stay off.
      if(symbol==9||symbol==32||symbol==27)NativeBridge.command(32,down?1:0,symbol,DesktopControls.scanCode(key),0);
      else if(down&&!ctrl&&!desktop.ctrl())desktopMenuCommand(symbol);
      return;
    }
    if(activeGame&&page.equals("game")&&modal.isEmpty()){
      if(desktopSettings.gridHotkeys&&symbol>='a'&&symbol<='z'){
        if(down)desktopGridCommand(symbol);
        return; // Never fall back to an original letter, including on key-up.
      }
      NativeBridge.command(32,down?1:0,symbol,DesktopControls.scanCode(key),0);
    }
  }

  private void desktopMenuCommand(int symbol){
    if(pendingScreen!=null||profileEditing||chatEditing||activeGame&&page.equals("game")&&modal.isEmpty()&&!replayGame)return;
    Button match=null;
    for(Button button:buttons){
      if(!button.enabled||button.action==null||button.key==0||button.key!=symbol
          ||!page.equals(button.page)||!modal.equals(button.modal))continue;
      if(match!=null)return; // An ambiguous caption must never invoke the wrong action.
      match=button;
    }
    if(match==null)return;
    // Actions can change the page or dialog before another render. No previous
    // screen's key binding remains active in that interval, even after returning.
    buttons.clear();pressed=null;
    sfx("button");match.action.run();
  }

  private void advanceDesktop(double milliseconds){
    if(!desktopPage.equals(page)||!desktopModal.equals(modal)||desktopTextEditing!=(profileEditing||chatEditing)){resetDesktop(false);return;}
    if(profileEditing||chatEditing)return;
    if(desktopMouseDevice>=0&&InputDevice.getDevice(desktopMouseDevice)==null
        ||desktopKeyboardDevice>=0&&InputDevice.getDevice(desktopKeyboardDevice)==null){resetDesktop(true);return;}
    if(!activeGame||!page.equals("game")||!modal.isEmpty()||pendingScreen!=null)return;
    double dt=Math.max(0,Math.min(100,milliseconds))/1000.;
    int x=desktopSettings.arrowPan?desktop.cameraX():0,y=desktopSettings.arrowPan?desktop.cameraY():0;
    if(desktopSettings.edgeScroll&&desktopMouseActive&&desktopInside&&!desktopPrimary.equals("ui")&&!desktopPrimary.equals("mini")&&!desktopPrimary.equals("hud")&&(desktop.buttons&MotionEvent.BUTTON_TERTIARY)==0){
      if(desktopX<6)x--;else if(desktopX>=logicalWidth-6)x++;
      if(desktopY<6)y--;else if(desktopY>=474)y++;
    }
    if(x!=0||y!=0)panCamera(Math.max(-1,Math.min(1,x))*480*desktopSettings.cameraSpeed*dt/zoom,Math.max(-1,Math.min(1,y))*480*desktopSettings.cameraSpeed*dt/zoom);
    if(desktopMouseActive)desktopCursor();
  }

  @Override public PointerIcon onResolvePointerIcon(MotionEvent event,int pointerIndex){
    if(DesktopControls.mouseSource(event.getSource()))return PointerIcon.getSystemIcon(getContext(),PointerIcon.TYPE_NULL);
    return super.onResolvePointerIcon(event,pointerIndex);
  }

  @Override public boolean onKeyUp(int key,KeyEvent event){return handleDesktopKey(event)||super.onKeyUp(key,event);}

  private void desktopGridCommand(int symbol){
    int slot=DesktopSettings.gridSlot(symbol);
    if(slot<0||hud==null||replayGame||!activeGame||!page.equals("game")||!modal.isEmpty()||pendingScreen!=null)return;
    // The menu may have just closed before the next tick updates native pause state.
    NativeBridge.command(6,0,0,0,0);
    // Apply earlier selection/submenu events before resolving the currently displayed slot.
    NativeBridge.frame(world,0,clock);
    hud.gridHotkeys=true;
    NativeHud.Hit hit=hud.commandHitForSlot(slot,NativeBridge.text(0),NativeBridge.value(1,0));
    if(hit==null)return;
    if(hit.more){hud.page++;return;}
    String[] fields=hit.fields;
    if(fields==null||fields.length<3)return;
    if(fields.length>=18){
      try{NativeBridge.command(35,Integer.parseInt(fields[16]),Integer.parseInt(fields[17]),0,0);}
      catch(NumberFormatException ignored){} // An invalid/stale command never invokes a different hotkey.
    }else if(fields[0].equals("\u001b"))NativeBridge.command(2,27,41,0,0);
  }

  void openDesktopSettings(){
    enqueue(()->{
      if(!activeGame||!page.equals("game")||modal.equals("control-desktop"))return;
      resetDesktop(false);resetGamepad();cancelGesture();
      desktopReturnModal=modal;modal="control-desktop";
    });
  }

  private void desktopSettingsBack(){
    resetDesktop(false);modal=desktopReturnModal;desktopReturnModal="";
  }

  private void changeDesktop(Runnable update){
    resetDesktop(false);update.run();desktopSettings.save(prefs);
    if(hud!=null)hud.gridHotkeys=desktopSettings.gridHotkeys;
    hint="";hintUntil=0;
  }

  private void desktopToggle(Canvas c,String label,boolean on,float x,float y,Runnable action){
    paint.setColor(0xff040e26);c.drawRect(x,y+4,x+14,y+18,paint);
    paint.setColor(on?0xffd1be56:0xff6282bc);paint.setStyle(Paint.Style.STROKE);
    c.drawRect(x,y+4,x+14,y+18,paint);
    if(on){paint.setStrokeWidth(2);c.drawLine(x+3,y+11,x+6,y+15,paint);c.drawLine(x+6,y+15,x+12,y+7,paint);paint.setStrokeWidth(1);}
    paint.setStyle(Paint.Style.FILL);
    gamepadLine(c,label,x+22,y,242,25,0,2);
    add(new RectF(x-4,y-2,x+266,y+28),()->changeDesktop(action),true);
  }

  private boolean drawDesktopSettings(Canvas c){
    if(!modal.equals("control-desktop"))return false;
    float x=(logicalWidth-604)/2f,y=8;
    paint.setColor(0x80000000);c.drawRect(0,0,logicalWidth,480,paint);panel(c,x,y,604,464);
    text(c,"Mouse & keyboard",x+12,y+10,580,1,2);
    gamepadLine(c,"Command hotkeys",x+22,y+44,160,28,0,2);
    gamepadChoice(c,"Original",null,x+190,y+43,120,28,!desktopSettings.gridHotkeys,
        ()->changeDesktop(()->desktopSettings.gridHotkeys=false));
    gamepadChoice(c,"Grid (QWE/ASD/ZXC)",null,x+318,y+43,264,28,desktopSettings.gridHotkeys,
        ()->changeDesktop(()->desktopSettings.gridHotkeys=true));
    desktopToggle(c,"Mouse edge scrolling",desktopSettings.edgeScroll,x+22,y+80,()->desktopSettings.edgeScroll=!desktopSettings.edgeScroll);
    desktopToggle(c,"Mouse wheel zoom",desktopSettings.wheelZoom,x+318,y+80,()->desktopSettings.wheelZoom=!desktopSettings.wheelZoom);
    desktopToggle(c,"Middle-button camera drag",desktopSettings.middlePan,x+22,y+112,()->desktopSettings.middlePan=!desktopSettings.middlePan);
    desktopToggle(c,"Arrow-key camera",desktopSettings.arrowPan,x+318,y+112,()->desktopSettings.arrowPan=!desktopSettings.arrowPan);
    gamepadSlider(c,"Camera speed",Math.round(desktopSettings.cameraSpeed*100),25,250,x+22,y+144,
        value->{desktopSettings.cameraSpeed=value/100f;desktopSettings.save(prefs);});
    drawDesktopGuide(c,x+22,y+184);
    gamepadOption(c,"Previous",null,x+194,y+428,216,28,false,this::desktopSettingsBack);return true;
  }

  private void desktopGuideHeading(Canvas c,String title,float x,float y){
    String label=PortText.text(title)+":";
    // Thicken the original bitmap glyphs by one pixel, keeping their native style.
    body(c,label,x,y,559,16);
    body(c,label,x+1,y,559,16);
  }

  private void drawDesktopGuide(Canvas c,float x,float y){
    desktopGuideHeading(c,"Mouse",x,y);
    body(c,DesktopGuide.MOUSE_GUIDE,x,y+16,560,80);
    // One full text line separates the two sections.
    desktopGuideHeading(c,"Keyboard",x,y+112);
    body(c,DesktopGuide.KEYBOARD_GUIDE,x,y+128,560,96);
  }

  void reloadGamepadSettings() { enqueue(()->{resetGamepad();gamepad.load(prefs);}); }

  boolean handleGamepadMotion(MotionEvent event) {
    if(!gamepad.enabled||!GamepadControls.source(event.getSource())||event.getActionMasked()!=MotionEvent.ACTION_MOVE)return false;
    MotionEvent copy=MotionEvent.obtain(event);
    enqueue(()->{try{
      activateGamepad(copy.getDeviceId());
      float lx=gamepad.axis(copy,MotionEvent.AXIS_X),ly=gamepad.axis(copy,MotionEvent.AXIS_Y);
      float rx=gamepad.axis(copy,MotionEvent.AXIS_Z),ry=gamepad.axis(copy,MotionEvent.AXIS_RZ);
      // Some controllers report the right stick as RX/RY instead of Z/RZ.
      InputDevice device=copy.getDevice();
      if(device!=null&&device.getMotionRange(MotionEvent.AXIS_Z,copy.getSource())==null){
        rx=gamepad.axis(copy,MotionEvent.AXIS_RX);ry=gamepad.axis(copy,MotionEvent.AXIS_RY);
      }
      padPointerX=gamepad.swapSticks?rx:lx;padPointerY=gamepad.swapSticks?ry:ly;
      padCameraX=gamepad.swapSticks?lx:rx;padCameraY=gamepad.swapSticks?ly:ry;
      padHatX=copy.getAxisValue(MotionEvent.AXIS_HAT_X);padHatY=copy.getAxisValue(MotionEvent.AXIS_HAT_Y);
      gamepadButton(KeyEvent.KEYCODE_BUTTON_L2,Math.max(copy.getAxisValue(MotionEvent.AXIS_LTRIGGER),copy.getAxisValue(MotionEvent.AXIS_BRAKE))>.5f,2);
      gamepadButton(KeyEvent.KEYCODE_BUTTON_R2,Math.max(copy.getAxisValue(MotionEvent.AXIS_RTRIGGER),copy.getAxisValue(MotionEvent.AXIS_GAS))>.5f,2);
    }finally{copy.recycle();}});
    return true;
  }

  boolean handleGamepadKey(KeyEvent event) {
    int key=event.getKeyCode();
    InputDevice deviceInfo=event.getDevice();
    boolean controller=GamepadControls.source(event.getSource())
        ||deviceInfo!=null&&GamepadControls.source(deviceInfo.getSources());
    if(!gamepad.enabled||!controller||(!GamepadControls.button(key)&&!gamepadDirection(key)))return false;
    if(event.getAction()!=KeyEvent.ACTION_DOWN&&event.getAction()!=KeyEvent.ACTION_UP)return false;
    if(event.getRepeatCount()!=0)return true;
    final boolean down=event.getAction()==KeyEvent.ACTION_DOWN;final int device=event.getDeviceId();
    enqueue(()->{activateGamepad(device);gamepadButton(key,down,1);});
    return true;
  }

  @Override public boolean onGenericMotionEvent(MotionEvent event) {
    return handleDesktopMotion(event)||handleGamepadMotion(event)||super.onGenericMotionEvent(event);
  }

  private static boolean gamepadDirection(int key) {
    return key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT
        ||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN;
  }

  private void activateGamepad(int device) {
    resetDesktop(true);
    if(padDevice!=device)resetGamepad();
    padDevice=device;padActive=true;
    padX=GamepadControls.limit(padX,logicalWidth);padY=GamepadControls.limit(padY,480);
  }

  private boolean gamepadActionHeld(int action) {
    for(int key:gamepadHeld.keySet())if(gamepad.action(key)==action)return true;
    return false;
  }

  private void gamepadButton(int key,boolean down,int source) {
    int action=gamepad.action(key),before=gamepadHeld.containsKey(key)?gamepadHeld.get(key):0;
    boolean heldBefore=gamepadActionHeld(action);
    int after=down?before|source:before&~source;
    if(after==0)gamepadHeld.remove(key);else gamepadHeld.put(key,after);
    boolean heldAfter=gamepadActionHeld(action);
    padPrecision=gamepadActionHeld(GamepadControls.PRECISION);
    if(gamepadDirection(key)||action==GamepadControls.NONE||action==GamepadControls.PRECISION)return;
    if(action==GamepadControls.CLICK){if(heldBefore!=heldAfter)gamepadClick(heldAfter);return;}
    if(!down||before!=0||after==0)return;
    if(action==GamepadControls.CANCEL){back();return;}
    if(action==GamepadControls.MENU){
      resetGamepadDrag();if(modal.startsWith("gamepad")){gamepadSettingsBack();return;}
      if(page.equals("game")){if(modal.isEmpty())openMenu();else modal="";}
      return;
    }
    if(!activeGame||!page.equals("game")||!modal.isEmpty()||pendingScreen!=null)return;
    if(action==GamepadControls.ORDER){
      if(hud!=null&&hud.minimap.contains(padX,padY))mini(padX,padY,true);
      else if(gamepadWorld(padX,padY)){
        NativeBridge.command(0,(int)(padX/zoom),(int)(padY/zoom),0,0);
        NativeBridge.command(1,1,3,(int)(padX/zoom),(int)(padY/zoom));
        NativeBridge.command(1,0,3,(int)(padX/zoom),(int)(padY/zoom));
      }
    }else if(action==GamepadControls.ATTACK||action==GamepadControls.STOP||action==GamepadControls.HOLD||action==GamepadControls.LARVAE){
      int keyChar=action==GamepadControls.ATTACK?'a':action==GamepadControls.STOP?'s':action==GamepadControls.HOLD?'h':'s';
      // Larva hotkey is only sent while a Hatchery/Lair/Hive is selected.
      int type=NativeBridge.value(1,0);
      if(action!=GamepadControls.LARVAE||(type>=131&&type<=133))NativeBridge.command(2,keyChar,0,0,0);
    }else if(action==GamepadControls.PREVIOUS_GROUP||action==GamepadControls.NEXT_GROUP){
      padGroup=GamepadControls.nextGroup(padGroup,action==GamepadControls.NEXT_GROUP?1:-1);
      hint="Control group "+padGroup;hintUntil=clock+1800;
    }else if(action==GamepadControls.RECALL_GROUP||action==GamepadControls.ASSIGN_GROUP){
      controlGroup(padGroup,action==GamepadControls.ASSIGN_GROUP);
    }else if(action==GamepadControls.ZOOM_IN||action==GamepadControls.ZOOM_OUT){
      resetGamepadDrag();zoom=Math.max(zoomOut?.5f:1,Math.min(3,zoom+(action==GamepadControls.ZOOM_IN?.25f:-.25f)));
      resizeWorld();saveSettings();
    }
  }

  private boolean gamepadWorld(float x,float y) {
    if(!activeGame||!page.equals("game")||!modal.isEmpty()||hud==null||y>=cameraBottom(x))return false;
    if(hud.minimap.contains(x,y)||hud.hit(x,y)!=null)return false;
    for(Button button:buttons)if(button.rect.contains(x,y))return false;
    return true;
  }

  private void gamepadClick(boolean down) {
    long now=android.os.SystemClock.uptimeMillis();
    if(down){
      if(pendingScreen!=null)return;
      padClickAt=now;padDragPage=page;padDragModal=modal;
      padNativeDrag=gamepadWorld(padX,padY);padTouchDrag=!padNativeDrag;
      if(padNativeDrag){
        NativeBridge.command(0,(int)(padX/zoom),(int)(padY/zoom),0,0);
        NativeBridge.command(1,1,1,(int)(padX/zoom),(int)(padY/zoom));
      }else gamepadTouch(MotionEvent.ACTION_DOWN,now);
    }else{
      if(!padDragPage.equals(page)||!padDragModal.equals(modal)){resetGamepadDrag();return;}
      if(padNativeDrag)NativeBridge.command(1,0,1,(int)(padX/zoom),(int)(padY/zoom));
      else if(padTouchDrag)gamepadTouch(MotionEvent.ACTION_UP,now);
      padNativeDrag=padTouchDrag=false;
    }
  }

  private void gamepadTouch(int action,long now) {
    float scale=physicalScale();
    MotionEvent event=MotionEvent.obtain(padClickAt,now,action,
        padX*scale+(physicalWidth-logicalWidth*scale)/2,
        padY*scale+(physicalHeight-480*scale)/2,0);
    try{touch(event);}finally{event.recycle();}
  }

  private void resetGamepadDrag() {
    if(padNativeDrag&&activeGame)NativeBridge.command(5,0,0,0,0);
    if(padTouchDrag)cancelGesture();
    padNativeDrag=padTouchDrag=false;
  }

  private void resetGamepad() {
    resetGamepadDrag();gamepadHeld.clear();padPrecision=false;padActive=false;padDevice=-1;
    padPointerX=padPointerY=padCameraX=padCameraY=padHatX=padHatY=0;
    if(activeGame)NativeBridge.command(0,-100,-100,0,0);
  }

  private void advanceGamepad(double milliseconds) {
    if(!padActive||!gamepad.enabled)return;
    if(padDevice>=0&&InputDevice.getDevice(padDevice)==null){resetGamepad();return;}
    if((padNativeDrag||padTouchDrag)&&(!padDragPage.equals(page)||!padDragModal.equals(modal)))resetGamepadDrag();
    double dt=Math.max(0,Math.min(100,milliseconds))/1000.;
    float oldX=padX,oldY=padY;
    double speed=360*gamepad.pointerSpeed*(padPrecision?.25:1);
    padX=GamepadControls.limit(padX+(float)(padPointerX*speed*dt),logicalWidth);
    padY=GamepadControls.limit(padY+(float)(padPointerY*speed*dt),480);
    updateHover(padX,padY,false);
    if(padTouchDrag&&(oldX!=padX||oldY!=padY))gamepadTouch(MotionEvent.ACTION_MOVE,android.os.SystemClock.uptimeMillis());
    if(activeGame&&page.equals("game")&&modal.isEmpty()&&pendingScreen==null){
      float cx=padCameraX+padHatX+(gamepadHeld.containsKey(KeyEvent.KEYCODE_DPAD_RIGHT)?1:0)-(gamepadHeld.containsKey(KeyEvent.KEYCODE_DPAD_LEFT)?1:0);
      float cy=padCameraY+padHatY+(gamepadHeld.containsKey(KeyEvent.KEYCODE_DPAD_DOWN)?1:0)-(gamepadHeld.containsKey(KeyEvent.KEYCODE_DPAD_UP)?1:0);
      panCamera(Math.max(-1,Math.min(1,cx))*480*gamepad.cameraSpeed*dt/zoom,
          Math.max(-1,Math.min(1,cy))*480*gamepad.cameraSpeed*dt/zoom);
      if(padNativeDrag||gamepadWorld(padX,padY))NativeBridge.command(0,(int)(padX/zoom),(int)(padY/zoom),0,0);
      else NativeBridge.command(0,-100,-100,0,0);
    }
  }

  private void drawGamepadCursor(Canvas canvas) {
    if(!padActive||!gamepad.enabled)return;
    drawPointer(canvas,padX,padY,padNativeDrag||gamepadWorld(padX,padY));
  }

  private void drawPointer(Canvas canvas,float x,float y,boolean nativeWorld){
    if(activeGame&&page.equals("game")&&modal.isEmpty()&&nativeWorld){
      try{
        int box=NativeBridge.value(23,0);
        if(box>0&&box<=256){
          if(padCursor==null||padCursor.getWidth()!=box){if(padCursor!=null)padCursor.recycle();padCursor=Bitmap.createBitmap(box,box,Bitmap.Config.ARGB_8888);}
          if(NativeBridge.cursor(padCursor)){canvas.drawBitmap(padCursor,x-box/2f,y-box/2f,NativeArt.PIXELS);return;}
        }
      }catch(Exception ignored){} // Missing cursor artwork must not disable the controller.
    }
    if(!padArrowChecked&&art!=null){
      padArrowChecked=true;
      try{
        byte[] data=art.read("cursor/arrow.grp"),palette=art.read("tileset/badlands.wpe");
        if(data!=null&&data.length>=6&&palette!=null&&palette.length>=1024){
          int count=Math.min(64,NativeArt.u16(data,0)),boxX=NativeArt.u16(data,2),boxY=NativeArt.u16(data,4);
          if(count>0&&data.length>=6+count*8){
            int[] colors=new int[256];for(int i=0;i<256;i++)colors[i]=Color.rgb(palette[i*4]&255,palette[i*4+1]&255,palette[i*4+2]&255);
            padArrows=new Bitmap[count];padArrowX=new int[count];padArrowY=new int[count];
            for(int i=0;i<count;i++){
              padArrows[i]=NativeArt.grp(data,i,colors,false,false);
              padArrowX[i]=(data[6+i*8]&255)-boxX/2;padArrowY[i]=(data[7+i*8]&255)-boxY/2;
            }
          }
        }
      }catch(Exception ignored){}
    }
    if(padArrows!=null&&padArrows.length>0){
      int frame=(int)(clock/100)%padArrows.length;
      if(padArrows[frame]!=null){canvas.drawBitmap(padArrows[frame],x+padArrowX[frame],y+padArrowY[frame],NativeArt.PIXELS);return;}
    }
    // A small high-contrast arrow remains usable with incomplete data imports.
    Path arrow=new Path();arrow.moveTo(x,y);arrow.lineTo(x+1,y+18);arrow.lineTo(x+6,y+13);arrow.lineTo(x+13,y+13);arrow.close();
    Paint pointer=new Paint(Paint.ANTI_ALIAS_FLAG);pointer.setColor(Color.BLACK);pointer.setStyle(Paint.Style.STROKE);pointer.setStrokeWidth(3);canvas.drawPath(arrow,pointer);
    pointer.setStyle(Paint.Style.FILL);pointer.setColor(0xff8fdb55);canvas.drawPath(arrow,pointer);
  }

  @Override
  public boolean onKeyDown(int key, android.view.KeyEvent event) {
    if(profileEditing){
      if(key==KeyEvent.KEYCODE_ENTER){enqueue(this::confirmProfileDialog);return true;}
      if(key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_ESCAPE){enqueue(this::cancelProfileDialog);return true;}
      if(key==KeyEvent.KEYCODE_DEL){if(profileInput.length()>0)profileInput.delete(profileInput.length()-1,profileInput.length());}
      else {int character=event.getUnicodeChar();if(character>=32&&profileInput.length()<24)profileInput.append(Character.toString((char)character));else return super.onKeyDown(key,event);}
      String value=profileInput.toString();enqueue(()->profileDraft=value);return true;
    }
    if(chatEditing){
      if(key==KeyEvent.KEYCODE_ENTER){enqueue(this::sendChat);return true;}
      if(key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_ESCAPE){closeChat();return true;}
      if(key==KeyEvent.KEYCODE_DEL){if(chatInput.length()>0)chatInput.delete(chatInput.length()-1,chatInput.length());publishChatInput();return true;}
      int character=event.getUnicodeChar();if(character>=32&&chatInput.length()<180){chatInput.append(Character.toString((char)character));publishChatInput();return true;}
    }
    if (key == android.view.KeyEvent.KEYCODE_BACK) {
      back();
      return true;
    }
    if(handleDesktopKey(event))return true;
    return super.onKeyDown(key, event);
  }
}
