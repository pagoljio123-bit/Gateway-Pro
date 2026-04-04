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

public class ScannerService extends AccessibilityService {
    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams params;

    private View dotIcon, menuLayout, glassScannerLayout, keyboardLayout;
    private TextView glassDecodedText;
    private EditText magicInput;
    private boolean isFullScreenGlass = false;

    private HashMap<Character, Character> brailleToTextMap;
    private HashMap<Character, Character> textToBrailleMap;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        initMaps();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = LayoutInflater.from(this).inflate(R.layout.floating_layout, null);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        windowManager.addView(overlayView, params);

        setupUI();
    }

    private void setupUI() {
        dotIcon = overlayView.findViewById(R.id.dot_icon);
        menuLayout = overlayView.findViewById(R.id.menu_layout);
        glassScannerLayout = overlayView.findViewById(R.id.glass_scanner_layout);
        keyboardLayout = overlayView.findViewById(R.id.keyboard_layout);
        glassDecodedText = overlayView.findViewById(R.id.glass_decoded_text);
        magicInput = overlayView.findViewById(R.id.magic_input);

        // Dot Click
        dotIcon.setOnClickListener(v -> {
            menuLayout.setVisibility(View.VISIBLE);
            dotIcon.setVisibility(View.GONE);
        });

        // Menu: Glass Scanner
        overlayView.findViewById(R.id.btn_glass_scanner).setOnClickListener(v -> {
            menuLayout.setVisibility(View.GONE);
            glassScannerLayout.setVisibility(View.VISIBLE);
            setFocusable(false);
        });

        // Menu: Auto Keyboard
        overlayView.findViewById(R.id.btn_auto_keyboard).setOnClickListener(v -> {
            menuLayout.setVisibility(View.GONE);
            keyboardLayout.setVisibility(View.VISIBLE);
            setFocusable(true); // Allow typing
        });

        // Exit
        overlayView.findViewById(R.id.btn_exit).setOnClickListener(v -> {
            if (overlayView != null) windowManager.removeView(overlayView);
            stopSelf();
        });

        // Close Glass
        overlayView.findViewById(R.id.btn_close_glass).setOnClickListener(v -> {
            glassScannerLayout.setVisibility(View.GONE);
            dotIcon.setVisibility(View.VISIBLE);
        });

        // Resize Glass (Toggle Full Screen)
        overlayView.findViewById(R.id.btn_resize_glass).setOnClickListener(v -> {
            ViewGroup.LayoutParams layoutParams = glassScannerLayout.getLayoutParams();
            if (isFullScreenGlass) {
                layoutParams.width = 800; layoutParams.height = 600; // Small Crop
            } else {
                layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            }
            isFullScreenGlass = !isFullScreenGlass;
            glassScannerLayout.setLayoutParams(layoutParams);
        });

        // Drag Glass
        glassScannerLayout.setOnTouchListener(new View.OnTouchListener() {
            private float dX, dY;
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (isFullScreenGlass) return false;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = view.getX() - event.getRawX();
                        dY = view.getY() - event.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        view.animate().x(event.getRawX() + dX).y(event.getRawY() + dY).setDuration(0).start();
                        break;
                }
                return true;
            }
        });

        // Close Keyboard
        overlayView.findViewById(R.id.btn_close_keyboard).setOnClickListener(v -> {
            keyboardLayout.setVisibility(View.GONE);
            dotIcon.setVisibility(View.VISIBLE);
            setFocusable(false);
        });

        // Auto Type Logic
        magicInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String brailleCode = encodeToBraille(s.toString());
                injectIntoWhatsApp(brailleCode);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setFocusable(boolean focusable) {
        if (focusable) {
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        } else {
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        }
        windowManager.updateViewLayout(overlayView, params);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (glassScannerLayout.getVisibility() == View.VISIBLE) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) scanNodesUnderGlass(root);
        }
    }

    private void scanNodesUnderGlass(AccessibilityNodeInfo node) {
        if (node == null) return;
        if (node.getText() != null && isBraille(node.getText().toString())) {
            Rect nodeRect = new Rect();
            node.getBoundsInScreen(nodeRect);
            
            int[] glassLocation = new int[2];
            glassScannerLayout.getLocationOnScreen(glassLocation);
            Rect glassRect = new Rect(glassLocation[0], glassLocation[1],
                    glassLocation[0] + glassScannerLayout.getWidth(),
                    glassLocation[1] + glassScannerLayout.getHeight());

            if (Rect.intersects(glassRect, nodeRect)) {
                glassDecodedText.setText("Decoded: " + decodeBraille(node.getText().toString()));
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            scanNodesUnderGlass(node.getChild(i));
        }
    }

    private void injectIntoWhatsApp(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo inputBox = findEditText(root);
        if (inputBox != null) {
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            inputBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
        }
    }

    private AccessibilityNodeInfo findEditText(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.getClassName() != null && node.getClassName().toString().equals("android.widget.EditText")) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = findEditText(node.getChild(i));
            if (child != null) return child;
        }
        return null;
    }

    private boolean isBraille(String text) {
        return text.contains("⠁") || text.contains("⡋") || text.contains("⡀");
    }

    private void initMaps() {
        brailleToTextMap = new HashMap<>();
        textToBrailleMap = new HashMap<>();
        
        // English & Bengali mappings
        String[] normal = {"a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","w","x","y","z", "অ","আ","ই","ঈ","উ","ঊ","ঋ","এ","ঐ","ও","ঔ","ক","খ","গ","ঘ","ঙ","চ","ছ","জ","ঝ","ঞ","ট","ঠ","ড","ঢ","ণ","ত","থ","দ","ধ","ন","প","ফ","ব","ভ","ম","য","র","ল","শ","ষ","স","হ","া","ি","ী","ু","ূ","ৃ","ে","ৈ","ো","ৌ","্","।"," "};
        String[] braille = {"⠁","⠃","⠉","⠙","⠑","⠋","⠛","⠓","⠊","⠚","⠅","⠇","⠍","⠝","⠕","⠏","⠟","⠗","⠎","⠞","⠥","⠧","⠺","⠭","⠽","⠵", "⡀","⡁","⡂","⡃","⡄","⡅","⡆","⡇","⡈","⡉","⡊","⡋","⡌","⡍","⡎","⡏","⡐","⡑","⡒","⡓","⡔","⡕","⡖","⡗","⡘","⡙","⡚","⡛","⡜","⡝","⡞","⡟","⡠","⡡","⡢","⡣","⡤","⡥","⡦","⡧","⡨","⡩","⡪","⡯","⡰","⡱","⡲","⡳","⡴","⡵","⡶","⡷","⡸","⡼","⡽"," "};

        for (int i = 0; i < normal.length; i++) {
            brailleToTextMap.put(braille[i].charAt(0), normal[i].charAt(0));
            textToBrailleMap.put(normal[i].charAt(0), braille[i].charAt(0));
        }
    }

    private String decodeBraille(String text) {
        StringBuilder res = new StringBuilder();
        for (char c : text.toCharArray()) res.append(brailleToTextMap.containsKey(c) ? brailleToTextMap.get(c) : c);
        return res.toString();
    }

    private String encodeToBraille(String text) {
        StringBuilder res = new StringBuilder();
        for (char c : text.toLowerCase().toCharArray()) res.append(textToBrailleMap.containsKey(c) ? textToBrailleMap.get(c) : c);
        return res.toString();
    }

    @Override public void onInterrupt() {}
}
