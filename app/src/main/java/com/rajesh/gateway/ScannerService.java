package com.rajesh.gateway;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityWindowInfo;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.HashMap;
import java.util.List;

public class ScannerService extends AccessibilityService {
    private WindowManager windowManager;
    private View controlView, triggerView;
    private FrameLayout lensView;
    private WindowManager.LayoutParams controlParams, lensParams, triggerParams;
    
    private View stealthBar, menuLayout, keyboardLayout;
    private View btnLensOn, btnLensOff;
    private EditText magicInput;
    
    private boolean isLensActive = false;
    private long lastTapTime = 0;
    private HashMap<Character, Character> bToT = new HashMap<>(), tToB = new HashMap<>();
    
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable scanRunnable = () -> {
        if (!isLensActive) return;
        lensView.removeAllViews();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) scanAndDrawNodes(root);
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        initMaps();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // ১. কন্ট্রোল ভিউ (স্টিলথ বার ও মেনু)
        controlView = LayoutInflater.from(this).inflate(R.layout.floating_layout, null);
        controlParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        controlParams.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        windowManager.addView(controlView, controlParams);

        // ২. অদৃশ্য ট্রিগার বিন্দু (ডাবল ট্যাপের জন্য)
        triggerView = new View(this);
        triggerParams = new WindowManager.LayoutParams(
                50, 50, // মাত্র ৫০ পিক্সেল সাইজ
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        triggerParams.gravity = Gravity.TOP | Gravity.END;
        windowManager.addView(triggerView, triggerParams);

        // ৩. লেন্স পর্দা
        lensView = new FrameLayout(this);
        lensParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        windowManager.addView(lensView, lensParams);

        setupSmartLogic();
    }

