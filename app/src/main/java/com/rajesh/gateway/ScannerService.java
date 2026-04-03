package com.rajesh.gateway;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import java.util.HashMap;

public class ScannerService extends AccessibilityService {
    private WindowManager windowManager;
    private View overlayView;
    private TextView decodedView;
    private HashMap<Character, Character> reverseMap;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo == null) return;
        findBrailleNodes(nodeInfo);
    }

    private void findBrailleNodes(AccessibilityNodeInfo node) {
        if (node == null) return;
        if (node.getText() != null) {
            String text = node.getText().toString();
            if (isBraille(text)) {
                decodedView.setText("ডিকোড: " + decodeBraille(text));
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            findBrailleNodes(node.getChild(i));
        }
    }

    private boolean isBraille(String text) {
        return text.contains("⠁") || text.contains("⡋") || text.contains("⡀");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        initMap();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = LayoutInflater.from(this).inflate(R.layout.floating_layout, null);

        // এখানে Capital letter-এর ভুলটা ঠিক করা হয়েছে
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);

        params.gravity = android.view.Gravity.TOP;
        decodedView = overlayView.findViewById(R.id.decodedView);
        windowManager.addView(overlayView, params);
    }

    private void initMap() {
        reverseMap = new HashMap<>();
        reverseMap.put('⠁', 'a'); reverseMap.put('⠃', 'b'); reverseMap.put('⠉', 'c'); reverseMap.put('⠙', 'd');
        reverseMap.put('⠑', 'e'); reverseMap.put('⠋', 'f'); reverseMap.put('⠛', 'g'); reverseMap.put('⠓', 'h');
        reverseMap.put('⠊', 'i'); reverseMap.put('⠚', 'j'); reverseMap.put('⠅', 'k'); reverseMap.put('⠇', 'l');
        reverseMap.put('⠍', 'm'); reverseMap.put('⠝', 'n'); reverseMap.put('⠕', 'o'); reverseMap.put('⠏', 'p');
        reverseMap.put('⠟', 'q'); reverseMap.put('⠗', 'r'); reverseMap.put('⠎', 's'); reverseMap.put('⠞', 't');
        reverseMap.put('⠥', 'u'); reverseMap.put('⠧', 'v'); reverseMap.put('⠺', 'w'); reverseMap.put('⠭', 'x');
        reverseMap.put('⠽', 'y'); reverseMap.put('⠵', 'z');
        reverseMap.put('⡀', 'অ'); reverseMap.put('⡁', 'আ'); reverseMap.put('⡂', 'ই'); reverseMap.put('⡃', 'ঈ');
        reverseMap.put('⡄', 'উ'); reverseMap.put('⡅', 'ঊ'); reverseMap.put('⡆', 'ঋ'); reverseMap.put('⡇', 'এ');
        reverseMap.put('⡈', 'ঐ'); reverseMap.put('⡉', 'ও'); reverseMap.put('⡊', 'ঔ');
        reverseMap.put('⡋', 'ক'); reverseMap.put('⡌', 'খ'); reverseMap.put('⡍', 'গ'); reverseMap.put('⡎', 'ঘ');
        reverseMap.put('⡏', 'ঙ'); reverseMap.put('⡐', 'চ'); reverseMap.put('⡑', 'ছ'); reverseMap.put('⡒', 'জ');
        reverseMap.put('⡓', 'ঝ'); reverseMap.put('⡔', 'ঞ'); reverseMap.put('⡕', 'ট'); reverseMap.put('⡖', 'ঠ');
        reverseMap.put('⡗', 'ড'); reverseMap.put('⡘', 'ঢ'); reverseMap.put('⡙', 'ণ'); reverseMap.put('⡚', 'ত');
        reverseMap.put('⡛', 'থ'); reverseMap.put('⡜', 'দ'); reverseMap.put('⡝', 'ধ'); reverseMap.put('⡞', 'ন');
        reverseMap.put('⡟', 'প'); reverseMap.put('⡠', 'ফ'); reverseMap.put('⡡', 'ব'); reverseMap.put('⡢', 'ভ');
        reverseMap.put('⡣', 'ম'); reverseMap.put('⡤', 'য'); reverseMap.put('⡥', 'র'); reverseMap.put('⡦', 'ল');
        reverseMap.put('⡧', 'শ'); reverseMap.put('⡨', 'ষ'); reverseMap.put('⡩', 'স'); reverseMap.put('⡪', 'হ');
        reverseMap.put('⡯', 'া'); reverseMap.put('⡰', 'ি'); reverseMap.put('⡱', 'ী'); reverseMap.put('⡲', 'ু');
        reverseMap.put('⡳', 'ূ'); reverseMap.put('⡴', 'ৃ'); reverseMap.put('⡵', 'ে'); reverseMap.put('⡶', 'ৈ');
        reverseMap.put('⡷', 'ো'); reverseMap.put('⡸', 'ৌ'); reverseMap.put('⡼', '্'); reverseMap.put('⡽', '।');
        reverseMap.put(' ', ' ');
    }

    private String decodeBraille(String text) {
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (reverseMap.containsKey(c)) result.append(reverseMap.get(c));
            else result.append(c);
        }
        return result.toString();
    }

    @Override
    public void onInterrupt() {}
}
