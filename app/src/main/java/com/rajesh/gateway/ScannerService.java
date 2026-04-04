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
    private WindowManager.LayoutParams controlParams, lensParams;
    
    private View dotIcon, menuLayout, keyboardLayout;
    private View btnLensOn, btnLensOff;
    private EditText magicInput;
    
    private boolean isLensActive = false;
    private HashMap<Character, Character> tToB = new HashMap<>(), bToT = new HashMap<>();
    
    private Handler uiHandler = new Handler(Looper.getMainLooper());
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

        // কন্ট্রোলার উইন্ডো
        controlView = LayoutInflater.from(this).inflate(R.layout.floating_layout, null);
        controlParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        controlParams.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        windowManager.addView(controlView, controlParams);

        // লেন্স উইন্ডো
        lensView = new FrameLayout(this);
        lensParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
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

        controlView.findViewById(R.id.btn_hide).setOnClickListener(v -> hideMenus());

        controlView.findViewById(R.id.btn_exit).setOnClickListener(v -> {
            windowManager.removeView(controlView);
            windowManager.removeView(lensView);
            stopSelf();
        });

        btnLensOn.setOnClickListener(v -> {
            isLensActive = true;
            btnLensOn.setVisibility(View.GONE);
            btnLensOff.setVisibility(View.VISIBLE);
            uiHandler.post(scanRunnable);
        });

        btnLensOff.setOnClickListener(v -> stopLens());

        controlView.findViewById(R.id.btn_keyboard).setOnClickListener(v -> {
            menuLayout.setVisibility(View.GONE);
            keyboardLayout.setVisibility(View.VISIBLE);
            controlParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(controlView, controlParams);
            magicInput.requestFocus();
        });

        controlView.findViewById(R.id.btn_close_kb).setOnClickListener(v -> closeKeyboard());

        // হ্যাং ফিক্স: Thread সরানো হয়েছে, সরাসরি মেইন থ্রেড থেকে হোয়াটসঅ্যাপে ইনজেক্ট হবে
        magicInput.addTextChangedListener(new TextWatcher() {
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                injectIntoWhatsApp(encode(s.toString()));
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void afterTextChanged(Editable s) {}
        });
    }

    private void hideMenus() {
        menuLayout.setVisibility(View.GONE);
        dotIcon.setVisibility(View.VISIBLE);
    }

    private void stopLens() {
        isLensActive = false;
        lensView.removeAllViews();
        btnLensOff.setVisibility(View.GONE);
        btnLensOn.setVisibility(View.VISIBLE);
    }

    private void closeKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(magicInput.getWindowToken(), 0);
        
        keyboardLayout.setVisibility(View.GONE);
        dotIcon.setVisibility(View.VISIBLE);
        controlParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        windowManager.updateViewLayout(controlView, controlParams);
    }

    // হোয়াটসঅ্যাপ থেকে বেরোলেই সব হাইড হয়ে যাবে
    private void hideEverything() {
        uiHandler.post(() -> {
            if (keyboardLayout.getVisibility() == View.VISIBLE) closeKeyboard();
            if (isLensActive) stopLens();
            hideMenus();
        });
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null && !pkg.toString().contains("whatsapp")) {
                hideEverything();
            }
        }
        if (!isLensActive) return;
        
        uiHandler.removeCallbacks(scanRunnable);
        uiHandler.postDelayed(scanRunnable, 300); 
    }

    private void scanAndDrawNodes(AccessibilityNodeInfo node) {
        if (node == null) return;
        
        String viewId = node.getViewIdResourceName();
        if (viewId != null && (viewId.contains("quoted") || viewId.contains("reply"))) return;

        if (node.getText() != null) {
            String text = node.getText().toString();
            if (isBraille(text)) {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                boolean isPriyanka = (bounds.centerX() < (screenWidth / 2));

                drawDecodedText(decode(text), bounds, isPriyanka);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            scanAndDrawNodes(node.getChild(i));
        }
    }

    private boolean isBraille(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        for (char c : text.toCharArray()) {
            if (c >= '\u2800' && c <= '\u28FF') return true;
        }
        return false;
    }

    // পারফেক্ট অ্যালাইনমেন্ট ফর্মুলা (কোডের একদম ওপরে বসবে)
    private void drawDecodedText(String text, Rect bounds, boolean isPriyanka) {
        // স্ক্রিনের পজিশন মেপে স্ট্যাটাস বারের উচ্চতা বাদ দেওয়া হলো
        int[] screenPos = new int[2];
        lensView.getLocationOnScreen(screenPos);
        int adjustedY = bounds.top - screenPos[1];
        int adjustedX = bounds.left - screenPos[0];

        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(14);
        textView.setTypeface(null, android.graphics.Typeface.BOLD);
        textView.setGravity(Gravity.CENTER); 
        textView.setPadding(10, 5, 10, 5);
        
        if (isPriyanka) {
            textView.setBackgroundColor(Color.parseColor("#F2FFFFFF")); 
            textView.setTextColor(Color.BLACK);
        } else {
            textView.setBackgroundColor(Color.parseColor("#E60055FF")); 
            textView.setTextColor(Color.WHITE);
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(bounds.width(), bounds.height());
        params.leftMargin = adjustedX;
        params.topMargin = adjustedY;
        lensView.addView(textView, params);
    }

    // হোয়াটসঅ্যাপে টাইপিংয়ের ১০০% ওয়ার্কিং ইনজেক্টর
    private void injectIntoWhatsApp(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        
        AccessibilityNodeInfo editableNode = findMessageNode(root);
        if (editableNode != null) {
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        }
    }

    private AccessibilityNodeInfo findMessageNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable()) return node;
        if (node.getViewIdResourceName() != null && node.getViewIdResourceName().endsWith("id/entry")) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = findMessageNode(node.getChild(i));
            if (child != null) return child;
        }
        return null;
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
