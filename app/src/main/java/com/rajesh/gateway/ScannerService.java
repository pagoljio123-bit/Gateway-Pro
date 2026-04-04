package com.rajesh.gateway;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.TextView;
import java.util.HashMap;
import java.util.List;

public class ScannerService extends AccessibilityService {
    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams params;
    private View dotIcon, menuLayout, glassScannerLayout, keyboardLayout;
    private TextView glassDecodedText;
    private EditText magicInput;
    private HashMap<Character, Character> tToB = new HashMap<>(), bToT = new HashMap<>();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        initMaps();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = LayoutInflater.from(this).inflate(R.layout.floating_layout, null);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(overlayView, params);

        setupSmartUI();
    }

    private void setupSmartUI() {
        dotIcon = overlayView.findViewById(R.id.dot_icon);
        menuLayout = overlayView.findViewById(R.id.menu_layout);
        glassScannerLayout = overlayView.findViewById(R.id.glass_scanner_layout);
        keyboardLayout = overlayView.findViewById(R.id.keyboard_layout);
        glassDecodedText = overlayView.findViewById(R.id.glass_decoded_text);
        magicInput = overlayView.findViewById(R.id.magic_input);

        // ১. ডট ক্লিক করলে মেনু আসবে
        dotIcon.setOnClickListener(v -> {
            dotIcon.setVisibility(View.GONE);
            menuLayout.setVisibility(View.VISIBLE);
            updateWindow(true, false);
        });

        // ২. মেনু অপশন
        overlayView.findViewById(R.id.btn_glass_scanner).setOnClickListener(v -> {
            menuLayout.setVisibility(View.GONE);
            glassScannerLayout.setVisibility(View.VISIBLE);
            updateWindow(true, false);
        });

        overlayView.findViewById(R.id.btn_auto_keyboard).setOnClickListener(v -> {
            menuLayout.setVisibility(View.GONE);
            keyboardLayout.setVisibility(View.VISIBLE);
            updateWindow(true, true); // কিবোর্ডের জন্য ফোকাস অন
        });

        overlayView.findViewById(R.id.btn_hide).setOnClickListener(v -> resetToDot());
        overlayView.findViewById(R.id.btn_back_glass).setOnClickListener(v -> showMenu());
        overlayView.findViewById(R.id.btn_back_kb).setOnClickListener(v -> showMenu());
        overlayView.findViewById(R.id.btn_exit).setOnClickListener(v -> { windowManager.removeView(overlayView); stopSelf(); });

        // ৩. ড্র্যাগ ও রিসাইজ লজিক
        overlayView.findViewById(R.id.drag_handle).setOnTouchListener(new View.OnTouchListener() {
            private int iX, iY; private float itX, itY;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN: iX=params.x; iY=params.y; itX=e.getRawX(); itY=e.getRawY(); return true;
                    case MotionEvent.ACTION_MOVE: params.x=iX+(int)(e.getRawX()-itX); params.y=iY+(int)(e.getRawY()-itY);
                        windowManager.updateViewLayout(overlayView, params); return true;
                } return false;
            }
        });

        overlayView.findViewById(R.id.resize_handle).setOnTouchListener(new View.OnTouchListener() {
            private int iW, iH; private float itX, itY;
            @Override public boolean onTouch(View v, MotionEvent e) {
                ViewGroup.LayoutParams lp = glassScannerLayout.getLayoutParams();
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN: iW=lp.width; iH=lp.height; itX=e.getRawX(); itY=e.getRawY(); return true;
                    case MotionEvent.ACTION_MOVE: lp.width=iW+(int)(e.getRawX()-itX); lp.height=iH+(int)(e.getRawY()-itY);
                        glassScannerLayout.setLayoutParams(lp); return true;
                } return false;
            }
        });

        // ৪. কিবোর্ড টাইপিং লজিক (WhatsApp Injector)
        magicInput.addTextChangedListener(new TextWatcher() {
            public void onTextChanged(CharSequence s, int st, int b, int c) { injectToWhatsApp(encode(s.toString())); }
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void afterTextChanged(Editable s) {}
        });
    }

    private void showMenu() {
        glassScannerLayout.setVisibility(View.GONE);
        keyboardLayout.setVisibility(View.GONE);
        menuLayout.setVisibility(View.VISIBLE);
        updateWindow(true, false);
    }

    private void resetToDot() {
        menuLayout.setVisibility(View.GONE);
        dotIcon.setVisibility(View.VISIBLE);
        updateWindow(false, false);
    }

    private void updateWindow(boolean isExpanded, boolean isFocusable) {
        if (isExpanded) { params.width = WindowManager.LayoutParams.WRAP_CONTENT; params.height = WindowManager.LayoutParams.WRAP_CONTENT; }
        else { params.width = 40; params.height = 100; } // ডটের জন্য ছোট জায়গা
        
        if (isFocusable) params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        else params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        
        windowManager.updateViewLayout(overlayView, params);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (glassScannerLayout.getVisibility() == View.VISIBLE) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) scanNodes(root);
        }
    }

    private void scanNodes(AccessibilityNodeInfo node) {
        if (node == null) return;
        if (node.getText() != null && node.getText().toString().contains("⠁")) {
            Rect r = new Rect(); node.getBoundsInScreen(r);
            int[] pos = new int[2]; glassScannerLayout.getLocationOnScreen(pos);
            Rect glass = new Rect(pos[0], pos[1], pos[0]+glassScannerLayout.getWidth(), pos[1]+glassScannerLayout.getHeight());
            if (Rect.intersects(glass, r)) glassDecodedText.setText("Decoded: " + decode(node.getText().toString()));
        }
        for (int i = 0; i < node.getChildCount(); i++) scanNodes(node.getChild(i));
    }

    private void injectToWhatsApp(String t) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        // হোয়াটসঅ্যাপের টাইপিং বক্স খোঁজা
        findAndFill(root, t);
    }

    private boolean findAndFill(AccessibilityNodeInfo node, String t) {
        if (node == null) return false;
        if (node.isEditable() && node.getClassName().toString().contains("EditText")) {
            Bundle b = new Bundle(); b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t);
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) if (findAndFill(node.getChild(i), t)) return true;
        return false;
    }

    private void initMaps() {
        String[] n = {"a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","w","x","y","z","অ","আ","ই","ঈ","উ","ঊ","ঋ","এ","ঐ","ও","ঔ","ক","খ","গ","ঘ","ঙ","চ","ছ","জ","ঝ","ঞ","ট","ঠ","ড","ঢ","ণ","ত","থ","দ","ধ","ন","প","ফ","ব","ভ","ম","য","র","ল","শ","ষ","স","হ","া","ি","ী","ু","ূ","ৃ","ে","ৈ","ো","ৌ","্","।"," "};
        String[] b = {"⠁","⠃","⠉","⠙","⠑","⠋","⠛","⠓","⠊","⠚","⠅","⠇","⠍","⠝","⠕","⠏","⠟","⠗","⠎","⠞","⠥","⠧","⠺","⠭","⠽","⠵","⡀","⡁","⡂","⡃","⡄","⡅","⡆","⡇","⡈","⡉","⡊","⡋","⡌","⡍","⡎","⡏","⡐","⡑","⡒","⡓","⡔","⡕","⡖","⡗","⡘","⡙","⡚","⡛","⡜","⡝","⡞","⡟","⡠","⡡","⡢","⡣","⡤","⡥","⡦","⡧","⡨","⡩","⡪","⡯","⡰","⡱","⡲","⡳","⡴","⡵","⡶","⡷","⡸","⡼","⡽"," "};
        for(int i=0; i<n.length; i++) { tToB.put(n[i].charAt(0), b[i].charAt(0)); bToT.put(b[i].charAt(0), n[i].charAt(0)); }
    }
    private String decode(String s) { StringBuilder r=new StringBuilder(); for(char c:s.toCharArray()) r.append(bToT.getOrDefault(c,c)); return r.toString(); }
    private String encode(String s) { StringBuilder r=new StringBuilder(); for(char c:s.toLowerCase().toCharArray()) r.append(tToB.getOrDefault(c,c)); return r.toString(); }
    public void onInterrupt() {}
}
