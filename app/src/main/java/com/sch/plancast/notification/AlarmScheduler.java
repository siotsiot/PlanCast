package com.sch.plancast.notification;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.sch.plancast.data.local.ScheduleEntity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AlarmScheduler {

    private static final int NOTIFICATION_LEAD_MINUTES = 30;

    public void scheduleNotification(Context context, ScheduleEntity schedule) {
        if (context == null || schedule == null || schedule.getId() <= 0) {
            return;
        }

        Calendar triggerTime = parseScheduleTime(schedule.getDate(), schedule.getTime());
        if (triggerTime == null) {
            return;
        }

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

    private int getImmutableFlag() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.FLAG_IMMUTABLE;
        }
        return 0;
    }

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
