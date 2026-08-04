package edu.cmu.msis.project4.service;

import edu.cmu.msis.project4.model.JobRecommendation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailServiceTest {
    @Test
    void digestEscapesUntrustedJobFields() {
        JobRecommendation job = new JobRecommendation();
        job.title = "Data <Engineer>";
        job.company = "A&B";
        job.location = "New York";
        job.applyLink = "https://example.com/apply?a=1&b=2";
        job.matchScore = 88;
        job.matchReasons = List.of("Shared skills: Python, SQL");

        String html = new EmailService().renderHtml(List.of(job));

        assertTrue(html.contains("Data &lt;Engineer&gt;"));
        assertTrue(html.contains("A&amp;B"));
        assertTrue(html.contains("88% match"));
        assertFalse(html.contains("Data <Engineer>"));
    }
}
