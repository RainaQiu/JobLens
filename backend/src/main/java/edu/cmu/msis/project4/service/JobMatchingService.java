package edu.cmu.msis.project4.service;

import edu.cmu.msis.project4.model.JobRecommendation;
import edu.cmu.msis.project4.model.RecommendationRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Explainable NLP ranking for the portfolio MVP. It deliberately remains deterministic so
 * scores can be tested and audited. An embedding/LLM reranker can be layered on later.
 */
public class JobMatchingService {
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "in", "is",
            "it", "of", "on", "or", "our", "that", "the", "this", "to", "we", "will", "with",
            "you", "your", "job", "role", "work", "team", "experience", "years");

    private static final List<String> SKILLS = Arrays.asList(
            "python", "java", "javascript", "typescript", "react", "sql", "spark", "hadoop",
            "airflow", "aws", "gcp", "azure", "docker", "kubernetes", "mongodb", "postgresql",
            "pytorch", "tensorflow", "scikit learn", "pandas", "machine learning", "deep learning",
            "nlp", "rag", "langchain", "llm", "agent", "data pipeline", "etl", "snowflake",
            "bigquery", "tableau", "power bi", "git", "rest api", "microservices");

    public List<JobRecommendation> rank(List<JobRecommendation> jobs, RecommendationRequest request) {
        List<JobRecommendation> ranked = new ArrayList<>(jobs == null ? List.of() : jobs);
        for (JobRecommendation job : ranked) {
            score(job, request);
        }
        ranked.sort(Comparator.comparingInt((JobRecommendation job) -> job.matchScore).reversed()
                .thenComparing(job -> safe(job.postedAt)));
        return ranked;
    }

    void score(JobRecommendation job, RecommendationRequest request) {
        String role = safe(request == null ? null : request.role);
        String resume = safe(request == null ? null : request.resumeText);
        String title = safe(job == null ? null : job.title);
        String description = safe(job == null ? null : job.description);
        String jobText = title + " " + description;

        Set<String> roleTokens = tokens(role);
        Set<String> titleTokens = tokens(title);
        Set<String> resumeTokens = tokens(resume);
        Set<String> jobTokens = tokens(jobText);

        double titleCoverage = coverage(roleTokens, titleTokens);
        double resumeCoverage = coverage(resumeTokens, jobTokens);
        Set<String> sharedSkills = sharedSkills(resume, jobText);

        int score = (int) Math.round(50 * titleCoverage);
        score += (int) Math.round(25 * Math.min(1.0, resumeCoverage * 4));
        score += Math.min(20, sharedSkills.size() * 4);
        if (experienceMatches(request == null ? null : request.experienceLevel, jobText)) {
            score += 5;
        }
        job.matchScore = Math.max(0, Math.min(100, score));

        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        if (titleCoverage >= 0.5) {
            reasons.add("Strong role-title alignment");
        } else if (titleCoverage > 0) {
            reasons.add("Partial role-title alignment");
        }
        if (!sharedSkills.isEmpty()) {
            reasons.add("Shared skills: " + sharedSkills.stream().limit(4).collect(Collectors.joining(", ")));
        }
        if (resumeCoverage >= 0.08) {
            reasons.add("Resume experience overlaps with the job description");
        }
        if (experienceMatches(request == null ? null : request.experienceLevel, jobText)) {
            reasons.add("Experience level alignment");
        }
        if (reasons.isEmpty()) {
            reasons.add("Fresh job matching your search filters");
        }
        job.matchReasons = new ArrayList<>(reasons);
    }

    private boolean experienceMatches(String experienceLevel, String jobText) {
        String level = safe(experienceLevel);
        String text = safe(jobText);
        if (level.isBlank() || "any".equals(level)) {
            return false;
        }
        if (level.contains("intern")) {
            return text.contains("intern");
        }
        if (level.contains("entry") || level.contains("junior")) {
            return text.contains("entry") || text.contains("junior") || text.contains("new grad")
                    || text.contains("early career");
        }
        if (level.contains("senior")) {
            return text.contains("senior") || text.contains("lead") || text.contains("staff");
        }
        return text.contains(level);
    }

    private Set<String> sharedSkills(String resume, String jobText) {
        Set<String> matches = new LinkedHashSet<>();
        String normalizedResume = normalizePhrase(resume);
        String normalizedJob = normalizePhrase(jobText);
        for (String skill : SKILLS) {
            if (normalizedResume.contains(skill) && normalizedJob.contains(skill)) {
                matches.add(skill);
            }
        }
        return matches;
    }

    private double coverage(Set<String> source, Set<String> target) {
        if (source.isEmpty() || target.isEmpty()) {
            return 0;
        }
        Set<String> overlap = new HashSet<>(source);
        overlap.retainAll(target);
        return overlap.size() / (double) source.size();
    }

    private Set<String> tokens(String value) {
        return Arrays.stream(normalizePhrase(value).split("\\s+"))
                .filter(token -> token.length() > 1 && !STOP_WORDS.contains(token))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizePhrase(String value) {
        return safe(value).replaceAll("[^a-z0-9+#.]+", " ").trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US).trim();
    }
}
