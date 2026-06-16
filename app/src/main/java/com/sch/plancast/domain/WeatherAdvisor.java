package com.sch.plancast.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class WeatherAdvisor {

    private static final String DEFAULT_RISK_MESSAGE = "특별한 날씨 위험 요소가 없습니다.";
    private static final String DEFAULT_RECOMMENDATION = "기본 준비물만 챙기면 됩니다.";

    // 날씨 데이터를 기반으로 위험 요소와 준비물을 판단함
    public WeatherAdviceResult advise(
            String weatherMain,
            String description,
            Double temperature,
            Double windSpeed
    ) {
        List<String> riskMessages = new ArrayList<>();
        Set<String> recommendedItems = new LinkedHashSet<>();

        String combinedWeatherText = normalize(weatherMain) + " " + normalize(description);

        // 비, 눈, 뇌우 여부 확인하여 준비물 추천함
        if (containsAny(
                combinedWeatherText,
                "rain",
                "drizzle",
                "thunderstorm",
                "snow",
                "비",
                "눈",
                "소나기",
                "뇌우"
        )) {
            riskMessages.add("비 또는 눈 예보가 있어 외출 준비가 필요합니다.");
            addItems(recommendedItems, "우산", "방수 신발", "여분 양말 또는 장갑");
        }

        // 폭염 여부 확인함 (30도 이상)
        if (temperature != null && temperature >= 30.0) {
            riskMessages.add("기온이 높아 폭염에 주의해야 합니다.");
            addItems(recommendedItems, "물", "선크림", "모자");
        }

        // 한파 여부 확인함 (0도 이하)
        if (temperature != null && temperature <= 0.0) {
            riskMessages.add("기온이 낮아 한파에 주의해야 합니다.");
            addItems(recommendedItems, "목도리", "장갑", "두꺼운 외투");
        }

        // 강풍 여부 확인함 (10m/s 이상)
        if (windSpeed != null && windSpeed >= 10.0) {
            riskMessages.add("바람이 강하므로 외출 시 주의가 필요합니다.");
            addItems(recommendedItems, "고정이 잘 되는 우산", "바람막이");
        }

        if (riskMessages.isEmpty()) {
            return new WeatherAdviceResult(DEFAULT_RISK_MESSAGE, DEFAULT_RECOMMENDATION, false);
        }

        return new WeatherAdviceResult(
                joinMessages(riskMessages),
                joinItems(recommendedItems),
                true
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String source, String... keywords) {
        for (String keyword : keywords) {
            if (source.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void addItems(Set<String> recommendedItems, String... items) {
        for (String item : items) {
            recommendedItems.add(item);
        }
    }

    private String joinMessages(List<String> messages) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < messages.size(); index++) {
            if (index > 0) {
                builder.append("\n");
            }
            builder.append(messages.get(index));
        }
        return builder.toString();
    }

    private String joinItems(Set<String> items) {
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (String item : items) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(item);
            index++;
        }
        return builder.toString();
    }
}
