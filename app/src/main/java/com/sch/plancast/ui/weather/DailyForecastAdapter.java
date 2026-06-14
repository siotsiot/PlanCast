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

public class DailyForecastAdapter extends RecyclerView.Adapter<DailyForecastAdapter.ForecastViewHolder> {

    private final List<DailyForecastItem> forecastItems = new ArrayList<>();

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
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_daily_forecast, parent, false);
        return new ForecastViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ForecastViewHolder holder, int position) {
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
