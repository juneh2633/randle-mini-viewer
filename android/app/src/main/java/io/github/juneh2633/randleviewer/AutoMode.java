package io.github.juneh2633.randleviewer;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;

final class AutoMode {
    private AutoMode() {}
    static String source(Context context) throws IOException {
        try (InputStream input = context.getAssets().open("auto.js")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
    }
    static boolean valid(String command) { return Arrays.asList("/auto start", "/auto stop", "/auto pause", "/auto resume").contains(command); }
    static void pause(WebView page, String reason) {
        if (page == null || page.getUrl() == null) return;
        Uri uri = Uri.parse(page.getUrl());
        if (!"https".equals(uri.getScheme()) || !"randle.lixx.kr".equals(uri.getHost())) return;
        page.evaluateJavascript("if(location.origin==='https://randle.lixx.kr'&&window.RandleAndroidAuto)RandleAndroidAuto.pause(" + JSONObject.quote(reason) + ");", null);
    }
    static AlertDialog dialog(Context context, boolean overlay, Consumer<String> submit) {
        EditText input = new EditText(context); input.setSingleLine(true); input.setHint("명령을 입력하세요");
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle("명령 입력").setView(input)
            .setPositiveButton("실행", (item, which) -> {
                String command = input.getText().toString().trim();
                if (valid(command)) submit.accept(command);
                else Toast.makeText(context, "알 수 없는 명령입니다.", Toast.LENGTH_SHORT).show();
            }).setNegativeButton("취소", null).create();
        if (overlay && dialog.getWindow() != null) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        dialog.show();
        return dialog;
    }
    static JSONObject state(JSONObject data) { JSONObject state = data.optJSONObject("automation"); return state == null ? new JSONObject() : state; }
    static String message(JSONObject data, String fallback) {
        JSONObject state = state(data);
        return state.optBoolean("enabled") ? (state.optBoolean("running") ? "자동 실행 · " : "자동 정지 · ") + state.optString("message") : fallback;
    }
}
