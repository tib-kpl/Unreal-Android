package com.ast.unreal;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** No UI: restart v200 from a separate process after an explicit settings confirmation. */
public final class UnrealRestartActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        int gamePid = getIntent().getIntExtra("game_pid", 0);
        if (gamePid > 0 && gamePid != android.os.Process.myPid()) android.os.Process.killProcess(gamePid);
        Intent launcher = new Intent(this, MainActivity.class);
        launcher.putExtra(MainActivity.EXTRA_MANAGE_DATA, getIntent().getBooleanExtra("manage_data", false));
        launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(launcher);
        finish();
    }
}
