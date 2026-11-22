package com.liskovsoft.smartyoutubetv2.common.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * MOD: SmartTube Premium automatic updates.
 * On Android 12+ the app updates itself with no "Install" button: the downloaded update is installed
 * quietly once the user leaves the app, so the next launch opens the new version.
 * Anything that blocks a quiet install (older Android, install permission off, the box asks for
 * confirmation) makes the app fall back to the normal update popup.
 */
public final class PremiumAutoInstaller {
    private static final String TAG = PremiumAutoInstaller.class.getSimpleName();
    private static final String PREFS = "smarttube_premium";
    private static final String KEY_FAILED_VERSION = "auto_install_failed_version";
    private static final String ACTION_STATUS = "smarttube.premium.AUTO_INSTALL_STATUS";
    private static final long BACKGROUND_DELAY_MS = 3_000;

    @SuppressLint("StaticFieldLeak")
    private static Context sAppContext;
    private static String sPendingApkPath;
    private static String sPendingVersion;
    private static boolean sCallbacksRegistered;
    private static boolean sInstalling;
    private static int sStartedActivities;
    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    private PremiumAutoInstaller() {
    }

    /**
     * True when this box can update the app without asking.
     */
    public static boolean isSupported(Context context, String versionName) {
        if (context == null || Build.VERSION.SDK_INT < 31) {
            return false;
        }

        Context appContext = context.getApplicationContext();

        if (!appContext.getPackageManager().canRequestPackageInstalls()) {
            return false;
        }

        // This version already failed to install quietly: use the normal popup for it.
        String failed = getPrefs(appContext).getString(KEY_FAILED_VERSION, null);
        return failed == null || !failed.equals(versionName);
    }

    /**
     * Installs the update once the app goes to the background.
     */
    public static void scheduleInstall(Context context, String versionName, String apkPath) {
        if (context == null || apkPath == null) {
            return;
        }

        sAppContext = context.getApplicationContext();
        sPendingApkPath = apkPath;
        sPendingVersion = versionName;

        registerCallbacks();

        if (!sCallbacksRegistered || sStartedActivities == 0) {
            sHandler.postDelayed(PremiumAutoInstaller::installIfInBackground, BACKGROUND_DELAY_MS);
        }
    }

    private static void registerCallbacks() {
        if (sCallbacksRegistered || !(sAppContext instanceof Application)) {
            return;
        }

        sCallbacksRegistered = true;
        // Registered while a screen is open: count it as started
        sStartedActivities = isProcessInForeground() ? 1 : 0;

        ((Application) sAppContext).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStopped(Activity activity) {
                sStartedActivities = Math.max(0, sStartedActivities - 1);

                if (sStartedActivities == 0) {
                    // Give the next screen a moment to open before deciding the user left the app
                    sHandler.removeCallbacksAndMessages(null);
                    sHandler.postDelayed(PremiumAutoInstaller::installIfInBackground, BACKGROUND_DELAY_MS);
                }
            }

            @Override
            public void onActivityStarted(Activity activity) {
                sStartedActivities++;
                sHandler.removeCallbacksAndMessages(null);
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }

    private static void installIfInBackground() {
        if (sInstalling || sPendingApkPath == null || sAppContext == null) {
            return;
        }

        // Never while a screen is open or something plays in the background
        if (sStartedActivities > 0 || isProcessInForeground()) {
            return;
        }

        install(sAppContext, sPendingApkPath, sPendingVersion);
    }

    @SuppressLint("WrongConstant")
    private static void install(Context context, String apkPath, String versionName) {
        if (Build.VERSION.SDK_INT < 31) {
            return;
        }

        File apk = new File(apkPath);

        if (!apk.exists()) {
            return;
        }

        sInstalling = true;
        PackageInstaller.Session session = null;

        try {
            PackageInstaller installer = context.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(context.getPackageName());
            params.setSize(apk.length());
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);

            int sessionId = installer.createSession(params);
            session = installer.openSession(sessionId);

            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                session.fsync(out);
            }

            registerStatusReceiver(context, versionName);

            Intent statusIntent = new Intent(ACTION_STATUS).setPackage(context.getPackageName());
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, sessionId, statusIntent, flags);

            session.commit(pendingIntent.getIntentSender());
            session.close();
            session = null;
        } catch (Exception e) {
            Log.e(TAG, "Auto install failed", e);
            markFailed(context, versionName);
            sInstalling = false;

            if (session != null) {
                session.abandon();
            }
        }
    }

    private static void registerStatusReceiver(Context context, String versionName) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);

                if (status == PackageInstaller.STATUS_SUCCESS) {
                    return; // The app restarts as the new version
                }

                // The box wants the user to confirm, or the install failed: don't pop anything up
                // in the background. The next launch shows the normal update popup for this version.
                Log.w(TAG, "Auto install not completed, status " + status);
                markFailed(ctx, versionName);
                sInstalling = false;

                try {
                    ctx.getApplicationContext().unregisterReceiver(this);
                } catch (IllegalArgumentException ignored) {
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_STATUS);

        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
    }

    /**
     * True while the app is on screen or runs a foreground service (background playback).
     */
    private static boolean isProcessInForeground() {
        ActivityManager.RunningAppProcessInfo info = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(info);
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;
    }

    private static void markFailed(Context context, String versionName) {
        sPendingApkPath = null;
        getPrefs(context).edit().putString(KEY_FAILED_VERSION, versionName).apply();
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
