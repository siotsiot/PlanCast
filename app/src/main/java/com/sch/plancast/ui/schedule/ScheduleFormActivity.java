package com.sch.plancast.ui.schedule;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.sch.plancast.R;
import com.sch.plancast.data.local.ScheduleEntity;
import com.sch.plancast.data.repository.ScheduleRepository;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ScheduleFormActivity extends AppCompatActivity {

    public static final String EXTRA_SCHEDULE_ID = "com.sch.plancast.EXTRA_SCHEDULE_ID";
    public static final String EXTRA_DATE = "com.sch.plancast.EXTRA_DATE";

    private static final int NEW_SCHEDULE_ID = -1;

    private ScheduleRepository scheduleRepository;
    private ScheduleEntity editingSchedule;
    private EditText titleEditText;
    private EditText memoEditText;
    private Button dateButton;
    private Button timeButton;
    private RadioGroup activityTypeRadioGroup;
    private RadioButton indoorRadioButton;
    private RadioButton outdoorRadioButton;
    private Button deleteButton;
    private String selectedDate;
    private String selectedTime;
    private int scheduleId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_schedule_form);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scheduleFormRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        scheduleRepository = new ScheduleRepository(this);
        scheduleId = getIntent().getIntExtra(EXTRA_SCHEDULE_ID, NEW_SCHEDULE_ID);
        selectedDate = getIntent().getStringExtra(EXTRA_DATE);
        if (selectedDate == null || selectedDate.isEmpty()) {
            selectedDate = formatDate(System.currentTimeMillis());
        }
        selectedTime = formatTime(System.currentTimeMillis());

        initViews();
        bindClickListeners();

        if (isEditMode()) {
            ((TextView) findViewById(R.id.formTitleTextView)).setText("일정 수정");
            deleteButton.setVisibility(View.VISIBLE);
            loadSchedule();
        } else {
            ((TextView) findViewById(R.id.formTitleTextView)).setText("일정 등록");
            deleteButton.setVisibility(View.GONE);
            indoorRadioButton.setChecked(true);
            updateDateTimeButtons();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scheduleRepository != null) {
            scheduleRepository.shutdown();
        }
    }

    private void initViews() {
        titleEditText = findViewById(R.id.titleEditText);
        memoEditText = findViewById(R.id.memoEditText);
        dateButton = findViewById(R.id.dateButton);
        timeButton = findViewById(R.id.timeButton);
        activityTypeRadioGroup = findViewById(R.id.activityTypeRadioGroup);
        indoorRadioButton = findViewById(R.id.indoorRadioButton);
        outdoorRadioButton = findViewById(R.id.outdoorRadioButton);
        deleteButton = findViewById(R.id.deleteButton);
    }

    private void bindClickListeners() {
        dateButton.setOnClickListener(view -> showDatePicker());
        timeButton.setOnClickListener(view -> showTimePicker());
        findViewById(R.id.saveButton).setOnClickListener(view -> saveSchedule());
        deleteButton.setOnClickListener(view -> confirmDelete());
    }

    private boolean isEditMode() {
        return scheduleId != NEW_SCHEDULE_ID;
    }

    private void loadSchedule() {
        scheduleRepository.getById(scheduleId, new ScheduleRepository.RepositoryCallback<ScheduleEntity>() {
            @Override
            public void onSuccess(ScheduleEntity schedule) {
                runOnUiThread(() -> {
                    if (schedule == null) {
                        Toast.makeText(ScheduleFormActivity.this, "일정을 찾을 수 없습니다", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    editingSchedule = schedule;
                    populateForm(schedule);
                });
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() -> Toast.makeText(
                        ScheduleFormActivity.this,
                        "일정을 불러오지 못했습니다",
                        Toast.LENGTH_SHORT
                ).show());
            }
        });
    }

    private void populateForm(ScheduleEntity schedule) {
        titleEditText.setText(schedule.getTitle());
        memoEditText.setText(schedule.getMemo());
        selectedDate = schedule.getDate();
        selectedTime = schedule.getTime();

        if (ScheduleEntity.ACTIVITY_TYPE_OUTDOOR.equals(schedule.getActivityType())) {
            outdoorRadioButton.setChecked(true);
        } else {
            indoorRadioButton.setChecked(true);
        }

        updateDateTimeButtons();
    }

    private void saveSchedule() {
        String title = titleEditText.getText().toString().trim();
        if (title.isEmpty()) {
            titleEditText.setError("제목을 입력하세요");
            titleEditText.requestFocus();
            Toast.makeText(this, "제목을 입력하세요", Toast.LENGTH_SHORT).show();
            return;
        }

        String memo = memoEditText.getText().toString().trim();
        String activityType = activityTypeRadioGroup.getCheckedRadioButtonId() == R.id.outdoorRadioButton
                ? ScheduleEntity.ACTIVITY_TYPE_OUTDOOR
                : ScheduleEntity.ACTIVITY_TYPE_INDOOR;

        if (isEditMode()) {
            if (editingSchedule == null) {
                Toast.makeText(this, "일정 정보를 불러오는 중입니다", Toast.LENGTH_SHORT).show();
                return;
            }
            editingSchedule.setTitle(title);
            editingSchedule.setDate(selectedDate);
            editingSchedule.setTime(selectedTime);
            editingSchedule.setActivityType(activityType);
            editingSchedule.setMemo(memo);
            updateSchedule(editingSchedule);
        } else {
            ScheduleEntity schedule = new ScheduleEntity(
                    title,
                    selectedDate,
                    selectedTime,
                    activityType,
                    memo,
                    System.currentTimeMillis()
            );
            insertSchedule(schedule);
        }
    }

    private void insertSchedule(ScheduleEntity schedule) {
        scheduleRepository.insert(schedule, new ScheduleRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    Toast.makeText(ScheduleFormActivity.this, "일정이 저장되었습니다", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() -> Toast.makeText(
                        ScheduleFormActivity.this,
                        "일정을 저장하지 못했습니다",
                        Toast.LENGTH_SHORT
                ).show());
            }
        });
    }

    private void updateSchedule(ScheduleEntity schedule) {
        scheduleRepository.update(schedule, new ScheduleRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    Toast.makeText(ScheduleFormActivity.this, "일정이 수정되었습니다", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() -> Toast.makeText(
                        ScheduleFormActivity.this,
                        "일정을 수정하지 못했습니다",
                        Toast.LENGTH_SHORT
                ).show());
            }
        });
    }

    private void confirmDelete() {
        if (editingSchedule == null) {
            Toast.makeText(this, "일정 정보를 불러오는 중입니다", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("일정 삭제")
                .setMessage("이 일정을 삭제할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제", (dialog, which) -> deleteSchedule())
                .show();
    }

    private void deleteSchedule() {
        scheduleRepository.delete(editingSchedule, new ScheduleRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    Toast.makeText(ScheduleFormActivity.this, "일정이 삭제되었습니다", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            }

            @Override
            public void onError(Exception exception) {
                runOnUiThread(() -> Toast.makeText(
                        ScheduleFormActivity.this,
                        "일정을 삭제하지 못했습니다",
                        Toast.LENGTH_SHORT
                ).show());
            }
        });
    }

    private void showDatePicker() {
        Calendar calendar = calendarFromDate(selectedDate);
        new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                    updateDateTimeButtons();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void showTimePicker() {
        Calendar calendar = calendarFromTime(selectedTime);
        new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    selectedTime = String.format(Locale.US, "%02d:%02d", hourOfDay, minute);
                    updateDateTimeButtons();
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
        ).show();
    }

    private void updateDateTimeButtons() {
        dateButton.setText(selectedDate);
        timeButton.setText(selectedTime);
    }

    private Calendar calendarFromDate(String date) {
        Calendar calendar = Calendar.getInstance();
        try {
            Date parsedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date);
            if (parsedDate != null) {
                calendar.setTime(parsedDate);
            }
        } catch (ParseException ignored) {
        }
        return calendar;
    }

    private Calendar calendarFromTime(String time) {
        Calendar calendar = Calendar.getInstance();
        try {
            Date parsedTime = new SimpleDateFormat("HH:mm", Locale.US).parse(time);
            if (parsedTime != null) {
                calendar.setTime(parsedTime);
            }
        } catch (ParseException ignored) {
        }
        return calendar;
    }

    private String formatDate(long timeMillis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(timeMillis));
    }

    private String formatTime(long timeMillis) {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(timeMillis));
    }
}
