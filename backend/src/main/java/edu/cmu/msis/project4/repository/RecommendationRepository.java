package edu.cmu.msis.project4.repository;

import edu.cmu.msis.project4.model.JobRecommendation;
import org.bson.Document;

import java.util.List;

/** Persistence seam used by the recommendation pipeline and its tests. */
public interface RecommendationRepository {
    boolean alreadyRecommended(String userId, String jobKey);

    void saveRecommendationHistory(String userId, JobRecommendation job);

    void saveLog(Document logDoc);

    List<Document> getHistory(String userId);

    List<Document> recentLogs(int limit);
}
