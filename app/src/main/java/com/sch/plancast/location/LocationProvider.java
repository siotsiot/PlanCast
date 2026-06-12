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
            callback.onLocationError("위치 권한이 없어 날씨 기능을 사용할 수 없습니다");
            return;
        }

        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
        try {
            fusedLocationProviderClient
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
                    .addOnSuccessListener(location -> {
                        if (location == null) {
                            callback.onLocationError("현재 위치를 확인할 수 없습니다");
                            return;
                        }
                        callback.onLocationReceived(location.getLatitude(), location.getLongitude());
                    })
                    .addOnFailureListener(exception -> callback.onLocationError(
                            "현재 위치 조회 실패: " + getErrorMessage(exception)
                    ));
        } catch (SecurityException exception) {
            callback.onLocationError("위치 권한이 없어 날씨 기능을 사용할 수 없습니다");
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private String getErrorMessage(Exception exception) {
        if (exception.getMessage() == null || exception.getMessage().isEmpty()) {
            return "알 수 없는 오류";
        }
        return exception.getMessage();
    }

    public interface LocationCallback {
        void onLocationReceived(double latitude, double longitude);

        void onLocationError(String errorMessage);
    }
}
