package com.sch.plancast.data.local;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ScheduleDao {

    @Insert
    long insert(ScheduleEntity schedule);

    @Update
    void update(ScheduleEntity schedule);

    @Delete
    void delete(ScheduleEntity schedule);

    @Query("SELECT * FROM schedules ORDER BY date ASC, time ASC")
    List<ScheduleEntity> getAll();

    @Query("SELECT * FROM schedules WHERE date = :date ORDER BY time ASC")
    List<ScheduleEntity> getByDate(String date);

    @Query("SELECT * FROM schedules WHERE id = :id LIMIT 1")
    ScheduleEntity getById(int id);

    // 오늘부터 향후 5일 이내의 야외 일정만 조회하기 위한 쿼리입니다.
    // date는 yyyy-MM-dd 형식으로 저장되어 있으므로 문자열 비교로 날짜 범위 조회가 가능합니다.
    @Query("SELECT * FROM schedules " +
            "WHERE date >= :startDate " +
            "AND date <= :endDate " +
            "AND activityType = :outdoorType " +
            "ORDER BY date ASC, time ASC")
    List<ScheduleEntity> getOutdoorSchedulesBetween(String startDate, String endDate, String outdoorType);
}
