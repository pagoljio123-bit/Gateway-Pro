package com.rajesh.gateway;

import android.app.Service;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import java.util.HashMap;

public class FloatingService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private HashMap<Character, Character> reverseMap;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        initMap(); // ডিকশনারি লোড করা

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_layout, null);

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 0;
        params.y = 200;

        windowManager.addView(floatingView, params);

        TextView decodedText = floatingView.findViewById(R.id.decodedText);
        Button btnDecode = floatingView.findViewById(R.id.btnDecode);

        // ডিকোড বাটনে ক্লিক করলে ক্লিপবোর্ড থেকে পড়া
        btnDecode.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence pasteData = clipboard.getPrimaryClip().getItemAt(0).getText();
                if (pasteData != null) {
                    decodedText.setText(decodeBraille(pasteData.toString()));
                }
            } else {
                decodedText.setText("No Code Copied!");
            }
        });

        // বাবলটিকে স্ক্রিনের যেকোনো জায়গায় সরানোর জন্য
        floatingView.findViewById(R.id.floating_container).setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x; initialY = params.y;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });
    }

    private void initMap() {
        reverseMap = new HashMap<>();
        // ইংরেজি
        reverseMap.put('⠁', 'a'); reverseMap.put('⠃', 'b'); reverseMap.put('⠉', 'c'); reverseMap.put('⠙', 'd');
        reverseMap.put('⠑', 'e'); reverseMap.put('⠋', 'f'); reverseMap.put('⠛', 'g'); reverseMap.put('⠓', 'h');
        reverseMap.put('⠊', 'i'); reverseMap.put('⠚', 'j'); reverseMap.put('⠅', 'k'); reverseMap.put('⠇', 'l');
        reverseMap.put('⠍', 'm'); reverseMap.put('⠝', 'n'); reverseMap.put('⠕', 'o'); reverseMap.put('⠏', 'p');
        reverseMap.put('⠟', 'q'); reverseMap.put('⠗', 'r'); reverseMap.put('⠎', 's'); reverseMap.put('⠞', 't');
        reverseMap.put('⠥', 'u'); reverseMap.put('⠧', 'v'); reverseMap.put('⠺', 'w'); reverseMap.put('⠭', 'x');
        reverseMap.put('⠽', 'y'); reverseMap.put('⠵', 'z');
        // বাংলা
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
            if (reverseMap.containsKey(c)) { result.append(reverseMap.get(c)); } 
            else { result.append(c); }
        }
        return result.toString();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) windowManager.removeView(floatingView);
    }
}
