package com.rajesh.gateway;

import android.accessibilityservice.AccessibilityService;
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
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.HashMap;

public class ScannerService extends AccessibilityService {
    private WindowManager windowManager;
    private View controlView;
    private FrameLayout lensView;
    private WindowManager.LayoutParams controlParams, lensParams;
    
    private View dotIcon, menuLayout, keyboardLayout;
    private View btnLensOn, btnLensOff;
    private EditText magicInput;
    
    private boolean isLensActive = false;
    private HashMap<Character, Character> tToB = new HashMap<>(), bToT = new HashMap<>();
    
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isLensActive) return;
            lensView.removeAllViews();
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                scanAndDrawNodes(root);
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        initMaps();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        controlView = LayoutInflater.from(this).inflate(R.layout.floating_layout, null);
        controlParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        controlParams.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        windowManager.addView(controlView, controlParams);

        lensView = new FrameLayout(this);
        lensParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        windowManager.addView(lensView, lensParams);

        setupControls();
    }

    private void setupControls() {
        dotIcon = controlView.findViewById(R.id.dot_icon);
        menuLayout = controlView.findViewById(R.id.menu_layout);
        keyboardLayout = controlView.findViewById(R.id.keyboard_layout);
        btnLensOn = controlView.findViewById(R.id.btn_lens_on);
        btnLensOff = controlView.findViewById(R.id.btn_lens_off);
        magicInput = controlView.findViewById(R.id.magic_input);

        dotIcon.setOnClickListener(v -> {
            dotIcon.setVisibility(View.GONE);
            menuLayout.setVisibility(View.VISIBLE);
        });

        controlView.findViewById(R.id.btn_hide).setOnClickListener(v -> {
            menuLayout.setVisibility(View.GONE);
            dotIcon.setVisibility(View.VISIBLE);
        });

        controlView.findViewById(R.id.btn_exit).setOnClickListener(v -> {
            windowManager.removeView(controlView);
            windowManager.removeView(lensView);
            stopSelf();
        });

        btnLensOn.setOnClickListener(v -> {
            isLensActive = true;
            btnLensOn.setVisibility(View.GONE);
            btnLensOff.setVisibility(View.VISIBLE);
            handler.post(scanRunnable);
        });

        btnLensOff.setOnClickListener(v -> {
            isLensActive = false;
            lensView.removeAllViews();
            btnLensOff.setVisibility(View.GONE);
            btnLensOn.setVisibility(View.VISIBLE);
        });

        controlView.findViewById(R.id.btn_keyboard).setOnClickListener(v -> {
            menuLayout.setVisibility(View.GONE);
            keyboardLayout.setVisibility(View.VISIBLE);
            controlParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(controlView, controlParams);
        });

        controlView.findViewById(R.id.btn_close_kb).setOnClickListener(v -> {
            keyboardLayout.setVisibility(View.GONE);
            dotIcon.setVisibility(View.VISIBLE);
            controlParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(controlView, controlParams);
        });

        magicInput.addTextChangedListener(new TextWatcher() {
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                injectIntoWhatsApp(encode(s.toString()));
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isLensActive) return;
        handler.removeCallbacks(scanRunnable);
        handler.postDelayed(scanRunnable, 500); 
    }

    private void scanAndDrawNodes(AccessibilityNodeInfo node) {
        if (node == null) return;
        if (node.getText() != null) {
            String text = node.getText().toString();
            if (isBraille(text)) {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                boolean isPriyanka = (bounds.left < (screenWidth / 3));

                drawDecodedText(decode(text), bounds, isPriyanka);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            scanAndDrawNodes(node.getChild(i));
        }
    }

    // এই মেথডটা আমি আগেরবার দিতে ভুলে গিয়েছিলাম, এখন অ্যাড করে দিয়েছি
    private boolean isBraille(String text) {
        return text.contains("⠁") || text.contains("⡋") || text.contains("⡀");
    }

    private void drawDecodedText(String text, Rect bounds, boolean isPriyanka) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(14);
        textView.setTypeface(null, android.graphics.Typeface.BOLD);
        textView.setPadding(15, 10, 15, 10);
        
        if (isPriyanka) {
            textView.setBackgroundColor(Color.parseColor("#CC25D366")); 
            textView.setTextColor(Color.WHITE);
        } else {
            textView.setBackgroundColor(Color.parseColor("#CC000000"));
            textView.setTextColor(Color.parseColor("#00ffcc"));
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(bounds.width(), bounds.height());
        params.leftMargin = bounds.left;
        params.topMargin = bounds.top;
        lensView.addView(textView, params);
    }

    private void injectIntoWhatsApp(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) findAndSetText(root, text);
    }

    private boolean findAndSetText(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        if (node.isEditable() && node.getClassName().toString().contains("EditText")) {
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (findAndSetText(node.getChild(i), text)) return true;
        }
        return false;
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
