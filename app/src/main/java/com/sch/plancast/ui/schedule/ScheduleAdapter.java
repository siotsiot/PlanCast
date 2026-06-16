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

// 일정 목록을 리사이클러뷰에 표시함.
// 선택한 날짜의 Room DB 일정 목록을 보여주고, 항목 클릭 시 수정 화면으로 이동시킨다.
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
        // 아이템 뷰 생성함
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_schedule, parent, false);
        return new ScheduleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScheduleViewHolder holder, int position) {
        // 데이터를 뷰홀더에 바인딩함.
        // 실내/야외 구분은 목록 표시용 텍스트로 바꾸되 Entity 값은 그대로 유지한다.
        ScheduleEntity schedule = schedules.get(position);
        holder.bind(schedule);
    }

    @Override
    public int getItemCount() {
        return schedules.size();
    }

    // 새로운 데이터 리스트로 갱신함
    public void submitList(List<ScheduleEntity> newSchedules) {
        schedules.clear();
        if (newSchedules != null) {
            schedules.addAll(newSchedules);
        }
        notifyDataSetChanged();
    }

    // 활동 유형 텍스트 반환함
    private String getActivityTypeText(String activityType) {
        if (ScheduleEntity.ACTIVITY_TYPE_OUTDOOR.equals(activityType)) {
            return "야외";
        }
        return "실내";
    }

    // 메모 미리보기 글자수 제한함
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

    // 클릭 리스너 인터페이스임
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

        // 데이터를 뷰에 표시함
        void bind(ScheduleEntity schedule) {
            titleTextView.setText(schedule.getTitle());
            timeTextView.setText(schedule.getTime());
            activityTypeTextView.setText(getActivityTypeText(schedule.getActivityType()));
            memoTextView.setText(getMemoPreview(schedule.getMemo()));
            itemView.setOnClickListener(view -> onScheduleClickListener.onScheduleClick(schedule));
        }
    }
}