    private void setupSmartLogic() {
        stealthBar = controlView.findViewById(R.id.stealth_bar);
        menuLayout = controlView.findViewById(R.id.menu_layout);
        keyboardLayout = controlView.findViewById(R.id.keyboard_layout);
        btnLensOn = controlView.findViewById(R.id.btn_lens_on);
        btnLensOff = controlView.findViewById(R.id.btn_lens_off);
        magicInput = controlView.findViewById(R.id.magic_input);

        // ড্র্যাগিং লজিক (বার সরানো)
        stealthBar.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY; private float initialTouchX, initialTouchY;
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = controlParams.x; initialY = controlParams.y;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        controlParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        controlParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(controlView, controlParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (Math.abs(event.getRawX() - initialTouchX) < 5) showMenu();
                        return true;
                } return false;
            }
        });

        // ডাবল ট্যাপ ম্যাজিক (অদৃশ্য বিন্দুর ওপর)
        triggerView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastTapTime < 300) { // ৩০০ মিলিসেকেন্ডের মধ্যে দুবার টাচ
                    showMenu();
                }
                lastTapTime = currentTime;
            }
            return true;
        });

        controlView.findViewById(R.id.btn_full_hide).setOnClickListener(v -> {
            menuLayout.setVisibility(View.GONE);
            stealthBar.setVisibility(View.GONE); // পুরোপুরি অদৃশ্য
        });

        controlView.findViewById(R.id.btn_keyboard).setOnClickListener(v -> {
            menuLayout.setVisibility(View.GONE);
            keyboardLayout.setVisibility(View.VISIBLE);
            controlParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(controlView, controlParams);
        });

        controlView.findViewById(R.id.btn_lens_on).setOnClickListener(v -> {
            isLensActive = true; btnLensOn.setVisibility(View.GONE); btnLensOff.setVisibility(View.VISIBLE);
            uiHandler.post(scanRunnable);
        });

        controlView.findViewById(R.id.btn_lens_off).setOnClickListener(v -> stopLens());
        controlView.findViewById(R.id.btn_close_kb).setOnClickListener(v -> closeKeyboard());
        controlView.findViewById(R.id.btn_hide).setOnClickListener(v -> hideToBar());
        controlView.findViewById(R.id.btn_exit).setOnClickListener(v -> {
            windowManager.removeView(controlView); windowManager.removeView(lensView); windowManager.removeView(triggerView); stopSelf();
        });

        magicInput.addTextChangedListener(new TextWatcher() {
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                final String code = encode(s.toString());
                new Thread(() -> injectIntoWhatsApp(code)).start();
            }
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void afterTextChanged(Editable s) {}
        });
    }

    private void showMenu() {
        stealthBar.setVisibility(View.GONE);
        menuLayout.setVisibility(View.VISIBLE);
    }

    private void hideToBar() {
        menuLayout.setVisibility(View.GONE);
        stealthBar.setVisibility(View.VISIBLE);
    }

    private void stopLens() { isLensActive = false; lensView.removeAllViews(); btnLensOff.setVisibility(View.GONE); btnLensOn.setVisibility(View.VISIBLE); }

    private void closeKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(magicInput.getWindowToken(), 0);
        keyboardLayout.setVisibility(View.GONE); stealthBar.setVisibility(View.VISIBLE);
        controlParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        windowManager.updateViewLayout(controlView, controlParams);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isLensActive) return;
        uiHandler.removeCallbacks(scanRunnable);
        uiHandler.postDelayed(scanRunnable, 300); 
    }

    private void scanAndDrawNodes(AccessibilityNodeInfo node) {
        if (node == null) return;
        String viewId = node.getViewIdResourceName();
        if (viewId != null && (viewId.contains("quoted") || viewId.contains("reply"))) return;
        if (node.getText() != null && isBraille(node.getText().toString())) {
            Rect b = new Rect(); node.getBoundsInScreen(b);
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            drawDecodedText(decode(node.getText().toString()), b, b.centerX() < (screenWidth / 2));
        }
        for (int i = 0; i < node.getChildCount(); i++) scanAndDrawNodes(node.getChild(i));
    }

    private boolean isBraille(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        for (char c : text.toCharArray()) if (c >= '\u2800' && c <= '\u28FF') return true;
        return false;
    }

    private void drawDecodedText(String text, Rect b, boolean isP) {
        int[] pos = new int[2]; lensView.getLocationOnScreen(pos);
        TextView tv = new TextView(this); tv.setText(text); tv.setTextSize(14); tv.setGravity(Gravity.CENTER);
        tv.setBackgroundColor(isP ? Color.parseColor("#F2FFFFFF") : Color.parseColor("#E60055FF"));
        tv.setTextColor(isP ? Color.BLACK : Color.WHITE);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(b.width(), b.height());
        p.leftMargin = b.left - pos[0]; p.topMargin = b.top - pos[1];
        lensView.addView(tv, p);
    }

    private void injectIntoWhatsApp(String t) {
        List<AccessibilityWindowInfo> wins = getWindows(); if (wins == null) return;
        for (AccessibilityWindowInfo w : wins) {
            if (w.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r != null && r.getPackageName() != null && r.getPackageName().toString().contains("whatsapp")) {
                    AccessibilityNodeInfo edit = findEdit(r);
                    if (edit != null) {
                        Bundle args = new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t);
                        edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args); return;
                    }
                }
            }
        }
    }

    private AccessibilityNodeInfo findEdit(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isEditable() && n.getClassName() != null && n.getClassName().toString().endsWith("EditText")) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = findEdit(n.getChild(i)); if (c != null) return c;
        } return null;
    }

    private void initMaps() {
        String[] n = {"a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","w","x","y","z","অ","আ","ই","ঈ","উ","ঊ","ঋ","এ","ঐ","ও","ঔ","ক","খ","গ","ঘ","ঙ","চ","ছ","জ","ঝ","ঞ","ট","ঠ","ড","ঢ","ণ","ত","থ","দ","ধ","ন","প","ফ","ব","ভ","ম","য","র","ল","শ","ষ","স","হ","া","ি","ী","ু","ূ","ৃ","ে","ৈ","ো","ৌ","্","।"," ","1","2","3","4","5","6","7","8","9","0"};
        String[] b = {"⠁","⠃","⠉","⠙","⠑","⠋","⠛","⠓","⠊","⠚","⠅","⠇","⠍","⠝","⠕","⠏","⠟","⠗","⠎","⠞","⠥","⠧","⠺","⠭","⠽","⠵","⡀","⡁","⡂","⡃","⡄","⡅","⡆","⡇","⡈","⡉","⡊","⡋","⡌","⡍","⡎","⡏","⡐","⡑","⡒","⡓","⡔","⡕","⡖","⡗","⡘","⡙","⡚","⡛","⡜","⡝","⡞","⡟","⡠","⡡","⡢","⡣","⡤","⡥","⡦","⡧","⡨","⡩","⡪","⡯","⡰","⡱","⡲","⡳","⡴","⡵","⡶","⡷","⡸","⡼","⡽"," ","⠂","⠆","⠒","⠲","⠢","⠖","⠶","⠦","⠔","⠴"};
        for(int i=0; i<n.length; i++) { tToB.put(n[i].charAt(0), b[i].charAt(0)); bToT.put(b[i].charAt(0), n[i].charAt(0)); }
    }
    private String decode(String s) { StringBuilder r=new StringBuilder(); for(char c:s.toCharArray()) r.append(bToT.getOrDefault(c,c)); return r.toString(); }
    private String encode(String s) { StringBuilder r=new StringBuilder(); for(char c:s.toLowerCase().toCharArray()) r.append(tToB.getOrDefault(c,c)); return r.toString(); }
    @Override public void onInterrupt() {}
}
