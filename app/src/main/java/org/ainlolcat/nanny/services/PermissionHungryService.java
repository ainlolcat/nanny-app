package org.ainlolcat.nanny.services;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public interface PermissionHungryService {

    AtomicInteger PERMISSION_COUNTER = new AtomicInteger(42);

    Map<String, Integer> getRequiredPermissions();

    default boolean checkIfAllGranted(Context context) {
        for (String permission : getRequiredPermissions().keySet()) {
            if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    default boolean ensurePermission(Activity activity) {
        boolean allGranted = true;
        Map<String, Integer> requiredPermissions = getRequiredPermissions();
        Log.i("PermissionHungryService", "Need to check permissions: " + requiredPermissions);
        for (Map.Entry<String, Integer> permission : requiredPermissions.entrySet()) {
            allGranted = allGranted && ensurePermission(activity, permission.getKey(), permission.getValue());
        }
        return allGranted;
    }

    default boolean ensurePermission(Activity activity, String permission, int listenCode) {
        int permissionGranted = ActivityCompat.checkSelfPermission(activity, permission);
        Log.i("PermissionHungryService", "Starting to check permission " + permission + " with current grant: " + permissionGranted);
        if (permissionGranted != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{permission}, listenCode);
            return false;
        }
        return true;
    }
}
