package io.github.juneh2633.randleviewer;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.widget.Button;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
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
public class AutoTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private WebView page;
    private MainActivity activity;
    private View find(View view, Class<?> type, String text) {
        if (type.isInstance(view) && (text == null || (view instanceof Button && text.contentEquals(((Button) view).getText())))) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View result = find(((ViewGroup) view).getChildAt(i), type, text); if (result != null) return result;
        }
        return null;
    }
    private String evaluate(String source) throws Exception {
        AtomicReference<String> result = new AtomicReference<>(); CountDownLatch latch = new CountDownLatch(1);
        instrumentation.runOnMainSync(() -> page.evaluateJavascript(source, value -> { result.set(value); latch.countDown(); }));
        assertTrue(latch.await(5, TimeUnit.SECONDS)); return result.get();
    }
    private void waitFor(BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 240; i++) {
            AtomicBoolean result = new AtomicBoolean(); instrumentation.runOnMainSync(() -> result.set(condition.getAsBoolean()));
            if (result.get()) return; Thread.sleep(50);
        }
        fail("Timed out waiting for native automation state");
    }
    private void waitForJs(String expression) throws Exception {
        for (int i = 0; i < 160; i++) { if ("true".equals(evaluate(expression))) return; Thread.sleep(50); }
        fail("Timed out: " + expression);
    }
    private void command(String command) { instrumentation.runOnMainSync(() -> activity.runAutoCommand(command)); }
    @Test public void commandEnablesAutoAcrossFloatingTransferAndHumanCheckStopsIt() throws Exception {
        try (ParcelFileDescriptor descriptor = instrumentation.getUiAutomation().executeShellCommand("appops set io.github.juneh2633.randleviewer SYSTEM_ALERT_WINDOW allow");
             ParcelFileDescriptor.AutoCloseInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) { byte[] buffer = new byte[1024]; while (input.read(buffer) != -1) { } }
        activity = (MainActivity) instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            instrumentation.runOnMainSync(() -> {
                page = (WebView) find(activity.getWindow().getDecorView(), WebView.class, null); page.stopLoading();
                String html = "<meta charset='utf-8'><section aria-label='랜들 숫자 뽑기' id='game'><button onclick='draw()'>숫자 뽑기</button></section><script>"
                    + "window.starts=0;window.confirms=0;function draw(){starts++;let cells='';for(let i=0;i<8;i++)cells+=`<button data-roll-cell aria-label='${i+1}번째 자리, 숫자 ${i+1}' onclick=\"this.setAttribute('aria-pressed','true')\">${i+1}</button>`;document.getElementById('game').innerHTML=\"<div aria-label='8자리 숫자'>\"+cells+\"</div><button onclick='confirmRound()'>이 숫자로 확정 (15초 후 자동 확정)</button>\";}"
                    + "function confirmRound(){confirms++;document.getElementById('game').innerHTML=\"<button onclick='draw()'>한 판 더 굴리기</button>\";}</script>";
                page.setWebViewClient(new WebViewClient() {
                    @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) { return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8))); }
                });
                page.loadUrl("https://randle.lixx.kr/play");
            });
            waitForJs("typeof RandleAndroidAuto!=='undefined'");
            Thread.sleep(700); assertEquals("0", evaluate("starts"));
            command("not a command"); Thread.sleep(500); assertEquals("0", evaluate("starts"));
            command("/auto start"); waitForJs("starts===1");
            waitFor(() -> { Button digit = (Button) find(activity.getWindow().getDecorView(), Button.class, "3"); return digit != null && digit.isEnabled(); });
            instrumentation.runOnMainSync(() -> ((Button) find(activity.getWindow().getDecorView(), Button.class, "3")).performClick());
            waitForJs("!RandleAndroidAuto.status().running"); Thread.sleep(2200); assertEquals("0", evaluate("confirms"));
            command("/auto resume"); waitForJs("starts>=2");
            command("/auto pause"); waitForJs("!RandleAndroidAuto.status().running");
            String before = evaluate("JSON.stringify([starts,confirms])"); Thread.sleep(2200); assertEquals(before, evaluate("JSON.stringify([starts,confirms])"));
            command("/auto resume");
            instrumentation.runOnMainSync(() -> ((Button) find(activity.getWindow().getDecorView(), Button.class, "작은 창")).performClick());
            waitFor(() -> FloatingService.isRunning() && FloatingService.instance.window != null && FloatingService.instance.window.isAttachedToWindow());
            assertSame(page, FloatingService.instance.web); waitForJs("starts>=3");
            evaluate("document.body.insertAdjacentHTML('beforeend','<button id=human>인증하고 계속하기</button>');true");
            waitForJs("!RandleAndroidAuto.status().running");
            before = evaluate("JSON.stringify([starts,confirms])"); evaluate("document.getElementById('human').remove();true");
            Thread.sleep(2200); assertEquals(before, evaluate("JSON.stringify([starts,confirms])"));
            instrumentation.runOnMainSync(() -> FloatingService.instance.runAutoCommand("/auto stop"));
            waitForJs("!RandleAndroidAuto.status().enabled");
        } finally {
            instrumentation.runOnMainSync(() -> { if (FloatingService.instance != null) FloatingService.instance.stopSelf(); activity.finish(); });
            Thread.sleep(300);
        }
    }
}
