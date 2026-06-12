package com.sch.plancast.ui.schedule;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sch.plancast.R;
import com.sch.plancast.data.local.ScheduleEntity;

import java.util.ArrayList;
import java.util.List;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder> {

    private static final int MEMO_PREVIEW_LIMIT = 40;

    private final List<ScheduleEntity> schedules = new ArrayList<>();
    private final OnScheduleClickListener onScheduleClickListener;

    public ScheduleAdapter(OnScheduleClickListener onScheduleClickListener) {
        this.onScheduleClickListener = onScheduleClickListener;
    }

    @NonNull
    @Override
    public ScheduleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_schedule, parent, false);
        return new ScheduleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScheduleViewHolder holder, int position) {
        ScheduleEntity schedule = schedules.get(position);
        holder.bind(schedule);
    }

    @Override
    public int getItemCount() {
        return schedules.size();
    }

    public void submitList(List<ScheduleEntity> newSchedules) {
        schedules.clear();
        if (newSchedules != null) {
            schedules.addAll(newSchedules);
        }
        notifyDataSetChanged();
    }

    private String getActivityTypeText(String activityType) {
        if (ScheduleEntity.ACTIVITY_TYPE_OUTDOOR.equals(activityType)) {
            return "야외";
        }
        return "실내";
    }

    private String getMemoPreview(String memo) {
        if (memo == null || memo.trim().isEmpty()) {
            return "메모 없음";
        }

        String trimmedMemo = memo.trim();
        if (trimmedMemo.length() <= MEMO_PREVIEW_LIMIT) {
            return trimmedMemo;
        }
        return trimmedMemo.substring(0, MEMO_PREVIEW_LIMIT) + "...";
    }

    public interface OnScheduleClickListener {
        void onScheduleClick(ScheduleEntity schedule);
    }

    class ScheduleViewHolder extends RecyclerView.ViewHolder {

        private final TextView titleTextView;
        private final TextView timeTextView;
        private final TextView activityTypeTextView;
        private final TextView memoTextView;

        ScheduleViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.scheduleTitleTextView);
            timeTextView = itemView.findViewById(R.id.scheduleTimeTextView);
            activityTypeTextView = itemView.findViewById(R.id.scheduleActivityTypeTextView);
            memoTextView = itemView.findViewById(R.id.scheduleMemoTextView);
        }

        void bind(ScheduleEntity schedule) {
            titleTextView.setText(schedule.getTitle());
            timeTextView.setText(schedule.getTime());
            activityTypeTextView.setText(getActivityTypeText(schedule.getActivityType()));
            memoTextView.setText(getMemoPreview(schedule.getMemo()));
            itemView.setOnClickListener(view -> onScheduleClickListener.onScheduleClick(schedule));
        }
    }
}
