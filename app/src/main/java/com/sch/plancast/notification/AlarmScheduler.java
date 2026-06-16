package com.sch.plancast.notification;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.sch.plancast.data.local.ScheduleEntity;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

// 알람 매니저를 통해 일정 알림과 매일 날씨 체크 알림을 예약함.
// 일정 시작 전 알림과 위험 날씨 알림은 서로 다른 PendingIntent action/requestCode로 분리한다.
public class AlarmScheduler {

    private static final String TAG = "PlanCastWeatherCheck";
    private static final String PLAN_ALARM_TAG = "PlanCastScheduleAlarm";
    private static final int NOTIFICATION_LEAD_MINUTES = 30;
    private static final int DAILY_WEATHER_CHECK_REQUEST_CODE = 8000;
    private static final int DAILY_WEATHER_CHECK_TEST_REQUEST_CODE = 8002;
    private static final int DAILY_WEATHER_CHECK_HOUR = 8;
    private static final int DAILY_WEATHER_CHECK_MINUTE = 0;
    private static final int DAILY_WEATHER_CHECK_TEST_DELAY_SECONDS = 10;
    private static final String KOREA_TIME_ZONE_ID = "Asia/Seoul";

    // 기존 호출부 호환을 위해 유지함. 실제 일정 시작 전 알림 예약은 schedulePlanAlarm에서 처리함.
    public void scheduleNotification(Context context, ScheduleEntity schedule) {
        schedulePlanAlarm(context, schedule);
    }

    // 특정 일정에 대한 시작 전 리마인더 알림을 예약함.
    // 일정 시작 전 알림은 실내/야외 일정 모두 대상으로 한다.
    public void schedulePlanAlarm(Context context, ScheduleEntity schedule) {
        if (context == null || schedule == null || schedule.getId() <= 0) {
            Log.w(
                    PLAN_ALARM_TAG,
                    "일정 알림 예약 실패: context/schedule/id 확인 필요, scheduleId="
                            + (schedule == null ? "null" : schedule.getId())
            );
            return;
        }

        Calendar triggerTime = parseScheduleTime(schedule.getDate(), schedule.getTime());
        if (triggerTime == null) {
            Log.w(
                    PLAN_ALARM_TAG,
                    "일정 알림 예약 실패: 날짜/시간 파싱 실패, date="
                            + schedule.getDate()
                            + ", time="
                            + schedule.getTime()
            );
            return;
        }

        long scheduleMillis = triggerTime.getTimeInMillis();
        TimeZone defaultTimeZone = TimeZone.getDefault();
        TimeZone scheduleTimeZone = triggerTime.getTimeZone();

        // 일정 시간 30분 전으로 설정함.
        // scheduleMillis는 실제 일정 시작 시간이고, alarmMillis는 사용자에게 알림을 보낼 시간이다.
        triggerTime.add(Calendar.MINUTE, -NOTIFICATION_LEAD_MINUTES);
        long alarmMillis = triggerTime.getTimeInMillis();
        long currentMillis = System.currentTimeMillis();
        long delayMillis = alarmMillis - currentMillis;
        boolean isAlarmInFuture = alarmMillis > currentMillis;
        boolean canScheduleExactAlarms = canScheduleExactAlarms(context);

        Log.d(
                PLAN_ALARM_TAG,
                "일정 알림 예약 계산: scheduleId=" + schedule.getId()
                        + ", title=" + schedule.getTitle()
                        + ", date=" + schedule.getDate()
                        + ", time=" + schedule.getTime()
                        + ", activityType=" + schedule.getActivityType()
                        + ", defaultTimeZone=" + defaultTimeZone.getID()
                        + ", scheduleTimeZone=" + scheduleTimeZone.getID()
                        + ", scheduleMillis=" + scheduleMillis
                        + ", alarmMillis=" + alarmMillis
                        + ", currentMillis=" + currentMillis
                        + ", delayMillis=" + delayMillis
                        + ", alarmInFuture=" + isAlarmInFuture
                        + ", canScheduleExactAlarms=" + canScheduleExactAlarms
        );

        if (!isAlarmInFuture) {
            Log.w(PLAN_ALARM_TAG, "일정 알림 예약 생략: 30분 전 알림 시간이 이미 지났습니다.");
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.w(PLAN_ALARM_TAG, "일정 알림 예약 실패: AlarmManager를 가져오지 못했습니다.");
            return;
        }

        PendingIntent pendingIntent = createPendingIntent(
                context,
                schedule,
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        Log.d(PLAN_ALARM_TAG, "PendingIntent requestCode=" + schedule.getId());
        Log.d(PLAN_ALARM_TAG, "intent action=" + PlanAlarmReceiver.ACTION_PLAN_ALARM);
        String usedMethod = setScheduleAlarm(
                context,
                alarmManager,
                alarmMillis,
                pendingIntent,
                schedule.getId(),
                false
        );
        Log.d(
                PLAN_ALARM_TAG,
                "일정 알림 예약 요청 완료: scheduleId="
                        + schedule.getId()
                        + ", usedMethod="
                        + usedMethod
        );
    }

    // 예약된 알림 취소함
    public void cancelNotification(Context context, int scheduleId) {
        if (context == null || scheduleId <= 0) {
            Log.w(PLAN_ALARM_TAG, "일정 알림 취소 생략: context 또는 scheduleId가 유효하지 않습니다.");
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.w(PLAN_ALARM_TAG, "일정 알림 취소 실패: AlarmManager를 가져오지 못했습니다.");
            return;
        }

        Intent intent = new Intent(context, PlanAlarmReceiver.class);
        intent.setAction(PlanAlarmReceiver.ACTION_PLAN_ALARM);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                scheduleId,
                intent,
                PendingIntent.FLAG_NO_CREATE | getImmutableFlag()
        );

        if (pendingIntent == null) {
            Log.d(PLAN_ALARM_TAG, "일정 알림 취소 대상 없음: scheduleId=" + scheduleId);
            return;
        }

        alarmManager.cancel(pendingIntent);
        pendingIntent.cancel();
        Log.d(PLAN_ALARM_TAG, "일정 알림 취소 완료: scheduleId=" + scheduleId);
    }

