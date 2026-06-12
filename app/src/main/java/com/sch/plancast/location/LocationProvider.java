package com.sch.plancast.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

public class LocationProvider {

    private final Context context;
    private final FusedLocationProviderClient fusedLocationProviderClient;

    public LocationProvider(Context context) {
        this.context = context.getApplicationContext();
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context);
    }

    public void getCurrentLocation(LocationCallback callback) {
        if (callback == null) {
            return;
        }

        if (!hasLocationPermission()) {
            callback.onLocationError("위치 권한이 없어 현재 위치와 날씨를 확인할 수 없습니다.");
            return;
        }

        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
        try {
            fusedLocationProviderClient
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
                    .addOnSuccessListener(location -> {
                        if (location == null) {
                            callback.onLocationError("현재 위치를 찾지 못했습니다. 기기의 위치 서비스가 켜져 있는지 확인하세요.");
                            return;
                        }
                        callback.onLocationReceived(location.getLatitude(), location.getLongitude());
                    })
                    .addOnFailureListener(exception -> callback.onLocationError(
                            "현재 위치를 가져오지 못했습니다. 위치 서비스 상태를 확인한 뒤 다시 시도하세요."
                    ));
        } catch (SecurityException exception) {
            callback.onLocationError("위치 권한이 없어 현재 위치와 날씨를 확인할 수 없습니다.");
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public interface LocationCallback {
        void onLocationReceived(double latitude, double longitude);

        void onLocationError(String errorMessage);
    }
}
