package com.sch.plancast.ui.schedule;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
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
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.sch.plancast.R;
import com.sch.plancast.data.local.ScheduleEntity;
import com.sch.plancast.data.repository.ScheduleRepository;
import com.sch.plancast.notification.AlarmScheduler;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

// 일정 등록 및 수정 화면을 담당함.
// Room DB 저장 후 일정 시작 전 알림 예약까지 연결하는 화면이다.
public class ScheduleFormActivity extends AppCompatActivity {

    public static final String EXTRA_SCHEDULE_ID = "com.sch.plancast.EXTRA_SCHEDULE_ID";
    public static final String EXTRA_DATE = "com.sch.plancast.EXTRA_DATE";

    private static final String TAG = "PlanCastScheduleAlarm";
    private static final int NEW_SCHEDULE_ID = -1;

    private ScheduleRepository scheduleRepository;
    private AlarmScheduler alarmScheduler;
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
        View scheduleFormRoot = findViewById(R.id.scheduleFormRoot);
        int initialLeftPadding = scheduleFormRoot.getPaddingLeft();
        int initialTopPadding = scheduleFormRoot.getPaddingTop();
        int initialRightPadding = scheduleFormRoot.getPaddingRight();
        int initialBottomPadding = scheduleFormRoot.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(scheduleFormRoot, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    initialLeftPadding + systemBars.left,
                    initialTopPadding + systemBars.top,
                    initialRightPadding + systemBars.right,
                    initialBottomPadding + systemBars.bottom
            );
            return insets;
        });

        // 저장소 및 알람 스케줄러 초기화함
        scheduleRepository = new ScheduleRepository(this);
        alarmScheduler = new AlarmScheduler();
        scheduleId = getIntent().getIntExtra(EXTRA_SCHEDULE_ID, NEW_SCHEDULE_ID);
        selectedDate = getIntent().getStringExtra(EXTRA_DATE);
        if (selectedDate == null || selectedDate.isEmpty()) {
            selectedDate = formatDate(System.currentTimeMillis());
        }
        selectedTime = formatTime(System.currentTimeMillis());

        initViews();
        bindClickListeners();

        // 수정 모드 여부에 따라 UI 설정함
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

    // 뷰 컴포넌트 찾아 연결함
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

    // 클릭 리스너 바인딩함
    private void bindClickListeners() {
        dateButton.setOnClickListener(view -> showDatePicker());
        timeButton.setOnClickListener(view -> showTimePicker());
        findViewById(R.id.saveButton).setOnClickListener(view -> saveSchedule());
        deleteButton.setOnClickListener(view -> confirmDelete());
    }

    private boolean isEditMode() {
        return scheduleId != NEW_SCHEDULE_ID;
    }

    // 기존 일정 데이터 불러옴
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

    // 입력 폼에 일정 데이터 채움
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

    // 일정 저장 또는 수정 로직 실행함
    private void saveSchedule() {
        String title = titleEditText.getText().toString().trim();
        if (title.isEmpty()) {
            titleEditText.setError("제목을 입력하세요");
            titleEditText.requestFocus();
            Toast.makeText(this, "제목을 입력하세요", Toast.LENGTH_SHORT).show();
            return;
        }

        String memo = memoEditText.getText().toString().trim();
        // 활동 유형은 일정 저장 값으로 사용한다.
        // 일정 시작 전 알림은 실내/야외 모두 대상이고, 위험 날씨 판단은 야외 일정만 대상으로 한다.
        String activityType = activityTypeRadioGroup.getCheckedRadioButtonId() == R.id.outdoorRadioButton
                ? ScheduleEntity.ACTIVITY_TYPE_OUTDOOR
                : ScheduleEntity.ACTIVITY_TYPE_INDOOR;

        if (isEditMode()) {
            // 기존 일정 업데이트함
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
            // 신규 일정 추가함
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

    // 데이터베이스에 새 일정 삽입함.
    // Room insert 후 Repository가 실제 scheduleId를 Entity에 반영하므로, 그 id로 알림 requestCode를 만든다.
    private void insertSchedule(ScheduleEntity schedule) {
        scheduleRepository.insert(schedule, new ScheduleRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    Log.d(TAG, "일정 저장 성공: insert, id=" + schedule.getId());
                    schedulePlanAlarmForSavedSchedule(schedule);
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

    // 데이터베이스 일정 정보 갱신함.
    // 수정 시 기존 알림을 먼저 취소한 뒤 변경된 날짜/시간 기준으로 다시 예약한다.
    private void updateSchedule(ScheduleEntity schedule) {
        scheduleRepository.update(schedule, new ScheduleRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    Log.d(TAG, "일정 저장 성공: update, id=" + schedule.getId());
                    alarmScheduler.cancelNotification(ScheduleFormActivity.this, schedule.getId());
                    schedulePlanAlarmForSavedSchedule(schedule);
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

    // 삭제 확인 다이얼로그 보여줌
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

    // 데이터베이스에서 일정 삭제함.
    // 삭제된 일정의 시작 전 알림이 남지 않도록 같은 scheduleId의 PendingIntent도 취소한다.
    private void deleteSchedule() {
        scheduleRepository.delete(editingSchedule, new ScheduleRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    alarmScheduler.cancelNotification(ScheduleFormActivity.this, editingSchedule.getId());
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

    // 일정 시작 전 리마인더는 실내/야외 일정 모두 예약함.
    // 위험 날씨 알림은 DailyWeatherCheckReceiver에서 야외 일정만 별도로 검사하므로, 두 기능을 분리해 둠.
    private void schedulePlanAlarmForSavedSchedule(ScheduleEntity schedule) {
        if (schedule == null) {
            Log.w(TAG, "schedulePlanAlarm 호출 여부: false, schedule=null");
            return;
        }

        Log.d(TAG, "schedulePlanAlarm 호출 여부: true");
        Log.d(
                TAG,
                "scheduleId=" + schedule.getId()
                        + ", title=" + schedule.getTitle()
                        + ", date=" + schedule.getDate()
                        + ", time=" + schedule.getTime()
                        + ", activityType=" + schedule.getActivityType()
        );

        // 실내/야외 일정 모두 일정 시작 30분 전 리마인더 대상이다.
        alarmScheduler.schedulePlanAlarm(this, schedule);

        if (!hasNotificationPermission()) {
            Toast.makeText(
                    this,
                    "알림 권한이 없어 예약 알림이 표시되지 않을 수 있습니다.",
                    Toast.LENGTH_LONG
            ).show();
        }

        if (!alarmScheduler.canScheduleExactAlarms(this)) {
            Toast.makeText(
                    this,
                    "정확한 알람 권한이 꺼져 있으면 일정 알림이 조금 늦을 수 있습니다.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // 알림 권한 있는지 확인함
    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }

        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    // 날짜 선택 팝업 띄움
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

    // 시간 선택 팝업 띄움
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

    // 선택된 날짜와 시간을 버튼에 표시함
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
