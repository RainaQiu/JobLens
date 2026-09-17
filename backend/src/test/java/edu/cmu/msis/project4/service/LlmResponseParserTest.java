package edu.cmu.msis.project4.service;

import edu.cmu.msis.project4.model.JobRecommendation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmResponseParserTest {
    private final LlmResponseParser parser = new LlmResponseParser();

    @Test
    void parsesValidDeepSeekMatchFixture() throws Exception {
        String json;
        try (var input = getClass().getResourceAsStream("/fixtures/deepseek-matches.json")) {
            json = new String(input.readAllBytes());
        }
        JobRecommendation job = job("job-1");

        List<LlmResponseParser.Result> results = parser.parse(json, List.of(job));

        assertEquals(1, results.size());
        assertEquals("job-1", results.get(0).jobKey);
        assertEquals(84, results.get(0).semanticScore);
        assertEquals("ETL", results.get(0).transferableSkills.get(0));
        assertTrue(results.get(0).suggestedBullet.contains("Spark"));
    }

    @Test
    void acceptsFencedJsonAndClampsOutOfRangeScores() {
        String json = "```json {\"matches\":[{\"jobKey\":\"job-1\",\"matchScore\":140," 
                + "\"rationale\":\"ok\"}]} ```";

        List<LlmResponseParser.Result> results = parser.parse(json, List.of(job("job-1")));

        assertEquals(100, results.get(0).semanticScore);
    }

    @Test
    void ignoresUnknownJobsAndRejectsResponseWithoutValidMatches() {
        String json = "{\"matches\":[{\"jobKey\":\"unknown\",\"semanticScore\":90}]}";

        assertThrows(IllegalArgumentException.class, () -> parser.parse(json, List.of(job("job-1"))));
    }

    private JobRecommendation job(String jobKey) {
        JobRecommendation job = new JobRecommendation();
        job.jobKey = jobKey;
        job.matchScore = 50;
        job.deterministicScore = 50;
        return job;
    }
}
