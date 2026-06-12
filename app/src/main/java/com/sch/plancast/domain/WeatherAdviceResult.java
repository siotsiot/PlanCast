package com.sch.plancast.domain;

public class WeatherAdviceResult {

    private final String riskMessage;
    private final String recommendedItems;
    private final boolean hasRisk;

    public WeatherAdviceResult(String riskMessage, String recommendedItems, boolean hasRisk) {
        this.riskMessage = riskMessage == null ? "" : riskMessage;
        this.recommendedItems = recommendedItems == null ? "" : recommendedItems;
        this.hasRisk = hasRisk;
    }

    public String getRiskMessage() {
        return riskMessage;
    }

    public String getRecommendedItems() {
        return recommendedItems;
    }

    public boolean hasRisk() {
        return hasRisk;
    }

    public String getRiskDisplayText() {
        return "위험 안내\n" + riskMessage;
    }

    public String getRecommendationDisplayText() {
        return "추천 준비물\n" + recommendedItems;
    }
}
