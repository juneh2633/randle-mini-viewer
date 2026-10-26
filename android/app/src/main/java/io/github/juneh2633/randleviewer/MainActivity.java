package io.github.juneh2633.randleviewer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class MainActivity extends Activity {
    private static final String HOME = "https://randle.lixx.kr/play";
    private static final String CONTROL = "io.github.juneh2633.randleviewer.CONTROL";
    private static final int BACKGROUND = Color.rgb(20, 32, 52);
    private static final int BLUE = Color.rgb(166, 216, 255);
    private static final int AMBER = Color.rgb(255, 224, 157);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Button[] digits = new Button[8];
    private WebView web;
    private LinearLayout header, panel, pipOverlay;
    private TextView status, timer, pipDigits, pipPhase, pipTimer, pipHint;
    private Button reroll, selectAll, confirm, start;
    private String viewerSource, lastState = "", lastActions = "";
    private JSONObject state = new JSONObject();
    private boolean active, pendingRead, destroyed;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!active || destroyed) return;
            readState();
            handler.postDelayed(this, 500);
        }
    };
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!CONTROL.equals(intent.getAction())) return;
            String action = intent.getStringExtra("command");
            if ("confirm".equals(action) || "start".equals(action)) perform(action, -1);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        try (InputStream input = getAssets().open("viewer.js")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            viewerSource = bytes.toString(StandardCharsets.UTF_8.name());
        } catch (IOException error) {
            TextView message = new TextView(this);
            message.setText("숫자 표시 파일을 읽지 못했습니다. 앱을 다시 설치해 주세요.");
            setContentView(message); return;
        }
        buildLayout();
        configureWebView();
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, new IntentFilter(CONTROL), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, new IntentFilter(CONTROL));
        if (saved == null || web.restoreState(saved) == null) web.loadUrl(HOME);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(int size) {
        TextView view = new TextView(this);
        view.setTextColor(Color.WHITE); view.setTextSize(size);
        return view;
    }
    private Button button(String title) {
        Button button = new Button(this);
        button.setText(title); button.setTextSize(12); button.setAllCaps(false);
        button.setTextColor(BACKGROUND); button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(BLUE));
        return button;
    }
    private void addControl(LinearLayout row, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
        params.setMargins(dp(2), 0, dp(2), 0); row.addView(button, params);
    }
    private void buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BACKGROUND);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout main = new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL);
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));
        header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), 0, dp(8), 0);
        TextView title = label(16); title.setText("랜들 미니 뷰어");
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button reload = button("새로고침"); reload.setOnClickListener(view -> web.reload());
        header.addView(reload);
        Button pip = button("PiP"); pip.setOnClickListener(view -> enterPip()); header.addView(pip);
        main.addView(header);
        web = new WebView(this); main.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(8), dp(4), dp(8), dp(8));
        status = label(12); status.setText("앱 안에서 랜들에 로그인해 주세요."); panel.addView(status);
        LinearLayout numberRow = new LinearLayout(this);
        for (int index = 0; index < 8; index++) {
            final int position = index;
            digits[index] = button("?"); digits[index].setPadding(0, 0, 0, 0);
            digits[index].setTextSize(20); digits[index].setEnabled(false);
            digits[index].setOnClickListener(view -> perform("select", position));
            addControl(numberRow, digits[index]);
        }
        panel.addView(numberRow);
        timer = label(11); timer.setTextColor(AMBER); panel.addView(timer);
        LinearLayout first = new LinearLayout(this), second = new LinearLayout(this);
        reroll = button("선택 자리 굴리기"); reroll.setOnClickListener(view -> perform("reroll", -1));
        selectAll = button("전체 선택"); selectAll.setOnClickListener(view -> perform("selectAll", -1));
        confirm = button("이 숫자로 확정"); confirm.setOnClickListener(view -> perform("confirm", -1));
        start = button("숫자 뽑기"); start.setOnClickListener(view -> perform("start", -1));
        for (Button control : new Button[]{reroll, selectAll, confirm, start}) control.setEnabled(false);
        addControl(first, reroll); addControl(first, selectAll); addControl(second, confirm); addControl(second, start);
        panel.addView(first); panel.addView(second); main.addView(panel);
        // Keep the WebView alive behind the native PiP summary; no separate session.
        pipOverlay = new LinearLayout(this); pipOverlay.setOrientation(LinearLayout.VERTICAL);
        pipOverlay.setGravity(Gravity.CENTER); pipOverlay.setBackgroundColor(BACKGROUND); pipOverlay.setPadding(dp(8), dp(4), dp(8), dp(4));
        pipPhase = label(12); pipPhase.setGravity(Gravity.CENTER);
        pipDigits = label(30); pipDigits.setGravity(Gravity.CENTER); pipDigits.setTypeface(Typeface.MONOSPACE, Typeface.BOLD); pipDigits.setText("????????");
        pipTimer = label(11); pipTimer.setTextColor(AMBER); pipTimer.setGravity(Gravity.CENTER);
        pipHint = label(10); pipHint.setGravity(Gravity.CENTER); pipHint.setText("자리 선택·인증은 앱을 펼쳐서 진행하세요.");
        pipOverlay.addView(pipPhase); pipOverlay.addView(pipDigits); pipOverlay.addView(pipTimer); pipOverlay.addView(pipHint);
        root.addView(pipOverlay, new FrameLayout.LayoutParams(-1, -1)); pipOverlay.setVisibility(View.GONE);
        setContentView(root); root.requestApplyInsets();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !"https".equals(request.getUrl().getScheme());
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                showState(new JSONObject()); lastState = "";
            }
            @Override public void onPageFinished(WebView view, String url) { readState(); }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) status.setText("연결하지 못했습니다. 인터넷을 확인하고 새로고침해 주세요.");
            }
        });
    }
    private boolean isPlayPage() {
        Uri uri = Uri.parse(web.getUrl() == null ? "" : web.getUrl());
        return "https".equals(uri.getScheme()) && "randle.lixx.kr".equals(uri.getHost()) && (uri.getPort() == -1 || uri.getPort() == 443)
                && ("/play".equals(uri.getPath()) || "/play/".equals(uri.getPath()));
    }
    private String script(String expression) {
        return "(function(){if(location.origin!=='https://randle.lixx.kr'||!/^\\/play\\/?$/.test(location.pathname))return null;"
                + viewerSource + ";return JSON.stringify(" + expression + ");})()";
    }
    private void readState() {
        if (web == null || destroyed || pendingRead) return;
        if (!isPlayPage()) { showState(new JSONObject()); return; }
        String url = web.getUrl(); pendingRead = true;
        web.evaluateJavascript(script("RandleViewer.snapshot(document)"), raw -> {
            pendingRead = false;
            if (destroyed || !isPlayPage() || !Objects.equals(url, web.getUrl())) return;
            try {
                Object decoded = new JSONTokener(raw).nextValue();
                if (!(decoded instanceof String)) return;
                String json = (String) decoded;
                if (!json.equals(lastState)) { lastState = json; showState(new JSONObject(json)); }
            } catch (Exception ignored) { status.setText("숫자 표시를 기다립니다."); }
        });
    }
    private void perform(String action, int position) {
        if (destroyed || !isPlayPage()) return;
        if (!Arrays.asList("select", "selectAll", "reroll", "confirm", "start").contains(action)) return;
        if ("select".equals(action) && (position < 0 || position >= 8)) return;
        // No JavaScript bridge is exposed to web pages. Every action is revalidated
        // by the same guarded manual-control code used by the desktop viewer.
        String expression = "RandleViewer.perform(document," + JSONObject.quote(action) + "," + position + ")";
        web.evaluateJavascript(script(expression), raw -> {
            if (destroyed) return;
            if (!"\"true\"".equals(raw)) Toast.makeText(this, "지금은 사용할 수 없는 버튼입니다.", Toast.LENGTH_SHORT).show();
            readState();
        });
    }
    private void showState(JSONObject value) {
        if (status == null || destroyed) return;
        state = value;
        JSONArray values = value.optJSONArray("digits"), selected = value.optJSONArray("selected"), selectable = value.optJSONArray("selectable");
        StringBuilder number = new StringBuilder();
        for (int index = 0; index < 8; index++) {
            String digit = values == null ? "?" : values.optString(index, "?");
            if (!digit.matches("[0-9?]")) digit = "?";
            number.append(digit); digits[index].setText(digit);
            digits[index].setContentDescription((index + 1) + "번째 자리 " + digit + " 리롤 선택");
            digits[index].setEnabled(selectable != null && selectable.optBoolean(index));
            boolean chosen = selected != null && selected.optBoolean(index);
            digits[index].setSelected(chosen);
            digits[index].setBackgroundTintList(android.content.res.ColorStateList.valueOf(chosen ? AMBER : BLUE));
        }
        status.setText(value.optString("status", "앱 안에서 랜들에 로그인해 주세요."));
        String countdown = value.optString("timer"); timer.setText(countdown); pipTimer.setText(countdown);
        pipDigits.setText(number); pipPhase.setText(value.optString("phase", "대기"));
        boolean human = "인증 필요".equals(value.optString("phase")); pipPhase.setTextColor(human ? AMBER : BLUE);
        pipHint.setText(human ? "앱을 펼쳐 직접 인증해 주세요." : "자리 선택·리롤은 앱을 펼쳐서 진행하세요.");
        reroll.setEnabled(value.optBoolean("canReroll")); selectAll.setEnabled(value.optBoolean("canSelectAll"));
        confirm.setEnabled(value.optBoolean("canConfirm")); start.setEnabled(value.optBoolean("canStart"));
        start.setText(value.optString("startLabel", "숫자 뽑기"));
        updatePipActions();
    }
    private RemoteAction remote(String command, String title, int icon, int requestCode, boolean enabled) {
        Intent intent = new Intent(CONTROL).setPackage(getPackageName()).putExtra("command", command);
        PendingIntent pending = PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        RemoteAction action = new RemoteAction(Icon.createWithResource(this, icon), title, title, pending);
        action.setEnabled(enabled); return action;
    }
    private PictureInPictureParams pipParams() {
        ArrayList<RemoteAction> actions = new ArrayList<>();
        actions.add(remote("confirm", "이 숫자로 확정", R.drawable.ic_confirm, 1, state.optBoolean("canConfirm")));
        actions.add(remote("start", state.optString("startLabel", "숫자 뽑기"), R.drawable.ic_next, 2, state.optBoolean("canStart")));
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9))
                .setActions(actions.subList(0, Math.min(actions.size(), getMaxNumPictureInPictureActions())));
        if (Build.VERSION.SDK_INT >= 31) builder.setSeamlessResizeEnabled(false);
        return builder.build();
    }
    private void updatePipActions() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return;
        String signature = state.optBoolean("canConfirm") + ":" + state.optBoolean("canStart") + ":" + state.optString("startLabel");
        if (!signature.equals(lastActions)) { lastActions = signature; setPictureInPictureParams(pipParams()); }
    }
    private void enterPip() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Toast.makeText(this, "이 기기에서는 PiP를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show(); return;
        }
        try {
            if (!enterPictureInPictureMode(pipParams())) Toast.makeText(this, "설정에서 이 앱의 화면 속 화면을 허용해 주세요.", Toast.LENGTH_LONG).show();
        } catch (IllegalStateException | IllegalArgumentException error) {
            Toast.makeText(this, "화면 속 화면 설정을 확인해 주세요.", Toast.LENGTH_LONG).show();
        }
    }
    @Override public void onPictureInPictureModeChanged(boolean inPip, Configuration configuration) {
        super.onPictureInPictureModeChanged(inPip, configuration);
        if (header == null) return;
        header.setVisibility(inPip ? View.GONE : View.VISIBLE); panel.setVisibility(inPip ? View.GONE : View.VISIBLE);
        pipOverlay.setVisibility(inPip ? View.VISIBLE : View.GONE);
    }
    @Override protected void onStart() {
        super.onStart(); active = true;
        if (web != null) web.onResume(); handler.removeCallbacks(poll); handler.post(poll);
    }
    @Override protected void onStop() {
        active = false; handler.removeCallbacks(poll);
        if (web != null) { web.onPause(); CookieManager.getInstance().flush(); }
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if (web != null) web.saveState(out); super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() {
        destroyed = true; handler.removeCallbacksAndMessages(null);
        if (web != null) { unregisterReceiver(receiver); web.destroy(); }
        super.onDestroy();
    }
    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
