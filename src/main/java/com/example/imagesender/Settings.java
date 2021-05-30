package com.example.imagesender;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;

public class Settings extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);
        Log.d("test", "test");
        SettingsFragment sf = new SettingsFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.settingsContainer, sf)
                .commit();
    }
}
