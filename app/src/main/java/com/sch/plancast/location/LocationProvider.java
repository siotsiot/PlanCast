package com.sch.plancast.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

// 기기의 현재 위치 정보를 제공함
public class LocationProvider {

    private final Context context;
    private final FusedLocationProviderClient fusedLocationProviderClient;

    public LocationProvider(Context context) {
        this.context = context.getApplicationContext();
        // 위치 서비스 클라이언트 초기화함
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context);
    }

    // 현재 위치 좌표를 비동기로 가져옴
    public void getCurrentLocation(LocationCallback callback) {
        if (callback == null) {
            return;
        }

        // 위치 권한 유무 확인함
        if (!hasLocationPermission()) {
            callback.onLocationError("위치 권한이 없어 현재 위치와 날씨를 확인할 수 없습니다.");
            return;
        }

        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
        try {
            // 고정밀도 위치 정보 요청함
            fusedLocationProviderClient
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
                    .addOnSuccessListener(location -> {
                        if (location == null) {
                            callback.onLocationError("현재 위치를 찾지 못했습니다. 기기의 위치 서비스가 켜져 있는지 확인하세요.");
                            return;
                        }
                        // 성공 시 위도, 경도 전달함
                        callback.onLocationReceived(location.getLatitude(), location.getLongitude());
                    })
                    .addOnFailureListener(exception -> callback.onLocationError(
                            "현재 위치를 가져오지 못했습니다. 위치 서비스 상태를 확인한 뒤 다시 시도하세요."
                    ));
        } catch (SecurityException exception) {
            callback.onLocationError("위치 권한이 없어 현재 위치와 날씨를 확인할 수 없습니다.");
        }
    }

    // 위치 권한 허용 여부 확인함
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    // 위치 정보를 받기 위한 콜백 인터페이스임
    public interface LocationCallback {
        void onLocationReceived(double latitude, double longitude);

        void onLocationError(String errorMessage);
    }
}
