package com.sch.plancast.notification;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.sch.plancast.data.local.ScheduleEntity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

// 알람 매니저를 통해 일정을 예약함
public class AlarmScheduler {

    private static final String TAG = "PlanCastWeatherCheck";
    private static final int NOTIFICATION_LEAD_MINUTES = 30;
    private static final int DAILY_WEATHER_CHECK_REQUEST_CODE = 8000;
    private static final int DAILY_WEATHER_CHECK_TEST_REQUEST_CODE = 8002;
    private static final int DAILY_WEATHER_CHECK_HOUR = 8;
    private static final int DAILY_WEATHER_CHECK_MINUTE = 0;
    private static final int DAILY_WEATHER_CHECK_TEST_DELAY_MINUTES = 1;

    // 특정 일정에 대한 알림 예약함
    public void scheduleNotification(Context context, ScheduleEntity schedule) {
        if (context == null || schedule == null || schedule.getId() <= 0) {
            return;
        }

        Calendar triggerTime = parseScheduleTime(schedule.getDate(), schedule.getTime());
        if (triggerTime == null) {
            return;
        }

        // 일정 시간 30분 전으로 설정함
        triggerTime.add(Calendar.MINUTE, -NOTIFICATION_LEAD_MINUTES);
        if (triggerTime.getTimeInMillis() <= System.currentTimeMillis()) {
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        PendingIntent pendingIntent = createPendingIntent(
                context,
                schedule,
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        // 절전 모드에서도 동작하도록 예약함
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime.getTimeInMillis(),
                    pendingIntent
            );
        } else {
            alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime.getTimeInMillis(),
                    pendingIntent
            );
        }
    }

    // 예약된 알림 취소함
    public void cancelNotification(Context context, int scheduleId) {
        if (context == null || scheduleId <= 0) {
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        Intent intent = new Intent(context, PlanAlarmReceiver.class);
        intent.setAction(PlanAlarmReceiver.ACTION_SCHEDULE_ALARM);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                scheduleId,
                intent,
                PendingIntent.FLAG_NO_CREATE | getImmutableFlag()
        );

        if (pendingIntent == null) {
            return;
        }

        alarmManager.cancel(pendingIntent);
        pendingIntent.cancel();
    }

    // 매일 정해진 시간에 날씨 체크 알람 예약함
    public void scheduleDailyWeatherCheck(Context context) {
        if (context == null) {
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        PendingIntent pendingIntent = createDailyWeatherCheckPendingIntent(context);
        Calendar triggerTime = getNextDailyWeatherCheckTime();

        // 매일 반복되는 알람 설정함
        alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerTime.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent
        );
    }

    // 테스트용 즉시 날씨 체크 알람 예약함
    public void scheduleDailyWeatherCheckForTest(Context context) {
        if (context == null) {
            Log.w(TAG, "테스트 날씨 체크 알림 예약 실패: context가 null입니다.");
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.w(TAG, "테스트 날씨 체크 알림 예약 실패: AlarmManager를 가져오지 못했습니다.");
            return;
        }

        PendingIntent pendingIntent = createDailyWeatherCheckPendingIntent(
                context,
                DAILY_WEATHER_CHECK_TEST_REQUEST_CODE
        );

        Calendar triggerTime = Calendar.getInstance();
        triggerTime.add(Calendar.MINUTE, DAILY_WEATHER_CHECK_TEST_DELAY_MINUTES);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime.getTimeInMillis(),
                    pendingIntent
            );
        } else {
            alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime.getTimeInMillis(),
                    pendingIntent
            );
        }

        Log.d(
                TAG,
                "테스트 날씨 체크 알림 예약 완료: requestCode="
                        + DAILY_WEATHER_CHECK_TEST_REQUEST_CODE
                        + ", triggerAtMillis="
                        + triggerTime.getTimeInMillis()
        );
    }

    // 알람 발생 시 실행될 PendingIntent 생성함
    private PendingIntent createPendingIntent(Context context, ScheduleEntity schedule, int flag) {
        Intent intent = new Intent(context, PlanAlarmReceiver.class);
        intent.setAction(PlanAlarmReceiver.ACTION_SCHEDULE_ALARM);
        intent.putExtra(PlanAlarmReceiver.EXTRA_SCHEDULE_ID, schedule.getId());
        intent.putExtra(PlanAlarmReceiver.EXTRA_TITLE, schedule.getTitle());
        intent.putExtra(PlanAlarmReceiver.EXTRA_DATE, schedule.getDate());
        intent.putExtra(PlanAlarmReceiver.EXTRA_TIME, schedule.getTime());
        intent.putExtra(PlanAlarmReceiver.EXTRA_ACTIVITY_TYPE, schedule.getActivityType());

        return PendingIntent.getBroadcast(
                context,
                schedule.getId(),
                intent,
                flag | getImmutableFlag()
        );
    }

    private PendingIntent createDailyWeatherCheckPendingIntent(Context context) {
        return createDailyWeatherCheckPendingIntent(context, DAILY_WEATHER_CHECK_REQUEST_CODE);
    }

    private PendingIntent createDailyWeatherCheckPendingIntent(Context context, int requestCode) {
        Intent intent = new Intent(context, DailyWeatherCheckReceiver.class);
        intent.setAction(DailyWeatherCheckReceiver.ACTION_DAILY_WEATHER_CHECK);

        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | getImmutableFlag()
        );
    }

    // 다음 날씨 체크 시간 계산함
    private Calendar getNextDailyWeatherCheckTime() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, DAILY_WEATHER_CHECK_HOUR);
        calendar.set(Calendar.MINUTE, DAILY_WEATHER_CHECK_MINUTE);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        return calendar;
    }

    private int getImmutableFlag() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.FLAG_IMMUTABLE;
        }
        return 0;
    }

    // 날짜/시간 문자열을 Calendar 객체로 변환함
    private Calendar parseScheduleTime(String date, String time) {
        if (isBlank(date) || isBlank(time)) {
            return null;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        dateFormat.setLenient(false);

        try {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(dateFormat.parse(date + " " + time));
            return calendar;
        } catch (ParseException exception) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
