package com.sch.plancast.domain;

// 날씨 조언 결과를 담는 데이터 클래스
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

    // 위험 요소 존재 여부 반환함
    public boolean hasRisk() {
        return hasRisk;
    }

    // 화면 표시용 위험 안내 텍스트 구성함
    public String getRiskDisplayText() {
        return "위험 안내\n" + riskMessage;
    }

    // 화면 표시용 추천 준비물 텍스트 구성함
    public String getRecommendationDisplayText() {
        return "추천 준비물\n" + recommendedItems;
    }
}
