package com.sch.plancast.domain;

import android.util.Log;

import com.sch.plancast.data.local.ScheduleEntity;
import com.sch.plancast.data.remote.dto.ForecastResponse;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ForecastScheduleMatcher {

    private static final String TAG = "ForecastScheduleMatcher";
    private static final String FORECAST_DATE_PATTERN = "yyyy-MM-dd";
    private static final String FORECAST_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

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
        if (forecastItems == null) {
            forecastItems = Collections.emptyList();
        }
        List<ForecastScheduleRiskResult> riskyResults = new ArrayList<>();

        // 발표용으로 이해하기 쉽게, 일정 시간과 가장 가까운 예보 1개만 보지 않습니다.
        // 야외 일정이 있는 "날짜"의 3시간 단위 예보를 모두 검사하고,
        // 그 날짜에 비/눈/뇌우/강풍/폭염/한파 중 하나라도 있으면 위험 일정으로 판단합니다.
        for (ScheduleEntity schedule : schedules) {
            if (!isOutdoorSchedule(schedule)) {
                continue;
            }

            if (isBlank(schedule.getDate())) {
                continue;
            }

            ForecastScheduleRiskResult result = createDailyRiskResult(schedule, forecastItems);
            if (result != null && result.hasRisk()) {
                riskyResults.add(result);
            }
        }

        Log.d(
                TAG,
                "Outdoor schedules: " + schedules.size()
                        + ", forecast items: " + forecastItems.size()
                        + ", risky daily matches: " + riskyResults.size()
        );
        return riskyResults;
    }

    private boolean isOutdoorSchedule(ScheduleEntity schedule) {
        return schedule != null
                && ScheduleEntity.ACTIVITY_TYPE_OUTDOOR.equals(schedule.getActivityType());
    }

    private ForecastScheduleRiskResult createDailyRiskResult(
            ScheduleEntity schedule,
            List<ForecastResponse.ForecastItem> forecastItems
    ) {
        if (forecastItems == null || forecastItems.isEmpty()) {
            return null;
        }

        ForecastResponse.ForecastItem representativeForecast = null;
        Set<String> riskMessages = new LinkedHashSet<>();
        Set<String> recommendedItems = new LinkedHashSet<>();

        for (ForecastResponse.ForecastItem forecastItem : forecastItems) {
            if (!isSameScheduleDate(schedule.getDate(), forecastItem)) {
                continue;
            }

            // 같은 날짜에 속한 모든 예보를 WeatherAdvisor로 검사합니다.
            // 여러 시간대에서 위험 요소가 발견되면 메시지와 준비물을 합치고, 중복 준비물은 제거합니다.
            WeatherAdviceResult adviceResult = weatherAdvisor.advise(
                    forecastItem.getWeatherMain(),
                    forecastItem.getDescription(),
                    forecastItem.getTemperature(),
                    forecastItem.getWindSpeed()
            );

            if (adviceResult.hasRisk()) {
                if (representativeForecast == null) {
                    representativeForecast = forecastItem;
                }
                addRiskMessages(riskMessages, adviceResult.getRiskMessage());
                addRecommendedItems(recommendedItems, adviceResult.getRecommendedItems());
            }
        }

        if (representativeForecast == null) {
            return null;
        }

        return new ForecastScheduleRiskResult(
                schedule,
                representativeForecast.getDtTxt(),
                representativeForecast.getWeatherMain(),
                representativeForecast.getDescription(),
                safeDouble(representativeForecast.getTemperature()),
                safeDouble(representativeForecast.getWindSpeed()),
                true,
                joinLines(riskMessages),
                joinComma(recommendedItems)
        );
    }

    private boolean isSameScheduleDate(String scheduleDate, ForecastResponse.ForecastItem forecastItem) {
        if (isBlank(scheduleDate)) {
            return false;
        }

        String forecastDate = getForecastDate(forecastItem);
        return scheduleDate.equals(forecastDate);
    }

    private String getForecastDate(ForecastResponse.ForecastItem forecastItem) {
        if (forecastItem == null || isBlank(forecastItem.getDtTxt())) {
            return null;
        }

        Date forecastDateTime = parseDate(forecastItem.getDtTxt(), FORECAST_DATE_TIME_PATTERN);
        if (forecastDateTime == null) {
            return null;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat(FORECAST_DATE_PATTERN, Locale.US);
        return dateFormat.format(forecastDateTime);
    }

    private void addRiskMessages(Set<String> riskMessages, String messageText) {
        if (isBlank(messageText)) {
            return;
        }

        String[] messages = messageText.split("\\n");
        for (String message : messages) {
            String trimmedMessage = message.trim();
            if (!trimmedMessage.isEmpty()) {
                riskMessages.add(trimmedMessage);
            }
        }
    }

    private void addRecommendedItems(Set<String> recommendedItems, String itemText) {
        if (isBlank(itemText)) {
            return;
        }

        String[] items = itemText.split(",");
        for (String item : items) {
            String trimmedItem = item.trim();
            if (!trimmedItem.isEmpty()) {
                recommendedItems.add(trimmedItem);
            }
        }
    }

    private String joinLines(Set<String> values) {
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (String value : values) {
            if (index > 0) {
                builder.append("\n");
            }
            builder.append(value);
            index++;
        }
        return builder.toString();
    }

    private String joinComma(Set<String> values) {
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (String value : values) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(value);
            index++;
        }
        return builder.toString();
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
