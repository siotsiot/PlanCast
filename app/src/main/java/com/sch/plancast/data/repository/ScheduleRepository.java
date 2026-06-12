package com.sch.plancast.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.sch.plancast.data.local.AppDatabase;
import com.sch.plancast.data.local.ScheduleDao;
import com.sch.plancast.data.local.ScheduleEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScheduleRepository {

    private final ScheduleDao scheduleDao;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    public ScheduleRepository(Context context) {
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

    public void shutdown() {
        executorService.shutdown();
    }

    private void executeInsert(ScheduleEntity schedule, RepositoryCallback<Void> callback) {
        executorService.execute(() -> {
            try {
                long insertedId = scheduleDao.insert(schedule);
                schedule.setId((int) insertedId);
                postSuccess(callback, null);
            } catch (Exception exception) {
                postError(callback, exception);
            }
        });
    }

    private void executeWrite(DatabaseWrite write, RepositoryCallback<Void> callback) {
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
