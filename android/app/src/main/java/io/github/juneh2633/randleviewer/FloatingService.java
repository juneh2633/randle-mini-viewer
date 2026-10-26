package io.github.juneh2633.randleviewer;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.Display;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JsResult;
import android.webkit.JsPromptResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.graphics.drawable.GradientDrawable;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class FloatingService extends Service {
    static final String RETURN = "io.github.juneh2633.randleviewer.RETURN";
    private static final String CLOSE = "io.github.juneh2633.randleviewer.CLOSE";
    private static final String CHANNEL = "floating-window";
    private static final int BACKGROUND = Color.rgb(20, 32, 52), BLUE = Color.rgb(166, 216, 255), AMBER = Color.rgb(255, 224, 157);
    static FloatingService instance;
    LinearLayout window;
    WebView web;
    private WindowManager manager;
    private Context uiContext;
    private AlertDialog activeDialog;
    private WindowManager.LayoutParams layout;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Button[] digits = new Button[8];
    private Button reroll, selectAll, confirm, start, size, autoToggle;
    private TextView status, timer;
    private FrameLayout pageFrame;
    private String source, autoSource, lastState = "";
    private JSONObject state = new JSONObject();
    private boolean closed, returning, reading, expanded;
    private int generation;
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) AutoMode.pause(web, "화면이 꺼져 자동 정지"); }
    };
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (closed || returning) return;
            read(); handler.postDelayed(this, 500);
        }
    };
    static boolean isRunning() { return instance != null && !instance.closed && !instance.returning; }
    @Override public void onCreate() {
        super.onCreate(); instance = this;
        ContextCompat.registerReceiver(this, screen, new IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED);
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "작은 창 실행", NotificationManager.IMPORTANCE_LOW));
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openApp = PendingIntent.getActivity(this, 40, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent close = PendingIntent.getService(this, 41, new Intent(this, FloatingService.class).setAction(CLOSE), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_app)
            .setContentTitle("랜들 작은 창 실행 중").setContentText("창에서 직접 조작할 수 있습니다. 닫기를 누르면 종료합니다.")
            .setContentIntent(openApp).setOngoing(true).addAction(new Notification.Action.Builder(null, "닫기", close).build()).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(20, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(20, notification);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && CLOSE.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (intent != null && RETURN.equals(intent.getAction())) { returnToApp(); return START_NOT_STICKY; }
        if (window != null) return START_NOT_STICKY;
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY; }
        try {
            try (InputStream input = getAssets().open("viewer.js")) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                source = bytes.toString(StandardCharsets.UTF_8.name());
            }
            autoSource = AutoMode.source(this);
            uiContext = this;
            if (Build.VERSION.SDK_INT >= 30) {
                Display display = getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
                uiContext = createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
            }
            uiContext = new ContextThemeWrapper(uiContext, android.R.style.Theme_Material_NoActionBar);
            manager = uiContext.getSystemService(WindowManager.class);
            web = WindowSession.take(uiContext); if (web == null) web = WindowSession.create(uiContext);
            buildWindow(); configurePage();
            Rect bounds = bounds();
            layout = new WindowManager.LayoutParams(Math.min(dp(260), bounds.width() - dp(16)), Math.min(dp(220), bounds.height() - dp(80)),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
            layout.gravity = Gravity.TOP | Gravity.START; layout.x = dp(8); layout.y = dp(48);
            layout.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
            manager.addView(window, layout); hideKeyboard(); web.onResume();
            if (web.getUrl() == null) web.loadUrl("https://randle.lixx.kr/play");
            handler.post(poll);
        } catch (Exception error) {
            if (web != null) { WindowSession.offer(web); returning = true; }
            Toast.makeText(this, "작은 창을 열지 못했습니다. 앱에서 표시 권한을 확인해 주세요.", Toast.LENGTH_LONG).show(); stopSelf();
        }
        return START_NOT_STICKY;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private Rect bounds() {
        if (Build.VERSION.SDK_INT >= 30) return manager.getMaximumWindowMetrics().getBounds();
        return new Rect(0, 0, getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
    }
    private Button button(String title) {
        Button control = new Button(uiContext); control.setText(title); control.setAllCaps(false); control.setTextSize(11);
        control.setTextColor(BACKGROUND); control.setBackgroundTintList(ColorStateList.valueOf(BLUE)); control.setMinWidth(0);
        control.setMinimumWidth(0); control.setMinHeight(0); control.setMinimumHeight(0);
        GradientDrawable background = new GradientDrawable(); background.setColor(BLUE); background.setCornerRadius(dp(4)); control.setBackground(background);
        control.setPadding(dp(2), 0, dp(2), 0); return control;
    }
    private TextView label(int size) { TextView view = new TextView(uiContext); view.setTextSize(size); view.setTextColor(Color.WHITE); return view; }
    private void add(LinearLayout row, View child, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(height), 1); params.setMargins(dp(1), dp(1), dp(1), dp(1)); row.addView(child, params);
    }
    @SuppressLint("ClickableViewAccessibility")
    private void buildWindow() {
        window = new LinearLayout(uiContext); window.setOrientation(LinearLayout.VERTICAL); window.setBackgroundColor(BACKGROUND);
        window.setPadding(dp(5), dp(4), dp(5), dp(4)); window.setElevation(dp(12));
        LinearLayout title = new LinearLayout(uiContext);
        TextView move = label(12); move.setText("⠿ 랜들"); move.setGravity(Gravity.CENTER); move.setContentDescription("창 이동");
        move.setOnTouchListener(new View.OnTouchListener() {
            float x, y; int left, top;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (layout == null) return false;
                if (event.getAction() == MotionEvent.ACTION_DOWN) { x = event.getRawX(); y = event.getRawY(); left = layout.x; top = layout.y; return true; }
                if (event.getAction() == MotionEvent.ACTION_MOVE) { layout.x = left + Math.round(event.getRawX() - x); layout.y = top + Math.round(event.getRawY() - y); clamp(); applyLayout(); return true; }
                return true;
            }
        });
        title.addView(move, new LinearLayout.LayoutParams(0, dp(28), 1));
        size = button("크게"); size.setOnClickListener(view -> toggleSize()); title.addView(size, new LinearLayout.LayoutParams(dp(40), dp(28)));
        Button app = button("앱"); app.setOnClickListener(view -> returnToApp()); title.addView(app, new LinearLayout.LayoutParams(dp(32), dp(28)));
        Button close = button("닫기"); close.setOnClickListener(view -> stopSelf()); title.addView(close, new LinearLayout.LayoutParams(dp(36), dp(28)));
        window.addView(title);
        pageFrame = new FrameLayout(uiContext); pageFrame.setClipChildren(true);
        pageFrame.addView(web, new FrameLayout.LayoutParams(-1, dp(360)));
        window.addView(pageFrame, new LinearLayout.LayoutParams(-1, dp(1)));
        status = label(10); status.setSingleLine(true); status.setEllipsize(android.text.TextUtils.TruncateAt.END); status.setText("숫자를 확인하는 중입니다."); window.addView(status);
        status.setOnLongClickListener(view -> {
            AutoMode.pause(web, "명령 입력 중");
            if (activeDialog != null) activeDialog.cancel();
            AlertDialog dialog = AutoMode.dialog(uiContext, true, this::runAutoCommand); activeDialog = dialog;
            dialog.setOnDismissListener(item -> { if (activeDialog == dialog) activeDialog = null; });
            return true;
        });
        LinearLayout numberRow = new LinearLayout(uiContext);
        for (int index = 0; index < 8; index++) {
            final int position = index;
            digits[index] = button("?"); digits[index].setTextSize(18); digits[index].setPadding(0, 0, 0, 0); digits[index].setEnabled(false);
            digits[index].setOnClickListener(view -> perform("select", position)); add(numberRow, digits[index], 34);
        }
        window.addView(numberRow); timer = label(10); timer.setSingleLine(true); timer.setTextColor(AMBER); window.addView(timer);
        autoToggle = button("자동 일시 정지"); autoToggle.setVisibility(View.GONE);
        autoToggle.setOnClickListener(view -> runAutoCommand(AutoMode.state(state).optBoolean("running") ? "/auto pause" : "/auto resume")); window.addView(autoToggle, new LinearLayout.LayoutParams(-1, dp(28)));
        LinearLayout first = new LinearLayout(uiContext), second = new LinearLayout(uiContext);
        reroll = button("선택 자리 굴리기"); selectAll = button("전체 선택"); confirm = button("이 숫자로 확정"); start = button("숫자 뽑기");
        reroll.setOnClickListener(view -> perform("reroll", -1)); selectAll.setOnClickListener(view -> perform("selectAll", -1));
        confirm.setOnClickListener(view -> perform("confirm", -1)); start.setOnClickListener(view -> perform("start", -1));
        for (Button control : new Button[]{reroll, selectAll, confirm, start}) control.setEnabled(false);
        add(first, reroll, 30); add(first, selectAll, 30); add(second, confirm, 30); add(second, start, 30);
        window.addView(first); window.addView(second);
        LinearLayout footer = new LinearLayout(uiContext);
        Button back = button("뒤로"); back.setOnClickListener(view -> { if (web.canGoBack()) web.goBack(); }); add(footer, back, 24);
        Button reload = button("새로고침"); reload.setOnClickListener(view -> web.reload()); add(footer, reload, 24);
        TextView resize = label(12); resize.setText("크기 조절 ↘"); resize.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL); resize.setContentDescription("창 크기 조절");
        resize.setOnTouchListener(new View.OnTouchListener() {
            float x, y; int width, height;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (layout == null) return false;
                if (event.getAction() == MotionEvent.ACTION_DOWN) { x = event.getRawX(); y = event.getRawY(); width = layout.width; height = layout.height; return true; }
                if (event.getAction() == MotionEvent.ACTION_MOVE) { layout.width = width + Math.round(event.getRawX() - x); layout.height = height + Math.round(event.getRawY() - y); clamp(); applyLayout(); return true; }
                return true;
            }
        });
        add(footer, resize, 24); window.addView(footer);
    }
    private void clamp() {
        Rect bounds = bounds(); int maxWidth = Math.max(dp(200), bounds.width() - dp(16)), maxHeight = Math.max(dp(300), bounds.height() - dp(64));
        layout.width = Math.max(Math.min(dp(240), maxWidth), Math.min(layout.width, maxWidth));
        layout.height = Math.max(Math.min(dp(expanded ? 360 : 220), maxHeight), Math.min(layout.height, maxHeight));
        layout.x = Math.max(0, Math.min(layout.x, bounds.width() - layout.width));
        layout.y = Math.max(dp(24), Math.min(layout.y, bounds.height() - layout.height));
    }
    private void applyLayout() {
        if (closed || window == null || !window.isAttachedToWindow()) return;
        try { manager.updateViewLayout(window, layout); } catch (RuntimeException error) { stopSelf(); }
    }
    private void toggleSize() {
        expanded = !expanded; Rect bounds = bounds(); layout.x = dp(8); layout.y = dp(32);
        layout.width = expanded ? bounds.width() - dp(16) : dp(260);
        layout.height = expanded ? bounds.height() - dp(64) : dp(220);
        if (!expanded) hideKeyboard();
        pageFrame.setLayoutParams(expanded ? new LinearLayout.LayoutParams(-1, 0, 1) : new LinearLayout.LayoutParams(-1, dp(1)));
        web.setLayoutParams(new FrameLayout.LayoutParams(-1, expanded ? -1 : dp(360)));
        size.setText(expanded ? "작게" : "크게"); clamp(); applyLayout();
    }
    private void hideKeyboard() {
        web.clearFocus();
        uiContext.getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(web.getWindowToken(), 0);
    }
    @SuppressLint("SetJavaScriptEnabled")
    private void configurePage() {
        web.getSettings().setJavaScriptEnabled(true); web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false); web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); web.getSettings().setSafeBrowsingEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return !"https".equals(request.getUrl().getScheme()); }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) { generation++; reading = false; lastState = ""; show(new JSONObject()); }
            @Override public void onPageFinished(WebView view, String url) { read(); }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onJsAlert(WebView view, String url, String message, JsResult result) { dialog(message, result, false); return true; }
            @Override public boolean onJsConfirm(WebView view, String url, String message, JsResult result) { dialog(message, result, true); return true; }
            @Override public boolean onJsPrompt(WebView view, String url, String message, String initial, JsPromptResult result) {
                EditText input = new EditText(uiContext); input.setText(initial);
                AlertDialog dialog = new AlertDialog.Builder(uiContext).setTitle("랜들 입력").setMessage(message).setView(input)
                    .setPositiveButton("확인", (item, which) -> result.confirm(input.getText().toString()))
                    .setNegativeButton("취소", (item, which) -> result.cancel()).setOnCancelListener(item -> result.cancel()).create();
                showDialog(dialog, result); return true;
            }
        });
    }
    private void dialog(String message, JsResult result, boolean cancel) {
        AlertDialog.Builder builder = new AlertDialog.Builder(uiContext).setTitle("랜들 안내").setMessage(message).setPositiveButton("확인", (item, which) -> result.confirm());
        if (cancel) builder.setNegativeButton("취소", (item, which) -> result.cancel());
        builder.setOnCancelListener(item -> result.cancel()); showDialog(builder.create(), result);
    }
    private void showDialog(AlertDialog dialog, JsResult result) {
        if (!Settings.canDrawOverlays(this) || closed || returning) { result.cancel(); return; }
        if (activeDialog != null) activeDialog.cancel();
        activeDialog = dialog;
        dialog.setOnDismissListener(item -> { if (activeDialog == dialog) activeDialog = null; });
        if (dialog.getWindow() != null) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        dialog.show();
    }
    private boolean playPage() {
        if (web == null) return false;
        Uri uri = Uri.parse(web.getUrl() == null ? "" : web.getUrl());
        return "https".equals(uri.getScheme()) && "randle.lixx.kr".equals(uri.getHost()) && (uri.getPort() == -1 || uri.getPort() == 443)
            && ("/play".equals(uri.getPath()) || "/play/".equals(uri.getPath()));
    }
    private String script(String expression) {
        return "(function(){if(location.origin!=='https://randle.lixx.kr'||!/^\\/play\\/?$/.test(location.pathname))return null;" + source + ";" + autoSource + ";return JSON.stringify(" + expression + ");})()";
    }
    private void read() {
        if (closed || returning || reading) return;
        if (!playPage()) { AutoMode.pause(web, "페이지 이동으로 자동 정지"); show(new JSONObject()); return; }
        final int currentGeneration = generation; reading = true;
        web.evaluateJavascript(script("RandleAndroidAuto.snapshot()"), raw -> {
            if (closed || returning || currentGeneration != generation) return;
            reading = false;
            try {
                Object value = new JSONTokener(raw).nextValue(); if (!(value instanceof String)) return;
                if (!value.equals(lastState)) { lastState = (String) value; show(new JSONObject(lastState)); }
            } catch (Exception ignored) { status.setText("숫자 표시를 기다립니다."); }
        });
    }
    private void show(JSONObject data) {
        if (status == null || closed || returning) return;
        state = data;
        JSONArray values = data.optJSONArray("digits"), selected = data.optJSONArray("selected"), selectable = data.optJSONArray("selectable");
        for (int index = 0; index < 8; index++) {
            String value = values == null ? "?" : values.optString(index, "?"); if (!value.matches("[0-9?]")) value = "?";
            digits[index].setText(value); digits[index].setContentDescription((index + 1) + "번째 자리 " + value + " 리롤 선택");
            digits[index].setEnabled(selectable != null && selectable.optBoolean(index));
            boolean chosen = selected != null && selected.optBoolean(index); digits[index].setSelected(chosen);
            digits[index].setBackgroundTintList(ColorStateList.valueOf(chosen ? AMBER : BLUE));
        }
        JSONObject automation = AutoMode.state(data);
        autoToggle.setVisibility(automation.optBoolean("enabled") ? View.VISIBLE : View.GONE);
        autoToggle.setText(automation.optBoolean("running") ? "자동 일시 정지" : "자동 재개");
        status.setText(AutoMode.message(data, data.optString("status", "위 웹 화면에서 로그인하거나 인증해 주세요."))); timer.setText(data.optString("timer"));
        status.setTextColor("인증 필요".equals(data.optString("phase")) ? AMBER : Color.WHITE);
        reroll.setEnabled(data.optBoolean("canReroll")); selectAll.setEnabled(data.optBoolean("canSelectAll"));
        confirm.setEnabled(data.optBoolean("canConfirm")); start.setEnabled(data.optBoolean("canStart")); start.setText(data.optString("startLabel", "숫자 뽑기"));
    }
    private void perform(String action, int position) {
        if (closed || returning || !playPage() || !Arrays.asList("select", "selectAll", "reroll", "confirm", "start").contains(action)) return;
        if ("select".equals(action) && (position < 0 || position >= 8)) return;
        AutoMode.pause(web, "직접 조작하여 자동 일시 정지");
        web.evaluateJavascript(script("RandleViewer.perform(document," + JSONObject.quote(action) + "," + position + ")"), result -> {
            if (closed || returning) return;
            if (!"\"true\"".equals(result)) Toast.makeText(this, "현재 사용할 수 없는 버튼입니다.", Toast.LENGTH_SHORT).show();
            read();
        });
    }
    void runAutoCommand(String command) {
        if (!AutoMode.valid(command) || closed || returning || !playPage()) return;
        web.evaluateJavascript(script("RandleAndroidAuto.command(" + JSONObject.quote(command) + ")"), result -> {
            if (closed || returning) return;
            if (!"\"true\"".equals(result)) Toast.makeText(this, "인증과 페이지 상태를 확인해 주세요.", Toast.LENGTH_SHORT).show();
            lastState = ""; read();
        });
    }
    private void returnToApp() {
        if (closed || returning) return;
        returning = true; handler.removeCallbacks(poll);
        if (web != null) WindowSession.offer(web);
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("returnedFromFloating", true));
        stopSelf();
    }
    @Override public void onConfigurationChanged(Configuration configuration) { super.onConfigurationChanged(configuration); if (layout != null) { clamp(); applyLayout(); } }
    @Override public void onTaskRemoved(Intent rootIntent) { stopSelf(); super.onTaskRemoved(rootIntent); }
    @Override public void onDestroy() {
        closed = true; handler.removeCallbacksAndMessages(null);
        unregisterReceiver(screen);
        if (activeDialog != null) activeDialog.cancel();
        if (window != null && window.isAttachedToWindow()) manager.removeView(window);
        if (web != null && !returning) {
            if (web.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) web.getParent()).removeView(web);
            web.destroy(); CookieManager.getInstance().flush();
        }
        if (instance == this) instance = null;
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
