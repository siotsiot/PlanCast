package com.sch.plancast.data.local;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ScheduleDao {

    // 새로운 일정 저장
    @Insert
    long insert(ScheduleEntity schedule);

    // 기존 일정 수정
    @Update
    void update(ScheduleEntity schedule);

    // 일정 삭제
    @Delete
    void delete(ScheduleEntity schedule);

    // 모든 일정 날짜순 조회
    @Query("SELECT * FROM schedules ORDER BY date ASC, time ASC")
    List<ScheduleEntity> getAll();

    // 특정 날짜의 일정 조회
    @Query("SELECT * FROM schedules WHERE date = :date ORDER BY time ASC")
    List<ScheduleEntity> getByDate(String date);

    // ID로 특정 일정 조회함
    @Query("SELECT * FROM schedules WHERE id = :id LIMIT 1")
    ScheduleEntity getById(int id);

    // 기간 내 야외 일정만 조회함
    @Query("SELECT * FROM schedules " +
            "WHERE date >= :startDate " +
            "AND date <= :endDate " +
            "AND activityType = :outdoorType " +
            "ORDER BY date ASC, time ASC")
    List<ScheduleEntity> getOutdoorSchedulesBetween(String startDate, String endDate, String outdoorType);
}
