package com.sch.plancast;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.sch.plancast.data.local.ScheduleEntity;
import com.sch.plancast.data.repository.ScheduleRepository;
import com.sch.plancast.location.LocationProvider;
import com.sch.plancast.ui.schedule.ScheduleAdapter;
import com.sch.plancast.ui.schedule.ScheduleFormActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private ScheduleRepository scheduleRepository;
    private ScheduleAdapter scheduleAdapter;
    private LocationProvider locationProvider;
    private TextView locationStatusTextView;
    private TextView selectedDateTextView;
    private TextView emptyTextView;
    private String selectedDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        scheduleRepository = new ScheduleRepository(this);
        locationProvider = new LocationProvider(this);
        selectedDate = formatDate(System.currentTimeMillis());

        locationStatusTextView = findViewById(R.id.locationStatusTextView);
        selectedDateTextView = findViewById(R.id.selectedDateTextView);
        emptyTextView = findViewById(R.id.emptyTextView);

        RecyclerView scheduleRecyclerView = findViewById(R.id.scheduleRecyclerView);
        scheduleAdapter = new ScheduleAdapter(schedule -> {
            Intent intent = new Intent(MainActivity.this, ScheduleFormActivity.class);
            intent.putExtra(ScheduleFormActivity.EXTRA_SCHEDULE_ID, schedule.getId());
            startActivity(intent);
        });
        scheduleRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        scheduleRecyclerView.setAdapter(scheduleAdapter);

        android.widget.CalendarView calendarView = findViewById(R.id.calendarView);
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            updateSelectedDateLabel();
            loadSchedulesBySelectedDate();
        });

        FloatingActionButton addScheduleButton = findViewById(R.id.addScheduleButton);
        addScheduleButton.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, ScheduleFormActivity.class);
            intent.putExtra(ScheduleFormActivity.EXTRA_DATE, selectedDate);
            startActivity(intent);
        });

        updateSelectedDateLabel();
        checkLocationPermissionAndLoadLocation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSchedulesBySelectedDate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scheduleRepository != null) {
            scheduleRepository.shutdown();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            return;
        }

        if (isLocationPermissionGranted(grantResults)) {
            loadCurrentLocation();
        } else {
            showLocationPermissionDeniedMessage();
        }
    }

    private void checkLocationPermissionAndLoadLocation() {
        if (hasLocationPermission()) {
            loadCurrentLocation();
            return;
        }

        locationStatusTextView.setText("위치 권한을 요청하는 중입니다");
        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_PERMISSION_REQUEST_CODE
        );
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isLocationPermissionGranted(int[] grantResults) {
        if (grantResults.length == 0) {
            return false;
        }

        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    private void loadCurrentLocation() {
        locationStatusTextView.setText("현재 위치를 확인하는 중입니다");
        locationProvider.getCurrentLocation(new LocationProvider.LocationCallback() {
            @Override
            public void onLocationReceived(double latitude, double longitude) {
                runOnUiThread(() -> locationStatusTextView.setText(String.format(
                        Locale.US,
                        "현재 위치: %.4f, %.4f",
                        latitude,
                        longitude
                )));
            }

            @Override
            public void onLocationError(String errorMessage) {
                runOnUiThread(() -> {
                    locationStatusTextView.setText(errorMessage);
                    Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showLocationPermissionDeniedMessage() {
        String message = "위치 권한이 없어 날씨 기능을 사용할 수 없습니다";
        locationStatusTextView.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void loadSchedulesBySelectedDate() {
        String requestedDate = selectedDate;
        scheduleRepository.getByDate(requestedDate, new ScheduleRepository.RepositoryCallback<List<ScheduleEntity>>() {
            @Override
            public void onSuccess(List<ScheduleEntity> schedules) {
                runOnUiThread(() -> {
                    if (!requestedDate.equals(selectedDate)) {
                        return;
                    }
                    scheduleAdapter.submitList(schedules);
                    emptyTextView.setVisibility(schedules.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        "일정을 불러오지 못했습니다",
                        Toast.LENGTH_SHORT
                ).show());
            }
        });
    }

    private void updateSelectedDateLabel() {
        selectedDateTextView.setText("선택 날짜: " + selectedDate);
    }

    private String formatDate(long timeMillis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(timeMillis));
    }
}
