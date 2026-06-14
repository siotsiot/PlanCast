package com.sch.plancast.domain;

import com.sch.plancast.data.local.ScheduleEntity;

public class ForecastScheduleRiskResult {

    private final ScheduleEntity schedule;
    private final String forecastTime;
    private final String weatherMain;
    private final String description;
    private final double temperature;
    private final double windSpeed;
    private final boolean hasRisk;
    private final String riskMessage;
    private final String recommendedItems;

    public ForecastScheduleRiskResult(
            ScheduleEntity schedule,
            String forecastTime,
            String weatherMain,
            String description,
            double temperature,
            double windSpeed,
            boolean hasRisk,
            String riskMessage,
            String recommendedItems
    ) {
        this.schedule = schedule;
        this.forecastTime = safeString(forecastTime);
        this.weatherMain = safeString(weatherMain);
        this.description = safeString(description);
        this.temperature = temperature;
        this.windSpeed = windSpeed;
        this.hasRisk = hasRisk;
        this.riskMessage = safeString(riskMessage);
        this.recommendedItems = safeString(recommendedItems);
    }

    public ScheduleEntity getSchedule() {
        return schedule;
    }

    public String getForecastTime() {
        return forecastTime;
    }

    public String getWeatherMain() {
        return weatherMain;
    }

    public String getDescription() {
        return description;
    }

    public double getTemperature() {
        return temperature;
    }

    public double getWindSpeed() {
        return windSpeed;
    }

    public boolean hasRisk() {
        return hasRisk;
    }

    public String getRiskMessage() {
        return riskMessage;
    }

    public String getRecommendedItems() {
        return recommendedItems;
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }
}
