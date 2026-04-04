package com.rajesh.gateway;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.HashMap;
import java.util.List;

public class ScannerService extends AccessibilityService {
    private WindowManager windowManager;
    private FrameLayout overlayContainer;
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private HashMap<Character, Character> bToT = new HashMap<>();
    private long lastReadTime = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        initMaps();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayContainer = (FrameLayout) LayoutInflater.from(this).inflate(R.layout.floating_layout, null);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(overlayContainer, params);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // ল্যাগ এড়াতে আমরা সব জটিল কাজ ব্যাকগ্রাউন্ড থ্রেডে নিয়ে যাবো
        if (System.currentTimeMillis() - lastReadTime < 100) return; // খুব ঘন ঘন স্ক্যান না করা
        lastReadTime = System.currentTimeMillis();

        new Thread(() -> {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            // মেইন থ্রেডে ওভারলে পরিষ্কার করা
            uiHandler.post(() -> overlayContainer.removeAllViews());
            // স্ক্যান শুরু
            scanNodes(root);
        }).start();
    }

    private void scanNodes(AccessibilityNodeInfo node) {
        if (node == null) return;
        if (node.getText() != null) {
            String text = node.getText().toString();
            if (isBraille(text)) {
                Rect rect = new Rect();
                node.getBoundsInScreen(rect);
                
                // ডিকোড করা এবং মেইন থ্রেডে পাঠানো
                final String decoded = decode(text);
                // এটি চ্যাটের আইডি বা কালার অনুযায়ী ঠিক করবে (সবুজ প্রিয়াঙ্কা, সাদা রাজেশ)
                final boolean isPriyanka = node.getViewIdResourceName() != null && node.getViewIdResourceName().contains("message_text_green");

                uiHandler.post(() -> addDecodedTextOverlay(decoded, rect, isPriyanka));
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) scanNodes(node.getChild(i));
    }

    private void addDecodedTextOverlay(String decoded, Rect rect, boolean isPriyanka) {
        TextView textView = new TextView(this);
        textView.setText(decoded);
        textView.setTextSize(14);
        textView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textView.setPadding(10, 5, 10, 5);

        // কালার সেট করা (সবুজ বা সাদা বাবল অনুযায়ী)
        if (isPriyanka) {
            textView.setTextColor(Color.WHITE);
            textView.setBackgroundColor(Color.parseColor("#4400ffcc")); // হালকা সবুজ
        } else {
            textView.setTextColor(Color.parseColor("#00ffcc"));
            textView.setBackgroundColor(Color.parseColor("#66000000")); // হালকা কালো
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(rect.width(), rect.height());
        params.leftMargin = rect.left;
        params.topMargin = rect.top;
        params.gravity = Gravity.TOP | Gravity.START;
        overlayContainer.addView(textView, params);
    }

    private boolean isBraille(String text) {
        return text.contains("⠁") || text.contains("⡋") || text.contains("⡀");
    }

    private void initMaps() {
        String[] n = {"a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","w","x","y","z","অ","আ","ই","ঈ","উ","ঊ","ঋ","এ","ঐ","ও","ঔ","ক","kh","g","gh","ঙ","চ","ছ","জ","ঝ","ঞ","ট","ঠ","ড","ঢ","ণ","ত","থ","দ","ধ","ন","প","ফ","ব","ভ","ম","য","র","ল","শ","ষ","স","হ","া","ি","ী","ু","ূ","ৃ","ে","ৈ","ো","ৌ","্","।"," ","1","2","3","4","5","6","7","8","9","0"};
        String[] b = {"⠁","⠃","⠉","⠙","⠑","⠋","⠛","⠓","⠊","⠚","⠅","⠇","⠍","⠝","⠕","⠏","⠟","⠗","⠎","⠞","⠥","⠧","⠺","⠭","⠽","⠵","⡀","⡁","⡂","⡃","⡄","⡅","⡆","⡇","⡈","⡉","⡊","⡋","⡌","⡍","⡎","⡏","⡐","⡑","⡒","⡓","⡔","⡕","⡖","⡗","⡘","⡙","⡚","⡛","⡜","⡝","⡞","⡟","⡠","⡡","⡢","⡣","⡤","⡥","⡦","⡧","⡨","⡩","⡪","⡯","⡰","⡱","⡲","⡳","⡴","⡵","⡶","⡷","⡸","⡼","⡽"," ","⠂","⠆","⠒","⠲","⠢","⠖","⠶","⠦","⠔","⠴"};
        for(int i=0; i<n.length; i++) bToT.put(b[i].charAt(0), n[i].charAt(0));
    }
    private String decode(String s) { StringBuilder r=new StringBuilder(); for(char c:s.toCharArray()) r.append(bToT.getOrDefault(c,c)); return r.toString(); }
    @Override public void onInterrupt() {}
}
