package com.ast.unreal;

import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.WindowManager;

import org.libsdl.app.SDLActivity;

import java.io.File;

/**
 * Runs Unreal Gold 226 data with the second native engine (libUnreal226.so,
 * the Android-ported UE1 v400 engine). The v200 data keeps UnrealSDLActivity.
 */
public class Unreal226Activity extends SDLActivity {
    static final String INI_NAME = "Android226.ini";
    static final String USER_INI_NAME = "Android226User.ini";
    static final String EXTRA_MAP = "com.ast.unreal.EXTRA_MAP";

    private File root;

    @Override
    protected String[] getLibraries() {
        // libxmp.so is a dependency of libUnreal226.so; API 16-22 do not load it implicitly.
        return new String[] { "SDL2", "xmp", "Unreal226" };
    }

    @Override
    protected String[] getArguments() {
        // The first engine token is the start URL: the Unreal Gold intro map.
        return new String[] {
                "--ue1-root", dataRoot().getAbsolutePath(),
                startMap(),
                "LOG=Unreal226.log",
                "INI=" + INI_NAME,
                "USERINI=" + USER_INI_NAME
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);
        Unreal226Config.ensureAndroidIni(new File(dataRoot(), "System"));
        super.onCreate(savedInstanceState);
    }

    /** Unreal.unr (the Gold intro) unless the intent names another map URL. */
    private String startMap() {
        String map = getIntent() != null ? getIntent().getStringExtra(EXTRA_MAP) : null;
        return map != null && !map.trim().isEmpty() ? map.trim() : "Unreal.unr";
    }

    private File dataRoot() {
        if (root == null) {
            String path = getIntent() != null ? getIntent().getStringExtra(UnrealDataPaths.EXTRA_UNREAL_ROOT) : null;
            root = new File(path != null ? path : "");
        }
        return root;
    }
}
