package com.sch.plancast;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.sch.plancast.data.local.ScheduleEntity;
import com.sch.plancast.data.repository.ScheduleRepository;
import com.sch.plancast.ui.schedule.ScheduleAdapter;
import com.sch.plancast.ui.schedule.ScheduleFormActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ScheduleRepository scheduleRepository;
    private ScheduleAdapter scheduleAdapter;
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
        selectedDate = formatDate(System.currentTimeMillis());

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
