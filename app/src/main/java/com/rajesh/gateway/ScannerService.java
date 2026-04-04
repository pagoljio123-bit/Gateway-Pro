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
    private WindowManager.LayoutParams params, lensParams, triggerParams;
    
    private View stealthBar, menuLayout, keyboardLayout;
    private View btnLensOn, btnLensOff;
    private EditText magicInput;
    
    private boolean isLensActive = false;
    private long lastTapTime = 0;
    private HashMap<Character, Character> bToT = new HashMap<>(), tToB = new HashMap<>();
    
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable typeRunnable;
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

        controlView = LayoutInflater.from(this).inflate(R.layout.floating_layout, null);
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(controlView, params);

        triggerView = new View(this);
        triggerParams = new WindowManager.LayoutParams(
                50, 50,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        triggerParams.gravity = Gravity.TOP | Gravity.END;
        windowManager.addView(triggerView, triggerParams);

        lensView = new FrameLayout(this);
        lensParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        windowManager.addView(lensView, lensParams);

        setupStealthUI();
    }

    private void setupStealthUI() {
        stealthBar = controlView.findViewById(R.id.stealth_bar);
        menuLayout = controlView.findViewById(R.id.menu_layout);
        keyboardLayout = controlView.findViewById(R.id.keyboard_layout);
        btnLensOn = controlView.findViewById(R.id.btn_lens_on);
        btnLensOff = controlView.findViewById(R.id.btn_lens_off);
        magicInput = controlView.findViewById(R.id.magic_input);

        stealthBar.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long touchStartTime;

            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x; initialY = params.y;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                        touchStartTime = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(controlView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        long touchDuration = System.currentTimeMillis() - touchStartTime;
                        float distance = Math.abs(event.getRawX() - initialTouchX) + Math.abs(event.getRawY() - initialTouchY);
                        if (touchDuration < 200 && distance < 10) showMenu();
                        return true;
                } return false;
            }
        });

        triggerView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastTapTime < 300) { showMenu(); }
                lastTapTime = currentTime;
            }
            return true;
        });

        controlView.findViewById(R.id.btn_keyboard).setOnClickListener(v -> {
            menuLayout.setVisibility(View.GONE);
            keyboardLayout.setVisibility(View.VISIBLE);
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(controlView, params);
        });

        controlView.findViewById(R.id.btn_back_to_bar).setOnClickListener(v -> hideToBar());
        controlView.findViewById(R.id.btn_close_kb).setOnClickListener(v -> hideToBar());
        controlView.findViewById(R.id.btn_lens_on).setOnClickListener(v -> {
            isLensActive = true; btnLensOn.setVisibility(View.GONE); btnLensOff.setVisibility(View.VISIBLE);
            uiHandler.post(scanRunnable);
        });
        controlView.findViewById(R.id.btn_lens_off).setOnClickListener(v -> stopLens());
        controlView.findViewById(R.id.btn_exit).setOnClickListener(v -> {
            windowManager.removeView(controlView); windowManager.removeView(lensView); windowManager.removeView(triggerView); stopSelf();
        });

        magicInput.addTextChangedListener(new TextWatcher() {
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (typeRunnable != null) uiHandler.removeCallbacks(typeRunnable);
                final String code = encode(s.toString());
                typeRunnable = () -> new Thread(() -> injectOnly(code)).start();
                uiHandler.postDelayed(typeRunnable, 150); 
            }
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void afterTextChanged(Editable s) {}
        });
    }

    private void showMenu() { stealthBar.setVisibility(View.GONE); menuLayout.setVisibility(View.VISIBLE); }
    
    private void hideToBar() {
        menuLayout.setVisibility(View.GONE); keyboardLayout.setVisibility(View.GONE);
        stealthBar.setVisibility(View.VISIBLE);
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        windowManager.updateViewLayout(controlView, params);
    }
    
    private void stopLens() { isLensActive = false; lensView.removeAllViews(); btnLensOff.setVisibility(View.GONE); btnLensOn.setVisibility(View.VISIBLE); }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // ম্যাজিক অটো-ক্লিয়ার: হোয়াটসঅ্যাপের সেন্ড বাটনে ক্লিক করলেই কিবোর্ড ফাঁকা হয়ে যাবে
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            AccessibilityNodeInfo node = event.getSource();
            if (checkIfWhatsAppSendButton(node)) {
                uiHandler.post(() -> {
                    if (magicInput != null) magicInput.setText(""); // লেখা ক্লিয়ার করে দেবে
                });
            }
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null && !pkg.toString().contains("whatsapp") && !pkg.toString().contains("gateway")) {
                uiHandler.post(() -> {
                    if (keyboardLayout.getVisibility() == View.VISIBLE) hideToBar();
                });
            }
        }

        if (!isLensActive) return;
        uiHandler.removeCallbacks(scanRunnable);
        uiHandler.postDelayed(scanRunnable, 300); 
    }

    // হোয়াটসঅ্যাপের সেন্ড বাটন নিখুঁতভাবে চেনার স্পেশাল লজিক (বাংলা/ইংরেজি সব সাপোর্ট করবে)
    private boolean checkIfWhatsAppSendButton(AccessibilityNodeInfo node) {
        if (node == null) return false;
        
        String viewId = node.getViewIdResourceName();
        CharSequence desc = node.getContentDescription();
        
        if (viewId != null && viewId.toLowerCase().endsWith("id/send")) return true;
        if (desc != null && (desc.toString().toLowerCase().contains("send") || desc.toString().contains("পাঠান"))) return true;
        
        // অনেক সময় বাটনের পেছনের বক্সে ক্লিক পড়ে যায়, তাই চাইল্ডগুলোও চেক করা হলো
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                String cViewId = child.getViewIdResourceName();
                CharSequence cDesc = child.getContentDescription();
                if (cViewId != null && cViewId.toLowerCase().endsWith("id/send")) return true;
                if (cDesc != null && (cDesc.toString().toLowerCase().contains("send") || cDesc.toString().contains("পাঠান"))) return true;
            }
        }
        return false;
    }

    private void injectOnly(String t) {
        List<AccessibilityWindowInfo> wins = getWindows(); if (wins == null) return;
        for (AccessibilityWindowInfo w : wins) {
            if (w.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                AccessibilityNodeInfo r = w.getRoot();
                if (r != null && r.getPackageName() != null && r.getPackageName().toString().contains("whatsapp")) {
                    AccessibilityNodeInfo edit = findEditable(r);
                    if (edit != null) {
                        Bundle args = new Bundle();
                        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t);
                        edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                        return;
                    }
                }
            }
        }
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isEditable() && n.getClassName().toString().contains("EditText")) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = findEditable(n.getChild(i)); if (c != null) return c;
        } return null;
    }

    private void scanAndDrawNodes(AccessibilityNodeInfo node) {
        if (node == null) return;
        String viewId = node.getViewIdResourceName();
        if (viewId != null && (viewId.contains("quoted") || viewId.contains("reply"))) return;
        if (node.getText() != null && isBraille(node.getText().toString())) {
            Rect b = new Rect(); node.getBoundsInScreen(b);
            int sw = getResources().getDisplayMetrics().widthPixels;
            drawDecoded(decode(node.getText().toString()), b, b.centerX() < (sw / 2));
        }
        for (int i = 0; i < node.getChildCount(); i++) scanAndDrawNodes(node.getChild(i));
    }

    private boolean isBraille(String t) {
        if (t == null || t.trim().isEmpty()) return false;
        for (char c : t.toCharArray()) if (c >= '\u2800' && c <= '\u28FF') return true;
        return false;
    }

    private void drawDecoded(String t, Rect b, boolean isP) {
        int[] pos = new int[2]; lensView.getLocationOnScreen(pos);
        TextView tv = new TextView(this); tv.setText(t); tv.setTextSize(14); tv.setGravity(Gravity.CENTER);
        tv.setBackgroundColor(isP ? Color.parseColor("#F2FFFFFF") : Color.parseColor("#E60055FF"));
        tv.setTextColor(isP ? Color.BLACK : Color.WHITE);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(b.width(), b.height());
        p.leftMargin = b.left - pos[0]; p.topMargin = b.top - pos[1];
        lensView.addView(tv, p);
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
