package com.sch.plancast.domain;

import android.util.Log;

import com.sch.plancast.data.local.ScheduleEntity;
import com.sch.plancast.data.remote.dto.ForecastResponse;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ForecastScheduleMatcher {

    private static final String TAG = "ForecastScheduleMatcher";
    private static final String SCHEDULE_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";
    private static final String FORECAST_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final long MAX_MATCH_DIFF_MILLIS = 3L * 60L * 60L * 1000L;

    private final WeatherAdvisor weatherAdvisor;

    public ForecastScheduleMatcher() {
        weatherAdvisor = new WeatherAdvisor();
    }

    public List<ForecastScheduleRiskResult> findRiskyOutdoorSchedules(
            List<ScheduleEntity> schedules,
            ForecastResponse forecastResponse
    ) {
        if (schedules == null || forecastResponse == null) {
            Log.d(TAG, "Outdoor schedules: 0, forecast items: 0, risky matches: 0");
            return Collections.emptyList();
        }

        List<ForecastResponse.ForecastItem> forecastItems = forecastResponse.getForecastItems();
        List<ForecastScheduleRiskResult> riskyResults = new ArrayList<>();

        // 이번 단계에서는 알림을 띄우지 않고, 일정 시간과 가장 가까운 예보를 찾아 위험 결과만 반환합니다.
        // Forecast API는 3시간 단위이므로, 일정 시간과 예보 시간의 차이가 3시간을 넘으면 신뢰도가 낮다고 보고 건너뜁니다.
        for (ScheduleEntity schedule : schedules) {
            if (!isOutdoorSchedule(schedule)) {
                continue;
            }

            Date scheduleDateTime = parseScheduleDateTime(schedule);
            if (scheduleDateTime == null) {
                continue;
            }

            ForecastResponse.ForecastItem nearestForecast = findNearestForecast(scheduleDateTime, forecastItems);
            if (nearestForecast == null) {
                continue;
            }

            ForecastScheduleRiskResult result = createRiskResult(schedule, nearestForecast);
            if (result.hasRisk()) {
                riskyResults.add(result);
            }
        }

        Log.d(
                TAG,
                "Outdoor schedules: " + schedules.size()
                        + ", forecast items: " + forecastItems.size()
                        + ", risky matches: " + riskyResults.size()
        );
        return riskyResults;
    }

    private boolean isOutdoorSchedule(ScheduleEntity schedule) {
        return schedule != null
                && ScheduleEntity.ACTIVITY_TYPE_OUTDOOR.equals(schedule.getActivityType());
    }

    private Date parseScheduleDateTime(ScheduleEntity schedule) {
        if (isBlank(schedule.getDate()) || isBlank(schedule.getTime())) {
            return null;
        }

        return parseDate(
                schedule.getDate() + " " + schedule.getTime(),
                SCHEDULE_DATE_TIME_PATTERN
        );
    }

    private ForecastResponse.ForecastItem findNearestForecast(
            Date scheduleDateTime,
            List<ForecastResponse.ForecastItem> forecastItems
    ) {
        if (forecastItems == null || forecastItems.isEmpty()) {
            return null;
        }

        ForecastResponse.ForecastItem nearestForecast = null;
        long nearestDiffMillis = Long.MAX_VALUE;

        for (ForecastResponse.ForecastItem forecastItem : forecastItems) {
            Date forecastDateTime = parseForecastDateTime(forecastItem);
            if (forecastDateTime == null) {
                continue;
            }

            long diffMillis = Math.abs(scheduleDateTime.getTime() - forecastDateTime.getTime());
            if (diffMillis <= MAX_MATCH_DIFF_MILLIS && diffMillis < nearestDiffMillis) {
                nearestDiffMillis = diffMillis;
                nearestForecast = forecastItem;
            }
        }

        return nearestForecast;
    }

    private Date parseForecastDateTime(ForecastResponse.ForecastItem forecastItem) {
        if (forecastItem == null || isBlank(forecastItem.getDtTxt())) {
            return null;
        }

        return parseDate(forecastItem.getDtTxt(), FORECAST_DATE_TIME_PATTERN);
    }

    private ForecastScheduleRiskResult createRiskResult(
            ScheduleEntity schedule,
            ForecastResponse.ForecastItem forecastItem
    ) {
        String weatherMain = forecastItem.getWeatherMain();
        String description = forecastItem.getDescription();
        Double temperature = forecastItem.getTemperature();
        Double windSpeed = forecastItem.getWindSpeed();

        // 위험 판단 규칙은 현재 날씨 화면에서 쓰는 WeatherAdvisor를 그대로 재사용합니다.
        // 이렇게 해두면 현재 날씨와 예보 기반 판단 기준이 서로 달라지는 문제를 줄일 수 있습니다.
        WeatherAdviceResult adviceResult = weatherAdvisor.advise(
                weatherMain,
                description,
                temperature,
                windSpeed
        );

        return new ForecastScheduleRiskResult(
                schedule,
                forecastItem.getDtTxt(),
                weatherMain,
                description,
                safeDouble(temperature),
                safeDouble(windSpeed),
                adviceResult.hasRisk(),
                adviceResult.getRiskMessage(),
                adviceResult.getRecommendedItems()
        );
    }

    private Date parseDate(String value, String pattern) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(pattern, Locale.US);
        dateFormat.setLenient(false);

        try {
            return dateFormat.parse(value);
        } catch (ParseException exception) {
            return null;
        }
    }

    private double safeDouble(Double value) {
        return value == null ? Double.NaN : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
