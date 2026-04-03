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
import java.util.List;

public class ScannerService extends AccessibilityService {
    private WindowManager windowManager;
    private View overlayView;
    private TextView decodedView;
    private HashMap<Character, Character> reverseMap;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo == null) return;

        // স্ক্রিনের সব টেক্সট চেক করা
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

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.match_parent,
                WindowManager.LayoutParams.wrap_content,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);

        params.gravity = android.view.Gravity.TOP;
        decodedView = overlayView.findViewById(R.id.decodedView);
        windowManager.addView(overlayView, params);
    }

    private void initMap() {
        reverseMap = new HashMap<>();
        // (এখানে আগের সেই সব ব্রেইল টু বাংলা ম্যাপগুলো থাকবে যা পার্ট-৩ এ দিয়েছিলাম)
        reverseMap.put('⠁', 'a'); reverseMap.put('⡀', 'অ'); // উদাহরণ হিসেবে কয়েকটা দিলাম
        // ... (বাকি ম্যাপগুলো এখানে আগের কোড থেকে বসিয়ে নিন)
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
