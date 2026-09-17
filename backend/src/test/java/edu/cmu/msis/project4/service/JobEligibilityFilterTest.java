package edu.cmu.msis.project4.service;

import edu.cmu.msis.project4.model.CareerTrack;
import edu.cmu.msis.project4.model.EligibilityDecision;
import edu.cmu.msis.project4.model.JobRecommendation;
import edu.cmu.msis.project4.model.RecommendationRequest;
import edu.cmu.msis.project4.model.ResolvedLocation;
import edu.cmu.msis.project4.model.SearchProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobEligibilityFilterTest {
    private final JobEligibilityFilter filter = new JobEligibilityFilter();
    private final RoleProfileExpander expander = new RoleProfileExpander();

    @Test
    void excludesSeniorJobFromNewGraduateTrack() {
        SearchProfile profile = profile("Software Engineer", "NEW_GRADUATE", "");
        JobRecommendation job = job("Senior Software Engineer", "Lead production services.", "Full-time", "United States");

        EligibilityDecision decision = filter.evaluate(job, profile, resolvedLocation("United States"));

        assertEquals(EligibilityDecision.Status.FAIL, decision.status);
        assertTrue(decision.reasons.stream().anyMatch(reason -> reason.toLowerCase().contains("career")));
    }

    @Test
    void excludesInternshipFromGeneralFullTimeTrack() {
        SearchProfile profile = profile("Software Engineer", "GENERAL_FULL_TIME", "");
        JobRecommendation job = job("Software Engineer Intern", "Internship on the mobile team.", "Internship", "United States");

        EligibilityDecision decision = filter.evaluate(job, profile, resolvedLocation("United States"));

        assertEquals(EligibilityDecision.Status.FAIL, decision.status);
    }

    @Test
    void genericSoftwareEngineerAcceptsAndroidSpecialization() {
        SearchProfile profile = profile("Software Engineer", "GENERAL_FULL_TIME", "");
        JobRecommendation job = job("Android Software Engineer", "Build Android applications.", "Full-time", "United States");

        EligibilityDecision decision = filter.evaluate(job, profile, resolvedLocation("United States"));

        assertEquals(EligibilityDecision.Status.PASS, decision.status);
    }

    @Test
    void selectedAndroidSpecializationExcludesPlatformRole() {
        SearchProfile profile = profile("Software Engineer", "GENERAL_FULL_TIME", "Android");
        JobRecommendation job = job("Platform Software Engineer", "Build developer infrastructure.", "Full-time", "United States");

        EligibilityDecision decision = filter.evaluate(job, profile, resolvedLocation("United States"));

        assertEquals(EligibilityDecision.Status.FAIL, decision.status);
        assertTrue(decision.reasons.stream().anyMatch(reason -> reason.toLowerCase().contains("special")));
    }

    @Test
    void keepsMissingJobMetadataAsUnknownInsteadOfDroppingIt() {
        SearchProfile profile = profile("Software Engineer", "GENERAL_FULL_TIME", "");
        JobRecommendation job = job("Software Engineer", "Build services.", "", "");

        EligibilityDecision decision = filter.evaluate(job, profile, resolvedLocation("United States"));

        assertEquals(EligibilityDecision.Status.UNKNOWN, decision.status);
        assertTrue(decision.reasons.stream().anyMatch(reason -> reason.toLowerCase().contains("unverified")));
    }

    private SearchProfile profile(String role, String track, String specialization) {
        RecommendationRequest request = new RecommendationRequest();
        request.role = role;
        request.location = "United States";
        request.careerTrack = track;
        request.specialization = specialization;
        return SearchProfile.from(request, expander);
    }

    private JobRecommendation job(String title, String description, String type, String location) {
        JobRecommendation job = new JobRecommendation();
        job.title = title;
        job.description = description;
        job.employmentType = type;
        job.location = location;
        return job;
    }

    private ResolvedLocation resolvedLocation(String value) {
        ResolvedLocation location = new ResolvedLocation();
        location.resolvedLocation = value;
        location.resolvedLabel = value;
        location.locationType = "Country";
        location.searchLocations.add(value);
        return location;
    }
}
