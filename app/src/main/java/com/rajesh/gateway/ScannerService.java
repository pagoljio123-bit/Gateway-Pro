package com.rajesh.gateway;

import android.accessibilityservice.AccessibilityService;
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
    private View controlView;
    private FrameLayout lensView;
    private WindowManager.LayoutParams params, lensParams;
    
    private View stealthBar, menuLayout, keyboardLayout;
    private View btnLensOn, btnLensOff;
    private EditText magicInput;
    
    private boolean isLensActive = false;
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

        controlView = LayoutInflater.from(this).inflate(R.layout.floating_layout, null);
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(controlView, params);

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

        // ১. ইউনিভার্সাল ড্র্যাগিং লজিক (যেকোনো জায়গায় টাচ করে মুভমেন্ট)
        View.OnTouchListener universalDrag = new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x; initialY = params.y;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(controlView, params);
                        return true;
                } return false;
            }
        };

        // ড্র্যাগিং অ্যাপ্লাই করা
        controlView.setOnTouchListener(universalDrag);
        stealthBar.setOnClickListener(v -> showMenu());

        // ২. কিবোর্ড ফিক্স ও অটো-ক্লিয়ার
        controlView.findViewById(R.id.btn_keyboard).setOnClickListener(v -> {
            menuLayout.setVisibility(View.GONE);
            keyboardLayout.setVisibility(View.VISIBLE);
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(controlView, params);
        });

        controlView.findViewById(R.id.btn_send_clear).setOnClickListener(v -> {
            String encodedCode = encode(magicInput.getText().toString());
            new Thread(() -> {
                if (sendToWhatsAppAndClick(encodedCode)) {
                    uiHandler.post(() -> {
                        magicInput.setText(""); // ভার্চুয়াল কিবোর্ড ক্লিয়ার
                    });
                }
            }).start();
        });

        // ৩. নেভিগেশন লজিক
        controlView.findViewById(R.id.btn_back_to_bar).setOnClickListener(v -> hideToBar());
        controlView.findViewById(R.id.btn_close_kb).setOnClickListener(v -> hideToBar());
        controlView.findViewById(R.id.btn_lens_on).setOnClickListener(v -> {
            isLensActive = true; btnLensOn.setVisibility(View.GONE); btnLensOff.setVisibility(View.VISIBLE);
            uiHandler.post(scanRunnable);
        });
        controlView.findViewById(R.id.btn_lens_off).setOnClickListener(v -> stopLens());
        controlView.findViewById(R.id.btn_exit).setOnClickListener(v -> {
            windowManager.removeView(controlView); windowManager.removeView(lensView); stopSelf();
        });

        magicInput.addTextChangedListener(new TextWatcher() {
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                injectOnly(encode(s.toString()));
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

    // হোয়াটসঅ্যাপে কোড পাঠানো, সেন্ড বাটনে ক্লিক করা এবং ক্লিয়ার করা
    private boolean sendToWhatsAppAndClick(String t) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        
        AccessibilityNodeInfo edit = findEditable(root);
        if (edit != null) {
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t);
            edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            
            // সেন্ড বাটন খুঁজে ক্লিক করা
            try { Thread.sleep(100); } catch (Exception e) {}
            clickWhatsAppSend(root);
            return true;
        }
        return false;
    }

    private void injectOnly(String t) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            AccessibilityNodeInfo edit = findEditable(root);
            if (edit != null) {
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t);
                edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            }
        }
    }

    private void clickWhatsAppSend(AccessibilityNodeInfo root) {
        // হোয়াটসঅ্যাপের সেন্ড বাটন সাধারণত 'Send' ডিসক্রিপশন বা নির্দিষ্ট আইডি দিয়ে থাকে
        List<AccessibilityNodeInfo> sendButtons = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send");
        if (sendButtons != null && !sendButtons.isEmpty()) {
            sendButtons.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } else {
            // আইডি না পেলে কন্টেন্ট ডেসক্রিপশন দিয়ে খোঁজা
            searchAndClickSend(root);
        }
    }

    private void searchAndClickSend(AccessibilityNodeInfo node) {
        if (node == null) return;
        if (node.getClassName() != null && node.getClassName().toString().contains("ImageView") && node.isClickable()) {
            if (node.getContentDescription() != null && node.getContentDescription().toString().equalsIgnoreCase("Send")) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK); return;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) searchAndClickSend(node.getChild(i));
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (n.isEditable() && n.getClassName().toString().contains("EditText")) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = findEditable(n.getChild(i)); if (c != null) return c;
        } return null;
    }

    // স্ক্যানারের আগের ১০০% নিখুঁত কোড
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
