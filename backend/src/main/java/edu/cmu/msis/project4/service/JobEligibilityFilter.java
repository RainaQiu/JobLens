package edu.cmu.msis.project4.service;

import edu.cmu.msis.project4.model.CareerTrack;
import edu.cmu.msis.project4.model.EligibilityDecision;
import edu.cmu.msis.project4.model.JobRecommendation;
import edu.cmu.msis.project4.model.ResolvedLocation;
import edu.cmu.msis.project4.model.SearchProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Applies hard career-track, role, specialization, and location constraints. */
public class JobEligibilityFilter {
    private final CareerTrackClassifier careerTrackClassifier;
    private final RoleProfileExpander roleProfileExpander;

    public JobEligibilityFilter() {
        this(new CareerTrackClassifier(), new RoleProfileExpander());
    }

    JobEligibilityFilter(CareerTrackClassifier careerTrackClassifier, RoleProfileExpander roleProfileExpander) {
        this.careerTrackClassifier = careerTrackClassifier;
        this.roleProfileExpander = roleProfileExpander;
    }

    public EligibilityDecision evaluate(
            JobRecommendation job,
            SearchProfile profile,
            ResolvedLocation resolvedLocation) {
        List<String> reasons = new ArrayList<>();
        boolean unknown = false;
        CareerTrackClassifier.Result trackResult = careerTrackClassifier.classify(job);
        CareerTrack requestedTrack = profile == null ? CareerTrack.ANY : profile.careerTrack;

        if (requestedTrack != CareerTrack.ANY) {
            if (!trackResult.explicit) {
                unknown = true;
                reasons.add("Career track is unverified");
            } else if (trackResult.track != requestedTrack) {
                return decision(EligibilityDecision.Status.FAIL, reasons,
                        "Career track mismatch: requested " + requestedTrack + " but found " + trackResult.track,
                        trackResult, profile, "", "");
            } else {
                reasons.add("Career track verified: " + requestedTrack);
            }
        }

        SearchProfile.RoleExpansion jobExpansion = roleProfileExpander.expand(
                safe(job == null ? null : job.title), "", safe(job == null ? null : job.description));
        String expectedFamily = profile == null ? "" : safe(profile.roleFamily);
        String jobFamily = safe(jobExpansion.canonicalFamily);
        if (!expectedFamily.isBlank() && !jobFamily.isBlank()
                && !normalize(expectedFamily).equals(normalize(jobFamily))) {
            return decision(EligibilityDecision.Status.FAIL, reasons,
                    "Role family mismatch: requested " + expectedFamily + " but found " + jobFamily,
                    trackResult, profile, jobFamily, jobExpansion.canonicalSpecialization);
        }

        String expectedSpecialization = profile == null ? "" : safe(profile.specialization);
        String detectedSpecialization = safe(jobExpansion.canonicalSpecialization);
        if (!expectedSpecialization.isBlank()) {
            if (detectedSpecialization.isBlank()) {
                unknown = true;
                reasons.add("Specialization is unverified");
            } else if (!normalize(expectedSpecialization).equals(normalize(detectedSpecialization))) {
                return decision(EligibilityDecision.Status.FAIL, reasons,
                        "Specialization mismatch: requested " + expectedSpecialization + " but found "
                                + detectedSpecialization,
                        trackResult, profile, jobFamily, detectedSpecialization);
            } else {
                reasons.add("Specialization verified: " + detectedSpecialization);
            }
        }

        LocationResult locationResult = locationResult(job, resolvedLocation);
        if (locationResult == LocationResult.FAIL) {
            return decision(EligibilityDecision.Status.FAIL, reasons, "Location mismatch",
                    trackResult, profile, jobFamily, detectedSpecialization);
        }
        if (locationResult == LocationResult.UNKNOWN) {
            unknown = true;
            reasons.add("Location is unverified");
        } else {
            reasons.add("Location verified");
        }

        if (reasons.isEmpty()) {
            reasons.add("Eligibility constraints passed");
        }
        return decision(unknown ? EligibilityDecision.Status.UNKNOWN : EligibilityDecision.Status.PASS,
                reasons, null, trackResult, profile, jobFamily, detectedSpecialization);
    }

    private EligibilityDecision decision(
            EligibilityDecision.Status status,
            List<String> reasons,
            String reason,
            CareerTrackClassifier.Result trackResult,
            SearchProfile profile,
            String family,
            String specialization) {
        List<String> finalReasons = new ArrayList<>(reasons);
        if (reason != null && !reason.isBlank()) {
            finalReasons.add(reason);
        }
        EligibilityDecision result = new EligibilityDecision(status, finalReasons,
                trackResult.track, family, specialization);
        return result;
    }

    private EligibilityDecision decision(
            EligibilityDecision.Status status,
            List<String> reasons,
            CareerTrackClassifier.Result trackResult,
            SearchProfile profile,
            String family,
            String specialization) {
        return decision(status, reasons, null, trackResult, profile, family, specialization);
    }

    private LocationResult locationResult(JobRecommendation job, ResolvedLocation resolvedLocation) {
        String jobLocation = safe(job == null ? null : job.location);
        if (jobLocation.isBlank() || resolvedLocation == null
                || safe(resolvedLocation.resolvedLocation).isBlank()) {
            return LocationResult.UNKNOWN;
        }
        if ("Country".equalsIgnoreCase(safe(resolvedLocation.locationType))
                && "US".equalsIgnoreCase(safe(resolvedLocation.countryCode))) {
            return LocationResult.PASS;
        }
        String expected = normalize(resolvedLocation.resolvedLocation);
        String actual = normalize(jobLocation);
        if (actual.contains(expected) || expected.contains(actual)) {
            return LocationResult.PASS;
        }
        if (safe(job == null ? null : job.workMode).toLowerCase(Locale.US).contains("remote")) {
            return LocationResult.PASS;
        }
        return LocationResult.FAIL;
    }

    private String normalize(String value) {
        return safe(value).replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private enum LocationResult {
        PASS,
        FAIL,
        UNKNOWN
    }
}
