package com.sch.plancast.notification;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.sch.plancast.R;

// 시스템 알림 표시를 도와주는 유틸리티 클래스임
public class NotificationHelper {

    private static final String TAG = "PlanCastWeatherCheck";
    public static final String CHANNEL_ID = "plancast_schedule_channel";
    private static final String CHANNEL_NAME = "PlanCast 일정 알림";
    private static final String CHANNEL_DESCRIPTION = "야외 일정 시작 전 알림";

    private final Context context;

    public NotificationHelper(Context context) {
        this.context = context.getApplicationContext();
        // 채널 초기화함
        createNotificationChannel();
    }

    // 시스템 상단바에 알림을 표시함
    public boolean showNotification(int notificationId, String title, String content) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "알림 표시 중단: POST_NOTIFICATIONS 권한이 없습니다.");
            return false;
        }

        // 알림 속성 설정 및 빌드함
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        // 알림 매니저를 통해 알림 띄움
        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
        Log.d(TAG, "알림 표시 요청 실행: notificationId=" + notificationId + ", title=" + title);
        return true;
    }

    // 안드로이드 8.0 이상을 위한 알림 채널 생성함
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(CHANNEL_DESCRIPTION);

        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    // 알림 권한 유무 확인함
    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }

        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }
}
