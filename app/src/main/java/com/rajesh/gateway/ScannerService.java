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
        params.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        windowManager.addView(overlayView, params);

        setupLogic();
    }

    private void setupLogic() {
        dotIcon = overlayView.findViewById(R.id.dot_icon);
        menuLayout = overlayView.findViewById(R.id.menu_layout);
        glassScannerLayout = overlayView.findViewById(R.id.glass_scanner_layout);
        keyboardLayout = overlayView.findViewById(R.id.keyboard_layout);
        glassDecodedText = overlayView.findViewById(R.id.glass_decoded_text);
        magicInput = overlayView.findViewById(R.id.magic_input);

        dotIcon.setOnClickListener(v -> showMenu());

        overlayView.findViewById(R.id.btn_glass_scanner).setOnClickListener(v -> {
            menuLayout.setVisibility(View.GONE);
            glassScannerLayout.setVisibility(View.VISIBLE);
            updateWindowSize(true, false);
        });

        overlayView.findViewById(R.id.btn_auto_keyboard).setOnClickListener(v -> {
            menuLayout.setVisibility(View.GONE);
            keyboardLayout.setVisibility(View.VISIBLE);
            updateWindowSize(true, true);
        });

        overlayView.findViewById(R.id.btn_hide).setOnClickListener(v -> {
            menuLayout.setVisibility(View.GONE);
            dotIcon.setVisibility(View.VISIBLE);
            updateWindowSize(false, false);
        });

        overlayView.findViewById(R.id.btn_back_from_glass).setOnClickListener(v -> showMenu());
        overlayView.findViewById(R.id.btn_back_from_kb).setOnClickListener(v -> showMenu());

        overlayView.findViewById(R.id.btn_resize_glass).setOnClickListener(v -> {
            ViewGroup.LayoutParams lp = glassScannerLayout.getLayoutParams();
            if (lp.width == 300 * 3) {
                lp.width = 300; lp.height = 200;
            } else {
                lp.width = WindowManager.LayoutParams.MATCH_PARENT;
                lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            }
            glassScannerLayout.setLayoutParams(lp);
        });

        overlayView.findViewById(R.id.btn_exit).setOnClickListener(v -> {
            windowManager.removeView(overlayView);
            stopSelf();
        });

        magicInput.addTextChangedListener(new TextWatcher() {
            public void onTextChanged(CharSequence s, int st, int b, int c) { inject(encode(s.toString())); }
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void afterTextChanged(Editable s) {}
        });
    }

    private void showMenu() {
        dotIcon.setVisibility(View.GONE);
        glassScannerLayout.setVisibility(View.GONE);
        keyboardLayout.setVisibility(View.GONE);
        menuLayout.setVisibility(View.VISIBLE);
        updateWindowSize(true, false);
    }

    private void updateWindowSize(boolean fullScreen, boolean focusable) {
        if (fullScreen) {
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
        } else {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        }
        
        if (focusable) params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        else params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        
        windowManager.updateViewLayout(overlayView, params);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (glassScannerLayout.getVisibility() == View.VISIBLE) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) scan(root);
        }
    }

    private void scan(AccessibilityNodeInfo node) {
        if (node == null) return;
        if (node.getText() != null && node.getText().toString().contains("⠁")) {
            Rect r = new Rect(); node.getBoundsInScreen(r);
            Rect glass = new Rect(); glassScannerLayout.getGlobalVisibleRect(glass);
            if (Rect.intersects(glass, r)) glassDecodedText.setText(decode(node.getText().toString()));
        }
        for (int i = 0; i < node.getChildCount(); i++) scan(node.getChild(i));
    }

    private void inject(String t) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry");
        if (!list.isEmpty()) {
            Bundle b = new Bundle(); b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t);
            list.get(0).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
        }
    }

    private void initMaps() {
        String[] n = {"a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","w","x","y","z","অ","আ","ই","ঈ","উ","ঊ","ঋ","এ","ঐ","ও","ঔ","ক","খ","গ","ঘ","ঙ","চ","ছ","জ","ঝ","ঞ","ট","ঠ","ড","ঢ","ণ","ত","থ","দ","ধ","ন","প","ফ","ব","ভ","ম","য","র","ল","শ","ষ","স","হ","া","ি","ী","ু","ূ","ৃ","ে","ৈ","ো","ৌ","্","।"," "};
        String[] b = {"⠁","⠃","⠉","⠙","⠑","⠋","⠛","⠓","⠊","⠚","⠅","⠇","⠍","⠝","⠕","⠏","⠟","⠗","⠎","⠞","⠥","⠧","⠺","⠭","⠽","⠵","⡀","⡁","⡂","⡃","⡄","⡅","⡆","⡇","⡈","⡉","⡊","⡋","⡌","⡍","⡎","⡏","⡐","⡑","⡒","⡓","⡔","⡕","⡖","⡗","⡘","⡙","⡚","⡛","⡜","⡝","⡞","⡟","⡠","⡡","⡢","⡣","⡤","⡥","⡗","⡧","⡨","⡩","⡪","⡯","⡰","⡱","⡲","⡳","⡴","⡵","⡶","⡷","⡸","⡼","⡽"," "};
        for(int i=0; i<n.length; i++) { tToB.put(n[i].charAt(0), b[i].charAt(0)); bToT.put(b[i].charAt(0), n[i].charAt(0)); }
    }
    private String decode(String s) { StringBuilder r=new StringBuilder(); for(char c:s.toCharArray()) r.append(bToT.getOrDefault(c,c)); return r.toString(); }
    private String encode(String s) { StringBuilder r=new StringBuilder(); for(char c:s.toLowerCase().toCharArray()) r.append(tToB.getOrDefault(c,c)); return r.toString(); }
    public void onInterrupt() {}
}
