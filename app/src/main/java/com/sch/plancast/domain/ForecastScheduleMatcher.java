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

// 일정과 날씨 예보를 비교하여 위험도를 판단함.
// OpenWeatherMap 3시간 단위 예보 중 일정 날짜에 포함된 모든 항목을 검사한다.
public class ForecastScheduleMatcher {

    private static final String TAG = "ForecastScheduleMatcher";
    private static final String FORECAST_DATE_PATTERN = "yyyy-MM-dd";
    private static final String FORECAST_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final WeatherAdvisor weatherAdvisor;

    public ForecastScheduleMatcher() {
        weatherAdvisor = new WeatherAdvisor();
    }

    // 야외 일정 중 날씨 위험이 있는 일정을 찾아 리스트로 반환함.
    // 위험 날씨 알림은 실내 일정이 아니라 야외 일정만 대상으로 한다.
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

        // 모든 일정을 순회하며 야외 일정의 날씨 위험도 확인함.
        // 시간 하나만 가장 가깝게 매칭하지 않고, 해당 날짜의 전체 Forecast list를 확인한다.
        for (ScheduleEntity schedule : schedules) {
            if (!isOutdoorSchedule(schedule)) {
                continue;
            }

            if (isBlank(schedule.getDate())) {
                continue;
            }

            // 특정 날짜의 모든 시간대 예보를 분석하여 결과 생성함.
            // 같은 날짜에 비, 눈, 뇌우, 폭염, 한파, 강풍 중 하나라도 있으면 위험 일정으로 본다.
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

    // 야외 활동 여부 확인함
    private boolean isOutdoorSchedule(ScheduleEntity schedule) {
        return schedule != null
                && ScheduleEntity.ACTIVITY_TYPE_OUTDOOR.equals(schedule.getActivityType());
    }

    // 특정 일정 날짜의 예보들을 종합하여 위험 분석 결과를 생성함.
    // Forecast API의 3시간 단위 list를 날짜 기준으로 훑어 위험 메시지와 준비물을 합친다.
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
            // 일정 날짜와 예보 날짜가 일치하는지 확인함.
            // 발표용 로직에서는 일정 시간보다 날짜 일치 여부를 우선으로 본다.
            if (!isSameScheduleDate(schedule.getDate(), forecastItem)) {
                continue;
            }

            // WeatherAdvisor를 통해 해당 시간대의 날씨 조언 구함
            WeatherAdviceResult adviceResult = weatherAdvisor.advise(
                    forecastItem.getWeatherMain(),
                    forecastItem.getDescription(),
                    forecastItem.getTemperature(),
                    forecastItem.getWindSpeed()
            );

            // 위험 요소가 있다면 메시지와 준비물을 합침 (중복 제거 포함)
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

        // 종합된 결과를 바탕으로 최종 리스크 결과 객체 반환함
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

    // 일정 날짜와 예보 날짜 동일 여부 확인함
    private boolean isSameScheduleDate(String scheduleDate, ForecastResponse.ForecastItem forecastItem) {
        if (isBlank(scheduleDate)) {
            return false;
        }

        String forecastDate = getForecastDate(forecastItem);
        return scheduleDate.equals(forecastDate);
    }

    // 예보 객체에서 날짜 문자열(yyyy-MM-dd) 추출함
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

    // 중복되지 않게 위험 메시지 리스트에 추가함
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

    // 중복되지 않게 추천 준비물 리스트에 추가함
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

    // 메시지들을 줄바꿈으로 합침
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

    // 준비물들을 쉼표로 합침
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

    // 문자열을 Date 객체로 파싱함
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
