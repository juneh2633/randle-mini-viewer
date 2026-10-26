package io.github.juneh2633.randleviewer;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PipTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private MainActivity activity;
    private WebView web;

    private View find(View view, Class<?> type, String text) {
        if (type.isInstance(view) && (text == null || (view instanceof Button && text.contentEquals(((Button) view).getText())))) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = find(group.getChildAt(i), type, text); if (result != null) return result;
            }
        }
        return null;
    }
    private Button button(String text) { return (Button) find(activity.getWindow().getDecorView(), Button.class, text); }
    private void waitFor(BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 200; i++) {
            AtomicBoolean result = new AtomicBoolean();
            instrumentation.runOnMainSync(() -> result.set(condition.getAsBoolean()));
            if (result.get()) return;
            Thread.sleep(50);
        }
        fail("Timed out waiting for activity state");
    }
    private String evaluate(String source) throws Exception {
        AtomicReference<String> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        instrumentation.runOnMainSync(() -> web.evaluateJavascript(source, value -> { result.set(value); latch.countDown(); }));
        assertTrue(latch.await(5, TimeUnit.SECONDS)); return result.get();
    }
    @Test public void manualSelectionAndPipActionUseTheSamePage() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(current -> {
                activity = current;
                web = (WebView) find(current.getWindow().getDecorView(), WebView.class, null);
                web.stopLoading();
                StringBuilder cells = new StringBuilder();
                for (int i = 0; i < 8; i++) cells.append("<button data-roll-cell aria-label='")
                    .append(i + 1).append("번째 자리, 숫자 ").append(i + 1)
                    .append("' aria-pressed='false' onclick=\"this.setAttribute('aria-pressed','true');document.getElementById('reroll').disabled=false\">")
                    .append(i + 1).append("</button>");
                String html = "<meta charset='utf-8'><section aria-label='랜들 숫자 뽑기'><div aria-label='8자리 숫자'>" + cells
                    + "</div><button id='reroll' disabled>선택한 자리 다시 굴리기</button><button onclick=\"window.confirms++\">이 숫자로 확정 (20초 후 자동 확정)</button></section><script>window.confirms=0</script>";
                web.loadDataWithBaseURL("https://randle.lixx.kr/play", html, "text/html", "UTF-8", null);
            });
            waitFor(() -> button("3") != null && button("3").isEnabled());
            assertEquals("0", evaluate("window.confirms"));
            instrumentation.runOnMainSync(() -> button("3").performClick());
            waitFor(() -> button("3").isSelected() && button("선택 자리 굴리기").isEnabled());
            instrumentation.runOnMainSync(() -> button("PiP").performClick());
            waitFor(() -> activity.isInPictureInPictureMode());
            // Allow one normal update while PiP is paused but still visible.
            Thread.sleep(750);
            instrumentation.getTargetContext().sendBroadcast(new Intent("io.github.juneh2633.randleviewer.CONTROL")
                .setPackage("io.github.juneh2633.randleviewer").putExtra("command", "confirm"));
            for (int attempt = 0; attempt < 20 && !"1".equals(evaluate("window.confirms")); attempt++) Thread.sleep(100);
            assertEquals("1", evaluate("window.confirms"));
            Thread.sleep(750);
            assertEquals("1", evaluate("window.confirms"));
            Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
            assertNotNull(screenshot);
            File target = new File(activity.getExternalFilesDir(null), "pip-smoke.png");
            try (FileOutputStream output = new FileOutputStream(target)) { screenshot.compress(Bitmap.CompressFormat.PNG, 100, output); }
            screenshot.recycle();
        }
    }
}
