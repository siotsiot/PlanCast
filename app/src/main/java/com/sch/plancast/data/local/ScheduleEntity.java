package com.sch.plancast.data.local;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "schedules")
public class ScheduleEntity {

    public static final String ACTIVITY_TYPE_INDOOR = "INDOOR";
    public static final String ACTIVITY_TYPE_OUTDOOR = "OUTDOOR";

    @PrimaryKey(autoGenerate = true)
    private int id;
    private String title;
    private String date;
    private String time;
    private String activityType;
    private String memo;
    private long createdAt;

    public ScheduleEntity() {
    }

    @Ignore
    public ScheduleEntity(String title, String date, String time, String activityType, String memo, long createdAt) {
        this.title = title;
        this.date = date;
        this.time = time;
        this.activityType = activityType;
        this.memo = memo;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getActivityType() {
        return activityType;
    }

    public void setActivityType(String activityType) {
        this.activityType = activityType;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
