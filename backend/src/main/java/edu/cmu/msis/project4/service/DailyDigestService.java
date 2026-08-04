package edu.cmu.msis.project4.service;

import edu.cmu.msis.project4.model.ClientRequestContext;
import edu.cmu.msis.project4.model.RecommendationRequest;
import edu.cmu.msis.project4.model.RecommendationResponse;
import edu.cmu.msis.project4.model.UserPreference;
import edu.cmu.msis.project4.repository.MongoRepository;

/** Orchestrates profile loading, matching, deduplication, and email delivery. */
public class DailyDigestService {
    private final MongoRepository repository = new MongoRepository();
    private final RecommendationService recommendationService = new RecommendationService();
    private final EmailService emailService = new EmailService();

    public DigestRunResult run() {
        DigestRunResult result = new DigestRunResult();
        for (UserPreference preference : repository.activePreferences()) {
            result.processed++;
            try {
                RecommendationRequest request = toRequest(preference);
                RecommendationResponse recommendations = recommendationService.recommend(
                        request, schedulerContext());
                if (recommendations.jobs == null || recommendations.jobs.isEmpty()) {
                    result.skipped++;
                    continue;
                }
                emailService.sendDigest(preference.userId, preference.email, recommendations.jobs);
                recommendations.jobs.forEach(job ->
                        repository.saveRecommendationHistory(preference.userId, job));
                result.sent++;
            } catch (Exception e) {
                result.failed++;
            }
        }
        return result;
    }

    private RecommendationRequest toRequest(UserPreference preference) {
        RecommendationRequest request = new RecommendationRequest();
        request.userId = preference.userId;
        request.role = preference.role;
        request.location = preference.location;
        request.experienceLevel = preference.experienceLevel;
        request.searchScope = preference.searchScope;
        request.resumeText = preference.resumeText;
        request.limit = 10;
        request.persistHistory = false;
        return request;
    }

    private ClientRequestContext schedulerContext() {
        ClientRequestContext context = new ClientRequestContext();
        context.endpoint = "/api/digests/run";
        context.deviceModel = "github-actions-scheduler";
        context.appVersion = "server";
        return context;
    }

    public static class DigestRunResult {
        public int processed;
        public int sent;
        public int skipped;
        public int failed;
    }
}