    public boolean canScheduleExactAlarms(Context context) {
        if (context == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        boolean canSchedule = alarmManager != null && alarmManager.canScheduleExactAlarms();
        Log.d(PLAN_ALARM_TAG, "canScheduleExactAlarms=" + canSchedule);
        return canSchedule;
    }

    // 매일 정해진 시간에 날씨 체크 알람 예약함.
    // 이 알람은 야외 일정만 대상으로 Forecast 위험 여부를 검사하는 DailyWeatherCheckReceiver를 실행한다.
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

    // 테스트용 즉시 날씨 체크 알람 예약함.
    // 발표/개발 검증을 위해 매일 8시 반복 알람을 기다리지 않고 같은 Receiver를 실행한다.
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
        triggerTime.add(Calendar.SECOND, DAILY_WEATHER_CHECK_TEST_DELAY_SECONDS);

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

    // 알람 발생 시 실행될 PendingIntent 생성함.
    // 일정별 scheduleId를 requestCode로 사용해 각 일정 알림이 서로 덮어쓰이지 않게 한다.
    private PendingIntent createPendingIntent(Context context, ScheduleEntity schedule, int flag) {
        Intent intent = createPlanAlarmIntent(
                context,
                PlanAlarmReceiver.ACTION_PLAN_ALARM,
                schedule.getId(),
                schedule.getTitle(),
                schedule.getDate(),
                schedule.getTime(),
                schedule.getActivityType()
        );

        return PendingIntent.getBroadcast(
                context,
                schedule.getId(),
                intent,
                flag | getImmutableFlag()
        );
    }

    private Intent createPlanAlarmIntent(
            Context context,
            String action,
            int scheduleId,
            String title,
            String date,
            String time,
            String activityType
    ) {
        Intent intent = new Intent(context, PlanAlarmReceiver.class);
        intent.setAction(action);
        intent.putExtra(PlanAlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId);
        intent.putExtra(PlanAlarmReceiver.EXTRA_TITLE, title);
        intent.putExtra(PlanAlarmReceiver.EXTRA_DATE, date);
        intent.putExtra(PlanAlarmReceiver.EXTRA_TIME, time);
        intent.putExtra(PlanAlarmReceiver.EXTRA_ACTIVITY_TYPE, activityType);
        return intent;
    }

    // Android 버전에 따라 사용할 수 있는 가장 안정적인 일정 알림 예약 방식을 선택한다.
    private String setScheduleAlarm(
            Context context,
            AlarmManager alarmManager,
            long alarmMillis,
            PendingIntent pendingIntent,
            int requestCode,
            boolean preferAlarmClock
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean canScheduleExactAlarms = alarmManager.canScheduleExactAlarms();
            Log.d(PLAN_ALARM_TAG, "canScheduleExactAlarms=" + canScheduleExactAlarms);
            if (canScheduleExactAlarms) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            alarmMillis,
                            pendingIntent
                    );
                    Log.d(PLAN_ALARM_TAG, "일정 알림 예약 방식=setExactAndAllowWhileIdle");
                    return "setExactAndAllowWhileIdle";
                } catch (SecurityException exception) {
                    alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            alarmMillis,
                            pendingIntent
                    );
                    Log.w(PLAN_ALARM_TAG, "정확한 알람 예약 예외 발생: setAndAllowWhileIdle로 대체했습니다.", exception);
                    return "setAndAllowWhileIdle";
                }
            }

            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmMillis,
                    pendingIntent
            );
            Log.w(PLAN_ALARM_TAG, "정확한 알람 권한 없음: setAndAllowWhileIdle로 대체 예약했습니다.");
            return "setAndAllowWhileIdle";
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        alarmMillis,
                        pendingIntent
                );
                Log.d(PLAN_ALARM_TAG, "일정 알림 예약 방식=setExactAndAllowWhileIdle");
                return "setExactAndAllowWhileIdle";
            } catch (SecurityException exception) {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        alarmMillis,
                        pendingIntent
                );
                Log.w(PLAN_ALARM_TAG, "정확한 알람 예약 예외 발생: setAndAllowWhileIdle로 대체했습니다.", exception);
                return "setAndAllowWhileIdle";
            }
        }

        alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                alarmMillis,
                pendingIntent
        );
        Log.d(PLAN_ALARM_TAG, "일정 알림 예약 방식=setExact");
        return "setExact";
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

    // 다음 날씨 체크 시간 계산함.
    // 이미 오늘 오전 8시가 지났다면 다음 날 오전 8시로 예약한다.
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

    // 날짜/시간 문자열을 Calendar 객체로 변환함.
    // UTC가 아니라 디바이스 기본 TimeZone을 기준으로 해석해야 30분 전 알림이 실제 사용자 시간과 일치한다.
    private Calendar parseScheduleTime(String date, String time) {
        if (isBlank(date) || isBlank(time)) {
            return null;
        }

        try {
            String[] dateParts = date.split("-");
            String[] timeParts = time.split(":");
            if (dateParts.length != 3 || timeParts.length != 2) {
                return null;
            }

            int year = Integer.parseInt(dateParts[0]);
            int month = Integer.parseInt(dateParts[1]);
            int day = Integer.parseInt(dateParts[2]);
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);

            // 날짜와 시간 문자열은 사용자가 입력한 현지 시간으로 보아야 하므로 TimeZone을 명시한다.
            TimeZone scheduleTimeZone = getScheduleTimeZone();
            Calendar calendar = Calendar.getInstance(scheduleTimeZone, Locale.KOREA);
            calendar.setLenient(false);
            calendar.clear();
            calendar.set(year, month - 1, day, hour, minute, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            // lenient=false 상태에서 잘못된 날짜/시간이면 여기서 예외가 발생합니다.
            calendar.getTimeInMillis();
            Log.d(
                    PLAN_ALARM_TAG,
                    "일정 날짜/시간 파싱 완료: input="
                            + date
                            + " "
                            + time
                            + ", defaultTimeZone="
                            + TimeZone.getDefault().getID()
                            + ", scheduleTimeZone="
                            + scheduleTimeZone.getID()
                            + ", parsedMillis="
                            + calendar.getTimeInMillis()
            );
            return calendar;
        } catch (Exception exception) {
            Log.e(
                    PLAN_ALARM_TAG,
                    "일정 날짜/시간 파싱 실패: date=" + date + ", time=" + time,
                    exception
            );
            return null;
        }
    }

    // 일정 시간 파싱에 사용할 TimeZone을 결정함.
    // 일부 테스트 환경에서 기본 TimeZone이 UTC로 잡히면 한국 시간 일정이 9시간 밀릴 수 있어 보정한다.
    private TimeZone getScheduleTimeZone() {
        TimeZone defaultTimeZone = TimeZone.getDefault();
        if (defaultTimeZone == null || isUtcLikeTimeZone(defaultTimeZone)) {
            TimeZone koreaTimeZone = TimeZone.getTimeZone(KOREA_TIME_ZONE_ID);
            Log.w(
                    PLAN_ALARM_TAG,
                    "기본 TimeZone이 UTC 계열이라 일정 시간을 "
                            + KOREA_TIME_ZONE_ID
                            + " 기준으로 해석합니다. defaultTimeZone="
                            + (defaultTimeZone == null ? "null" : defaultTimeZone.getID())
            );
            return koreaTimeZone;
        }
        return defaultTimeZone;
    }

    private boolean isUtcLikeTimeZone(TimeZone timeZone) {
        String timeZoneId = timeZone.getID();
        String upperTimeZoneId = timeZoneId.toUpperCase(Locale.US);
        return timeZone.getRawOffset() == 0
                && ("UTC".equalsIgnoreCase(timeZoneId)
                || "GMT".equalsIgnoreCase(timeZoneId)
                || "GMT0".equalsIgnoreCase(timeZoneId)
                || "GMT+00:00".equalsIgnoreCase(timeZoneId)
                || "GMT-00:00".equalsIgnoreCase(timeZoneId)
                || upperTimeZoneId.startsWith("ETC/UTC")
                || upperTimeZoneId.startsWith("ETC/GMT"));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
