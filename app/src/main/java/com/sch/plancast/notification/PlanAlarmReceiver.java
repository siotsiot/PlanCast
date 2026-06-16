package com.sch.plancast.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

// 일정 알람 수신 시 알림을 띄움.
// 실제 일정 시작 전 알림과 10초 QA 테스트 알림이 동일한 Receiver/Notification 경로를 사용한다.
public class PlanAlarmReceiver extends BroadcastReceiver {

    public static final String ACTION_PLAN_ALARM = "com.sch.plancast.ACTION_PLAN_ALARM";
    public static final String ACTION_TEST_PLAN_ALARM = "com.sch.plancast.ACTION_TEST_PLAN_ALARM";
    public static final String ACTION_SCHEDULE_ALARM = ACTION_PLAN_ALARM;
    public static final String EXTRA_SCHEDULE_ID = "com.sch.plancast.extra.SCHEDULE_ID";
    public static final String EXTRA_TITLE = "com.sch.plancast.extra.TITLE";
    public static final String EXTRA_DATE = "com.sch.plancast.extra.DATE";
    public static final String EXTRA_TIME = "com.sch.plancast.extra.TIME";
    public static final String EXTRA_ACTIVITY_TYPE = "com.sch.plancast.extra.ACTIVITY_TYPE";
    public static final String EXTRA_NOTIFICATION_CONTENT = "com.sch.plancast.extra.NOTIFICATION_CONTENT";

    private static final String TAG = "PlanCastPlanReceiver";
    private static final String DEFAULT_TITLE = "일정";

    // 알람 수신 시 호출됨
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "PlanAlarmReceiver onReceive 실행 여부: true");
        try {
            if (intent == null) {
                Log.w(TAG, "PlanAlarmReceiver 수신 실패: intent=null");
                return;
            }

            String action = intent.getAction();
            int scheduleId = intent.getIntExtra(EXTRA_SCHEDULE_ID, 0);
            String title = intent.getStringExtra(EXTRA_TITLE);
            String date = intent.getStringExtra(EXTRA_DATE);
            String time = intent.getStringExtra(EXTRA_TIME);
            String activityType = intent.getStringExtra(EXTRA_ACTIVITY_TYPE);
            String customContent = intent.getStringExtra(EXTRA_NOTIFICATION_CONTENT);

            Log.d(
                    TAG,
                    "수신된 일정 알림 데이터: action=" + action
                            + ", scheduleId=" + scheduleId
                            + ", title=" + title
                            + ", date=" + date
                            + ", time=" + time
                            + ", activityType=" + activityType
            );

            // 테스트 알림은 customContent를 사용하고, 실제 일정 알림은 일정 제목을 포함한 기본 문구를 사용한다.
            String scheduleTitle = isBlank(title) ? DEFAULT_TITLE : title;
            String notificationTitle = "PlanCast 일정 알림";
            String notificationContent = isBlank(customContent)
                    ? scheduleTitle + " 일정이 곧 시작됩니다. 야외 일정이라면 날씨와 준비물을 확인하세요."
                    : customContent;
            // 실제 일정은 scheduleId 기반으로 알림 ID를 만들고, 테스트 알림은 고정 ID를 사용한다.
            int notificationId = scheduleId > 0 ? scheduleId : 9002;

            Log.d(TAG, "NotificationHelper 호출 직전: notificationId=" + notificationId);
            NotificationHelper notificationHelper = new NotificationHelper(context);
            boolean notificationShown = notificationHelper.showNotification(
                    notificationId,
                    notificationTitle,
                    notificationContent
            );
            Log.d(TAG, "NotificationHelper 호출 직후: notificationShown=" + notificationShown);
        } catch (Exception exception) {
            Log.e(TAG, "PlanAlarmReceiver 처리 중 예외 발생", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
