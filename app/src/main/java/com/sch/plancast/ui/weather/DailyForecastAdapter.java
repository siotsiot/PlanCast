package com.sch.plancast.ui.weather;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sch.plancast.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// 5일 날씨 예보 목록을 리사이클러뷰에 표시함.
// MainActivity에서 3시간 단위 Forecast list를 날짜별로 그룹화한 결과를 화면에 바인딩한다.
public class DailyForecastAdapter extends RecyclerView.Adapter<DailyForecastAdapter.ForecastViewHolder> {

    private final List<DailyForecastItem> forecastItems = new ArrayList<>();

    // 예보 목록 데이터 업데이트함
    public void submitList(List<DailyForecastItem> newItems) {
        forecastItems.clear();
        if (newItems != null) {
            forecastItems.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ForecastViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 아이템 레이아웃 인플레이트함
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_daily_forecast, parent, false);
        return new ForecastViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ForecastViewHolder holder, int position) {
        // 데이터를 뷰홀더에 바인딩하여 화면에 표시함.
        // 최고/최저 기온은 Current Weather API 값이 아니라 날짜별 Forecast list에서 계산된 값이다.
        DailyForecastItem item = forecastItems.get(position);
        holder.dateTextView.setText(item.getDate());
        holder.dayTextView.setText(item.getDayOfWeek());
        holder.descriptionTextView.setText(item.getDescription());
        holder.temperatureTextView.setText(String.format(
                Locale.US,
                "최고 %.1f° / 최저 %.1f°",
                item.getMaxTemperature(),
                item.getMinTemperature()
        ));
        holder.detailTextView.setText(item.getDetailText());
    }

    @Override
    public int getItemCount() {
        return forecastItems.size();
    }

    // 예보 항목 뷰를 관리하는 뷰홀더 클래스임
    static class ForecastViewHolder extends RecyclerView.ViewHolder {

        private final TextView dateTextView;
        private final TextView dayTextView;
        private final TextView descriptionTextView;
        private final TextView temperatureTextView;
        private final TextView detailTextView;

        ForecastViewHolder(@NonNull View itemView) {
            super(itemView);
            dateTextView = itemView.findViewById(R.id.forecastDateTextView);
            dayTextView = itemView.findViewById(R.id.forecastDayTextView);
            descriptionTextView = itemView.findViewById(R.id.forecastDescriptionTextView);
            temperatureTextView = itemView.findViewById(R.id.forecastTemperatureTextView);
            detailTextView = itemView.findViewById(R.id.forecastDetailTextView);
        }
    }
}
