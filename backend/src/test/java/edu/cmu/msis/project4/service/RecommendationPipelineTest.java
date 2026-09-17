package edu.cmu.msis.project4.service;

import edu.cmu.msis.project4.client.SerpApiClient;
import edu.cmu.msis.project4.model.ClientRequestContext;
import edu.cmu.msis.project4.model.EligibilityDecision;
import edu.cmu.msis.project4.model.JobRecommendation;
import edu.cmu.msis.project4.model.RecommendationRequest;
import edu.cmu.msis.project4.model.RecommendationResponse;
import edu.cmu.msis.project4.model.ResolvedLocation;
import edu.cmu.msis.project4.model.SearchProfile;
import edu.cmu.msis.project4.repository.RecommendationRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationPipelineTest {
    @Test
    void filtersBeforeLlmAndPersistsOnlyReturnedEligibleJobs() throws Exception {
        FakeRepository repository = new FakeRepository();
        FakeLlmReranker llm = new FakeLlmReranker();
        List<JobRecommendation> candidates = List.of(
                job("new-grad", "Software Engineer, New Grad", "University hire program", "Full-time"),
                job("senior", "Senior Software Engineer", "Lead production services", "Full-time"),
                job("intern", "Software Engineer Intern", "Summer internship", "Internship"),
                job("android", "Android Software Engineer, New Grad", "University hire Android team", "Full-time"));
        FakeSerpApiClient serp = new FakeSerpApiClient(candidates);
        FakeLocationResolver locations = new FakeLocationResolver();
        RecommendationService service = new RecommendationService(
                repository,
                serp,
                locations,
                new RoleProfileExpander(),
                new JobEligibilityFilter(),
                new JobMatchingService(),
                llm);

        RecommendationRequest request = new RecommendationRequest();
        request.userId = "test-user";
        request.role = "Software Engineer";
        request.location = "United States";
        request.careerTrack = "NEW_GRADUATE";
        request.resumeText = "Java Android software development";
        request.limit = 10;
        request.persistHistory = true;

        RecommendationResponse response = service.recommend(request, new ClientRequestContext());

        assertEquals(2, response.jobs.size());
        assertTrue(response.jobs.stream().noneMatch(job -> job.jobKey.equals("senior") || job.jobKey.equals("intern")));
        assertEquals(2, llm.received.size());
        assertEquals(2, repository.savedHistory.size());
        assertTrue(response.meta.filteredCount >= 2);
    }

    private JobRecommendation job(String key, String title, String description, String type) {
        JobRecommendation job = new JobRecommendation();
        job.jobKey = key;
        job.title = title;
        job.description = description;
        job.employmentType = type;
        job.location = "United States";
        job.postedAt = "today";
        return job;
    }

    private static class FakeRepository implements RecommendationRepository {
        private final List<JobRecommendation> savedHistory = new ArrayList<>();

        @Override
        public boolean alreadyRecommended(String userId, String jobKey) {
            return false;
        }

        @Override
        public void saveRecommendationHistory(String userId, JobRecommendation job) {
            savedHistory.add(job);
        }

        @Override
        public void saveLog(Document logDoc) {
        }

        @Override
        public List<Document> getHistory(String userId) {
            return List.of();
        }

        @Override
        public List<Document> recentLogs(int limit) {
            return List.of();
        }
    }

    private static class FakeSerpApiClient extends SerpApiClient {
        private final List<JobRecommendation> jobs;

        private FakeSerpApiClient(List<JobRecommendation> jobs) {
            this.jobs = jobs;
        }

        @Override
        public FetchResult searchJobs(String role, String location, edu.cmu.msis.project4.model.CareerTrack careerTrack) {
            return new FetchResult(new ArrayList<>(jobs), 1, 200);
        }
    }

    private static class FakeLocationResolver extends LocationResolutionService {
        @Override
        public ResolvedLocation resolve(String rawLocation, String requestedScope) {
            ResolvedLocation location = new ResolvedLocation();
            location.resolvedLocation = "United States";
            location.resolvedLabel = "United States";
            location.locationType = "Country";
            location.countryCode = "US";
            location.searchStrategy = "specific_location";
            location.searchLocations.add("United States");
            return location;
        }
    }

    private static class FakeLlmReranker extends LlmReranker {
        private List<JobRecommendation> received = List.of();

        @Override
        public List<JobRecommendation> rerank(
                List<JobRecommendation> candidates,
                RecommendationRequest request,
                SearchProfile profile) {
            received = new ArrayList<>(candidates);
            return candidates;
        }
    }
}
