package com.sch.plancast.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// 일정 알람 수신 시 알림을 띄움
public class PlanAlarmReceiver extends BroadcastReceiver {

    public static final String ACTION_SCHEDULE_ALARM = "com.sch.plancast.action.SCHEDULE_ALARM";
    public static final String EXTRA_SCHEDULE_ID = "com.sch.plancast.extra.SCHEDULE_ID";
    public static final String EXTRA_TITLE = "com.sch.plancast.extra.TITLE";
    public static final String EXTRA_DATE = "com.sch.plancast.extra.DATE";
    public static final String EXTRA_TIME = "com.sch.plancast.extra.TIME";
    public static final String EXTRA_ACTIVITY_TYPE = "com.sch.plancast.extra.ACTIVITY_TYPE";

    private static final String DEFAULT_TITLE = "일정";

    // 알람 수신 시 호출됨
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        // 인텐트에서 데이터 추출함
        int scheduleId = intent.getIntExtra(EXTRA_SCHEDULE_ID, 0);
        String title = intent.getStringExtra(EXTRA_TITLE);
        String date = intent.getStringExtra(EXTRA_DATE);
        String time = intent.getStringExtra(EXTRA_TIME);
        String activityType = intent.getStringExtra(EXTRA_ACTIVITY_TYPE);

        String scheduleTitle = isBlank(title) ? DEFAULT_TITLE : title;
        String notificationTitle = "PlanCast 일정 알림";
        String notificationContent = scheduleTitle
                + " 일정이 곧 시작됩니다. 야외 일정이라면 날씨와 준비물을 확인하세요.";

        // 알림 생성 및 표시함
        NotificationHelper notificationHelper = new NotificationHelper(context);
        notificationHelper.showNotification(scheduleId, notificationTitle, notificationContent);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
