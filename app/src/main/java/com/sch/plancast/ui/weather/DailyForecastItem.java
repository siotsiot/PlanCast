package com.sch.plancast.ui.weather;

// 5일 예보 리스트의 개별 항목 데이터를 담는 모델임
public class DailyForecastItem {

    private final String date;
    private final String dayOfWeek;
    private final String description;
    private final double maxTemperature;
    private final double minTemperature;
    private final String detailText;

    public DailyForecastItem(
            String date,
            String dayOfWeek,
            String description,
            double maxTemperature,
            double minTemperature,
            String detailText
    ) {
        this.date = safeString(date);
        this.dayOfWeek = safeString(dayOfWeek);
        this.description = safeString(description);
        this.maxTemperature = maxTemperature;
        this.minTemperature = minTemperature;
        this.detailText = safeString(detailText);
    }

    public String getDate() {
        return date;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public String getDescription() {
        return description;
    }

    public double getMaxTemperature() {
        return maxTemperature;
    }

    public double getMinTemperature() {
        return minTemperature;
    }

    public String getDetailText() {
        return detailText;
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }
}
