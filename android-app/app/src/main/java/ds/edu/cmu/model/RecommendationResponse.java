package ds.edu.cmu.model;

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
        public int freshnessWindowDays;
        public boolean dedupApplied;
        public long apiLatencyMs;
    }
}

