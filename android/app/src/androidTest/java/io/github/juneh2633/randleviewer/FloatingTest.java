package io.github.juneh2633.randleviewer;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.widget.Button;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FloatingTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private WebView original;
    private MainActivity activity;
    private View find(View view, Class<?> type, String text) {
        if (type.isInstance(view) && (text == null || (view instanceof Button && text.contentEquals(((Button) view).getText())))) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View result = find(((ViewGroup) view).getChildAt(i), type, text); if (result != null) return result;
        }
        return null;
    }
    private Button button(View root, String title) { return (Button) find(root, Button.class, title); }
    private void shell(String command) throws Exception {
        try (ParcelFileDescriptor descriptor = instrumentation.getUiAutomation().executeShellCommand(command);
             ParcelFileDescriptor.AutoCloseInputStream output = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            byte[] buffer = new byte[1024]; while (output.read(buffer) != -1) { }
        }
    }
    private String evaluate(String source) throws Exception {
        AtomicReference<String> result = new AtomicReference<>(); CountDownLatch latch = new CountDownLatch(1);
        instrumentation.runOnMainSync(() -> original.evaluateJavascript(source, value -> { result.set(value); latch.countDown(); }));
        assertTrue(latch.await(5, TimeUnit.SECONDS)); return result.get();
    }
    private void waitFor(BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 240; i++) {
            AtomicBoolean result = new AtomicBoolean(); instrumentation.runOnMainSync(() -> result.set(condition.getAsBoolean()));
            if (result.get()) return; Thread.sleep(50);
        }
        fail("Floating window did not reach the expected state");
    }
    @Test public void floatingWindowPreservesPageAndSupportsManualInputAndControls() throws Exception {
        shell("appops set io.github.juneh2633.randleviewer SYSTEM_ALERT_WINDOW allow");
        activity = (MainActivity) instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            instrumentation.runOnMainSync(() -> {
                original = (WebView) find(activity.getWindow().getDecorView(), WebView.class, null); original.stopLoading();
                StringBuilder digits = new StringBuilder();
                for (int i = 0; i < 8; i++) digits.append("<button data-roll-cell aria-label='").append(i + 1).append("번째 자리, 숫자 ").append(i + 1)
                    .append("' aria-pressed='false' onclick=\"this.setAttribute('aria-pressed','true');document.getElementById('reroll').disabled=false\">").append(i + 1).append("</button>");
                String html = "<meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><input id='answer' placeholder='인증 입력 시험'><section aria-label='랜들 숫자 뽑기'><div aria-label='8자리 숫자'>" + digits
                    + "</div><button id='all' onclick=\"document.querySelectorAll('[data-roll-cell]').forEach(e=>e.setAttribute('aria-pressed','true'))\">전체 선택</button><button id='reroll' disabled onclick=\"window.rerolls++;this.disabled=true\">선택한 자리 다시 굴리기</button>"
                    + "<button id='confirm' onclick=\"window.confirms++;this.hidden=true;document.getElementById('next').hidden=false\">이 숫자로 확정 (20초 후 자동 확정)</button>"
                    + "<button id='next' hidden onclick=\"window.starts++\">한 판 더 굴리기</button></section><script>window.confirms=0;window.rerolls=0;window.starts=0</script>";
                original.setWebViewClient(new WebViewClient() {
                    @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                        return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                    }
                });
                original.loadUrl("https://randle.lixx.kr/play");
            });
            waitFor(() -> button(activity.getWindow().getDecorView(), "3") != null && button(activity.getWindow().getDecorView(), "3").isEnabled());
            evaluate("document.getElementById('answer').value='keep';true");
            instrumentation.runOnMainSync(() -> button(activity.getWindow().getDecorView(), "3").performClick());
            waitFor(() -> button(activity.getWindow().getDecorView(), "3").isSelected());
            instrumentation.runOnMainSync(() -> button(activity.getWindow().getDecorView(), "작은 창").performClick());
            waitFor(() -> FloatingService.isRunning() && FloatingService.instance.window != null && FloatingService.instance.window.isAttachedToWindow());
            assertSame(original, FloatingService.instance.web);
            waitFor(() -> button(FloatingService.instance.window, "3").isSelected());
            assertEquals("\"keep\"", evaluate("document.getElementById('answer').value"));
            int[] originalWidth = new int[1];
            instrumentation.runOnMainSync(() -> {
                float density = activity.getResources().getDisplayMetrics().density;
                assertTrue(FloatingService.instance.window.getWidth() / density <= 261);
                assertTrue(FloatingService.instance.window.getHeight() / density <= 241);
                assertTrue(button(FloatingService.instance.window, "이 숫자로 확정").getHeight() / density <= 31);
            });
            instrumentation.runOnMainSync(() -> { originalWidth[0] = FloatingService.instance.window.getWidth(); button(FloatingService.instance.window, "크게").performClick(); });
            waitFor(() -> FloatingService.instance.window.getWidth() > originalWidth[0]);
            CountDownLatch rendered = new CountDownLatch(1);
            instrumentation.runOnMainSync(() -> original.postVisualStateCallback(1, new WebView.VisualStateCallback() {
                @Override public void onComplete(long requestId) { rendered.countDown(); }
            }));
            assertTrue(rendered.await(5, TimeUnit.SECONDS));
            // Touch an HTML input in the service window and type while the
            // originating activity is in the background.
            JSONObject point = new JSONObject((String) new JSONTokener(evaluate("JSON.stringify((()=>{let r=document.getElementById('answer').getBoundingClientRect();return{x:r.left+r.width/2,y:r.top+r.height/2,d:devicePixelRatio}})())")).nextValue());
            int[] location = new int[2]; instrumentation.runOnMainSync(() -> original.getLocationOnScreen(location));
            float x = location[0] + (float) (point.getDouble("x") * point.getDouble("d"));
            float y = location[1] + (float) (point.getDouble("y") * point.getDouble("d"));
            long now = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0), up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0);
            down.setSource(InputDevice.SOURCE_TOUCHSCREEN); up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            instrumentation.getUiAutomation().injectInputEvent(down, true); instrumentation.getUiAutomation().injectInputEvent(up, true); down.recycle(); up.recycle();
            Thread.sleep(300); instrumentation.sendStringSync("7");
            shell("screencap -p /data/local/tmp/randle-floating-input.png");
            String inputValue = evaluate("document.getElementById('answer').value");
            assertTrue("Input=" + inputValue + ", focus=" + evaluate("document.activeElement.id") + ", point=" + point + ", native=" + x + "," + y, inputValue.contains("7"));
            instrumentation.runOnMainSync(() -> button(FloatingService.instance.window, "전체 선택").performClick());
            waitFor(() -> button(FloatingService.instance.window, "8").isSelected());
            instrumentation.runOnMainSync(() -> button(FloatingService.instance.window, "선택 자리 굴리기").performClick());
            assertEquals("1", evaluate("window.rerolls"));
            instrumentation.runOnMainSync(() -> button(FloatingService.instance.window, "이 숫자로 확정").performClick());
            waitFor(() -> button(FloatingService.instance.window, "한 판 더 굴리기") != null && button(FloatingService.instance.window, "한 판 더 굴리기").isEnabled());
            instrumentation.runOnMainSync(() -> button(FloatingService.instance.window, "한 판 더 굴리기").performClick());
            assertEquals("1", evaluate("window.starts"));
            Thread.sleep(600); assertEquals("1", evaluate("window.confirms")); assertEquals("1", evaluate("window.starts"));
            instrumentation.runOnMainSync(() -> button(FloatingService.instance.window, "작게").performClick());
            waitFor(() -> FloatingService.instance.window.getWidth() <= originalWidth[0]);
            shell("screencap -p /data/local/tmp/randle-floating-smoke.png");
            instrumentation.runOnMainSync(() -> button(FloatingService.instance.window, "앱").performClick());
            waitFor(() -> !FloatingService.isRunning() && original.getParent() != null && find(activity.getWindow().getDecorView(), WebView.class, null) == original);
            assertTrue(evaluate("document.getElementById('answer').value").contains("7"));
        } finally {
            instrumentation.runOnMainSync(() -> { if (FloatingService.instance != null) FloatingService.instance.stopSelf(); activity.finish(); });
        }
    }
}
