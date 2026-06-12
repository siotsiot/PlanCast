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
}
