package com.rajesh.gateway;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnStart = findViewById(R.id.btnStart);
        
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // ওভারলে (Overlay) পারমিশন আছে কিনা চেক করা
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(MainActivity.this)) {
                    // পারমিশন না থাকলে সেটিংসে নিয়ে যাবে
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, 123);
                    Toast.makeText(MainActivity.this, "Please allow 'Display over other apps'", Toast.LENGTH_LONG).show();
                } else {
                    // পারমিশন থাকলে ম্যাজিক বাবল সার্ভিস চালু করে দেবে
                    startService(new Intent(MainActivity.this, FloatingService.class));
                    Toast.makeText(MainActivity.this, "Scanner Started! Go to WhatsApp.", Toast.LENGTH_SHORT).show();
                    finish(); // মেইন অ্যাপটি বন্ধ করে দেবে যাতে শুধু বাবল থাকে
                }
            }
        });
    }
}
