package com.sch.plancast.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.sch.plancast.data.local.AppDatabase;
import com.sch.plancast.data.local.ScheduleDao;
import com.sch.plancast.data.local.ScheduleEntity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Room DB 일정 접근을 담당하는 Repository 클래스임.
// 모든 DB 작업은 백그라운드 스레드에서 실행하고 결과만 메인 스레드 callback으로 전달한다.
public class ScheduleRepository {

    private static final String TAG = "ScheduleRepository";
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final int OUTDOOR_LOOKAHEAD_DAYS = 5;

    private final ScheduleDao scheduleDao;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    public ScheduleRepository(Context context) {
        // 데이터베이스 및 쓰레드 풀 초기화함
        AppDatabase database = AppDatabase.getInstance(context);
        scheduleDao = database.scheduleDao();
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public void insert(ScheduleEntity schedule) {
        executeInsert(schedule, null);
    }

    public void insert(ScheduleEntity schedule, RepositoryCallback<Void> callback) {
        executeInsert(schedule, callback);
    }

    public void update(ScheduleEntity schedule) {
        executeWrite(() -> scheduleDao.update(schedule), null);
    }

    public void update(ScheduleEntity schedule, RepositoryCallback<Void> callback) {
        executeWrite(() -> scheduleDao.update(schedule), callback);
    }

    public void delete(ScheduleEntity schedule) {
        executeWrite(() -> scheduleDao.delete(schedule), null);
    }

    public void delete(ScheduleEntity schedule, RepositoryCallback<Void> callback) {
        executeWrite(() -> scheduleDao.delete(schedule), callback);
    }

    public void getAll(RepositoryCallback<List<ScheduleEntity>> callback) {
        executeRead(scheduleDao::getAll, callback);
    }

    public void getByDate(String date, RepositoryCallback<List<ScheduleEntity>> callback) {
        executeRead(() -> scheduleDao.getByDate(date), callback);
    }

    public void getById(int id, RepositoryCallback<ScheduleEntity> callback) {
        executeRead(() -> scheduleDao.getById(id), callback);
    }

    // 오늘부터 향후 5일 이내의 야외 일정만 조회함.
    // 위험 날씨 알림은 실내 일정이 아니라 야외 일정만 대상으로 하므로 별도 메서드로 분리했다.
    public void getOutdoorSchedulesWithinFiveDays(RepositoryCallback<List<ScheduleEntity>> callback) {
        String startDate = getTodayDateString();
        String endDate = getDateAfterDaysString(OUTDOOR_LOOKAHEAD_DAYS);

        // 향후 5일간의 야외 활동 일정만 DB에서 조회함
        executeRead(() -> {
            List<ScheduleEntity> schedules = scheduleDao.getOutdoorSchedulesBetween(
                    startDate,
                    endDate,
                    ScheduleEntity.ACTIVITY_TYPE_OUTDOOR
            );
            Log.d(TAG, "Outdoor schedules within five days: " + schedules.size());
            return schedules;
        }, callback);
    }

    public void shutdown() {
        // 리소스 정리함
        executorService.shutdown();
    }

    private String getTodayDateString() {
        return formatDate(Calendar.getInstance());
    }

    private String getDateAfterDaysString(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return formatDate(calendar);
    }

    private String formatDate(Calendar calendar) {
        return new SimpleDateFormat(DATE_PATTERN, Locale.US).format(calendar.getTime());
    }

    private void executeInsert(ScheduleEntity schedule, RepositoryCallback<Void> callback) {
        // 백그라운드에서 삽입 실행하고 메인 스레드로 결과 보냄
        executorService.execute(() -> {
            try {
                // Room insert가 반환한 실제 id를 Entity에 반영해야 알림 PendingIntent requestCode로 사용할 수 있다.
                long insertedId = scheduleDao.insert(schedule);
                schedule.setId((int) insertedId);
                postSuccess(callback, null);
            } catch (Exception exception) {
                postError(callback, exception);
            }
        });
    }

    private void executeWrite(DatabaseWrite write, RepositoryCallback<Void> callback) {
        // 쓰기 작업 실행함
        executorService.execute(() -> {
            try {
                write.run();
                postSuccess(callback, null);
            } catch (Exception exception) {
                postError(callback, exception);
            }
        });
    }

    private <T> void executeRead(DatabaseRead<T> read, RepositoryCallback<T> callback) {
        // 읽기 작업 실행함
        executorService.execute(() -> {
            try {
                T result = read.run();
                postSuccess(callback, result);
            } catch (Exception exception) {
                postError(callback, exception);
            }
        });
    }

    private <T> void postSuccess(RepositoryCallback<T> callback, T result) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> callback.onSuccess(result));
    }

    private <T> void postError(RepositoryCallback<T> callback, Exception exception) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> callback.onError(exception));
    }

    private interface DatabaseWrite {
        void run();
    }

    private interface DatabaseRead<T> {
        T run();
    }

    public interface RepositoryCallback<T> {
        void onSuccess(T result);

        void onError(Exception exception);
    }
}
