package edu.cmu.msis.project4.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Author: Raina Qiu (yuluq)
 */
public class RecommendationResponse {
    public String requestId;
    public int recommendedCount;
    public List<JobRecommendation> jobs = new ArrayList<>();
    public Meta meta = new Meta();

    public static class Meta {
        public int freshnessWindowDays = 7;
        public boolean dedupApplied = true;
        public long apiLatencyMs;
        public String requestedSearchScope = "AUTO";
        public String searchStrategy = "";
        public String resolvedLocation = "";
        public String searchSummary = "";
        public int searchedLocationsCount;
        public int jobsWithApplyLinks;
    }
}
