package edu.cmu.msis.project4.service;

import edu.cmu.msis.project4.model.JobRecommendation;
import edu.cmu.msis.project4.model.MatchFeatures;
import edu.cmu.msis.project4.model.RecommendationRequest;
import edu.cmu.msis.project4.model.SearchProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Explainable soft ranking for role fit, skills, and resume evidence. */
public class JobMatchingService {
    private static final String SCORING_VERSION = "hybrid-v1";
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "in", "is",
            "it", "of", "on", "or", "our", "that", "the", "this", "to", "we", "will", "with",
            "you", "your", "job", "role", "work", "team", "experience", "years", "required",
            "preferred", "minimum", "qualifications", "plus", "nice", "have");

    private final RoleProfileExpander roleProfileExpander;

    public JobMatchingService() {
        this(new RoleProfileExpander());
    }

    JobMatchingService(RoleProfileExpander roleProfileExpander) {
        this.roleProfileExpander = roleProfileExpander;
    }

    public List<JobRecommendation> rank(List<JobRecommendation> jobs, RecommendationRequest request) {
        SearchProfile profile = SearchProfile.from(request, roleProfileExpander);
        return rank(jobs, request, profile);
    }

    public List<JobRecommendation> rank(
            List<JobRecommendation> jobs,
            RecommendationRequest request,
            SearchProfile profile) {
        List<JobRecommendation> ranked = new ArrayList<>(jobs == null ? List.of() : jobs);
        for (JobRecommendation job : ranked) {
            score(job, request, profile);
        }
        ranked.sort(Comparator.comparingInt((JobRecommendation job) -> job.matchScore).reversed()
                .thenComparing(job -> safe(job.postedAt)));
        return ranked;
    }

    MatchFeatures score(JobRecommendation job, RecommendationRequest request, SearchProfile profile) {
        if (job == null) {
            return new MatchFeatures(0, 0, 0, 0, List.of(), List.of(), List.of(), List.of());
        }
        SearchProfile effectiveProfile = profile == null
                ? SearchProfile.from(request, roleProfileExpander) : profile;
        String resume = safe(request == null ? null : request.resumeText);
        String jobText = safe(job.title) + " " + safe(job.description);
        SearchProfile.RoleExpansion jobExpansion = roleProfileExpander.expand(job.title, "", job.description);

        int roleFit = roleFit(effectiveProfile, jobExpansion, request);
        RequirementSections sections = splitRequirements(job.description);
        Set<String> resumeSkills = roleProfileExpander.canonicalSkillsInText(resume);
        Set<String> requiredSkills = roleProfileExpander.canonicalSkillsInText(sections.required);
        Set<String> preferredSkills = roleProfileExpander.canonicalSkillsInText(sections.preferred);
        Set<String> allJobSkills = roleProfileExpander.canonicalSkillsInText(jobText);

        List<String> requiredMatches = intersection(requiredSkills, resumeSkills);
        List<String> missingRequired = difference(requiredSkills, resumeSkills);
        List<String> preferredMatches = intersection(preferredSkills, resumeSkills);
        int requiredScore = coverageScore(requiredSkills, resumeSkills);
        int preferredScore = coverageScore(preferredSkills, resumeSkills);

        Set<String> resumeTokens = tokens(resume);
        Set<String> jobTokens = tokens(jobText);
        List<String> evidence = intersection(resumeTokens, jobTokens);
        int evidenceScore = coverageScore(jobTokens, resumeTokens);

        MatchFeatures features = new MatchFeatures(
                roleFit, requiredScore, preferredScore, evidenceScore,
                requiredMatches, missingRequired, preferredMatches, evidence);
        apply(job, features, allJobSkills, resumeSkills);
        return features;
    }

    private void apply(
            JobRecommendation job,
            MatchFeatures features,
            Set<String> allJobSkills,
            Set<String> resumeSkills) {
        job.roleFitScore = features.roleFitScore;
        job.requiredSkillScore = features.requiredSkillScore;
        job.preferredSkillScore = features.preferredSkillScore;
        job.evidenceFitScore = features.evidenceFitScore;
        job.deterministicScore = features.deterministicScore();
        job.matchScore = job.deterministicScore;
        job.scoringVersion = SCORING_VERSION;
        job.requiredSkillMatches = new ArrayList<>(features.requiredSkillMatches);
        job.missingRequiredSkills = new ArrayList<>(features.missingRequiredSkills);
        job.preferredSkillMatches = new ArrayList<>(features.preferredSkillMatches);
        job.evidenceHighlights = new ArrayList<>(features.evidenceHighlights.stream().limit(6).toList());

        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        if (features.roleFitScore >= 80) {
            reasons.add("Role fit: strong functional alignment");
        } else if (features.roleFitScore > 0) {
            reasons.add("Role fit: partial functional alignment");
        }
        List<String> sharedSkills = intersection(allJobSkills, resumeSkills);
        if (!sharedSkills.isEmpty()) {
            reasons.add("Shared skills: " + String.join(", ", sharedSkills.stream().limit(4).toList()));
        }
        if (!features.requiredSkillMatches.isEmpty()) {
            reasons.add("Required skills matched: " + String.join(", ", features.requiredSkillMatches));
        }
        if (!features.missingRequiredSkills.isEmpty()) {
            reasons.add("Required skill gaps: " + String.join(", ", features.missingRequiredSkills));
        }
        if (!features.preferredSkillMatches.isEmpty()) {
            reasons.add("Preferred skills matched: " + String.join(", ", features.preferredSkillMatches));
        }
        if (features.evidenceFitScore >= 20) {
            reasons.add("Resume evidence overlaps with the job description");
        }
        if (reasons.isEmpty()) {
            reasons.add("Fresh job matching your search role");
        }
        job.matchReasons = new ArrayList<>(reasons);
    }

    private int roleFit(
            SearchProfile profile,
            SearchProfile.RoleExpansion jobExpansion,
            RecommendationRequest request) {
        String expectedFamily = safe(profile == null ? null : profile.roleFamily);
        String jobFamily = safe(jobExpansion.canonicalFamily);
        if (!expectedFamily.isBlank() && !jobFamily.isBlank()) {
            if (!normalize(expectedFamily).equals(normalize(jobFamily))) {
                return 0;
            }
            String expectedSpecialization = safe(profile.specialization);
            if (expectedSpecialization.isBlank()) {
                return 100;
            }
            String actualSpecialization = safe(jobExpansion.canonicalSpecialization);
            if (actualSpecialization.isBlank()) {
                return 60;
            }
            return normalize(expectedSpecialization).equals(normalize(actualSpecialization)) ? 100 : 0;
        }

        Set<String> roleTokens = tokens(request == null ? null : request.role);
        Set<String> titleTokens = tokens(jobExpansion.canonicalFamily);
        return coverageScore(roleTokens, titleTokens);
    }

    private RequirementSections splitRequirements(String description) {
        String text = safe(description);
        String lower = normalize(text);
        int requiredAt = firstMarker(lower, "required", "minimum qualifications", "must have", "basic qualifications");
        int preferredAt = firstMarker(lower, "preferred", "nice to have", "bonus", "plus");
        if (requiredAt >= 0 && preferredAt >= 0 && preferredAt > requiredAt) {
            return new RequirementSections(text.substring(requiredAt, preferredAt), text.substring(preferredAt));
        }
        if (requiredAt >= 0) {
            return new RequirementSections(text.substring(requiredAt), "");
        }
        if (preferredAt >= 0) {
            return new RequirementSections("", text.substring(preferredAt));
        }
        return new RequirementSections("", text);
    }

    private int firstMarker(String text, String... markers) {
        int best = -1;
        for (String marker : markers) {
            int index = text.indexOf(marker);
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
    }

    private int coverageScore(Set<String> source, Set<String> target) {
        if (source == null || source.isEmpty()) {
            return 100;
        }
        Set<String> overlap = new HashSet<>(source);
        overlap.retainAll(target == null ? Set.of() : target);
        return (int) Math.round(overlap.size() * 100.0 / source.size());
    }

    private List<String> intersection(Set<String> left, Set<String> right) {
        LinkedHashSet<String> result = new LinkedHashSet<>(left == null ? Set.of() : left);
        result.retainAll(right == null ? Set.of() : right);
        return new ArrayList<>(result);
    }

    private List<String> difference(Set<String> left, Set<String> right) {
        LinkedHashSet<String> result = new LinkedHashSet<>(left == null ? Set.of() : left);
        result.removeAll(right == null ? Set.of() : right);
        return new ArrayList<>(result);
    }

    private Set<String> tokens(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(normalized.split("\\s+"))
                .filter(token -> token.length() > 1 && !STOP_WORDS.contains(token))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalize(String value) {
        return safe(value).toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9+#.]+", " ").trim().replaceAll("\\s+", " ");
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class RequirementSections {
        private final String required;
        private final String preferred;

        private RequirementSections(String required, String preferred) {
            this.required = required;
            this.preferred = preferred;
        }
    }
}
