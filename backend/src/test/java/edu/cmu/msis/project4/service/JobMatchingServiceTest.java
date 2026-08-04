package edu.cmu.msis.project4.service;

import edu.cmu.msis.project4.model.JobRecommendation;
import edu.cmu.msis.project4.model.RecommendationRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobMatchingServiceTest {
    private final JobMatchingService service = new JobMatchingService();

    @Test
    void ranksRelevantJobAheadOfUnrelatedJob() {
        RecommendationRequest request = new RecommendationRequest();
        request.role = "data engineer";
        request.experienceLevel = "Entry level";
        request.resumeText = "Python SQL Spark Airflow AWS data pipeline ETL";

        JobRecommendation relevant = job("Junior Data Engineer",
                "Build Python and SQL ETL data pipelines with Spark, Airflow, and AWS.");
        JobRecommendation unrelated = job("Graphic Designer",
                "Create brand illustrations and marketing layouts in Adobe tools.");

        List<JobRecommendation> ranked = service.rank(List.of(unrelated, relevant), request);

        assertEquals(relevant, ranked.get(0));
        assertTrue(relevant.matchScore > unrelated.matchScore);
        assertTrue(relevant.matchReasons.stream().anyMatch(reason -> reason.startsWith("Shared skills:")));
    }

    @Test
    void scoreStaysWithinDisplayRange() {
        RecommendationRequest request = new RecommendationRequest();
        request.role = "AI agent engineer";
        request.experienceLevel = "Any";
        request.resumeText = "Python PyTorch NLP RAG LangChain LLM agent machine learning";
        JobRecommendation job = job("AI Agent Engineer",
                "Python PyTorch NLP RAG LangChain LLM agent machine learning");

        service.rank(List.of(job), request);

        assertTrue(job.matchScore >= 0 && job.matchScore <= 100);
    }

    private JobRecommendation job(String title, String description) {
        JobRecommendation job = new JobRecommendation();
        job.title = title;
        job.description = description;
        job.postedAt = "today";
        return job;
    }
}
