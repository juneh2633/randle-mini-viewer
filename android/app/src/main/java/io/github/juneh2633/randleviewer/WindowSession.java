package io.github.juneh2633.randleviewer;

import android.content.Context;
import android.content.MutableContextWrapper;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Transfers the existing page between the activity and the floating window. */
final class WindowSession {
    private static WebView pending;
    private WindowSession() {}
    static WebView create(Context owner) {
        return new WebView(new MutableContextWrapper(owner));
    }
    static void offer(WebView page) {
        if (page.getParent() instanceof ViewGroup) ((ViewGroup) page.getParent()).removeView(page);
        page.setWebViewClient(new WebViewClient());
        page.setWebChromeClient(new WebChromeClient());
        if (page.getContext() instanceof MutableContextWrapper) {
            MutableContextWrapper wrapper = (MutableContextWrapper) page.getContext();
            wrapper.setBaseContext(wrapper.getApplicationContext());
        }
        pending = page;
    }
    static WebView take(Context owner) {
        WebView page = pending; pending = null;
        if (page != null && page.getContext() instanceof MutableContextWrapper) ((MutableContextWrapper) page.getContext()).setBaseContext(owner);
        return page;
    }
}
