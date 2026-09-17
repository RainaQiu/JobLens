package edu.cmu.msis.project4.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Auditable soft-match components used before optional LLM blending. */
public final class MatchFeatures {
    public final int roleFitScore;
    public final int requiredSkillScore;
    public final int preferredSkillScore;
    public final int evidenceFitScore;
    public final List<String> requiredSkillMatches;
    public final List<String> missingRequiredSkills;
    public final List<String> preferredSkillMatches;
    public final List<String> evidenceHighlights;

    public MatchFeatures(
            int roleFitScore,
            int requiredSkillScore,
            int preferredSkillScore,
            int evidenceFitScore,
            List<String> requiredSkillMatches,
            List<String> missingRequiredSkills,
            List<String> preferredSkillMatches,
            List<String> evidenceHighlights) {
        this.roleFitScore = clamp(roleFitScore);
        this.requiredSkillScore = clamp(requiredSkillScore);
        this.preferredSkillScore = clamp(preferredSkillScore);
        this.evidenceFitScore = clamp(evidenceFitScore);
        this.requiredSkillMatches = immutable(requiredSkillMatches);
        this.missingRequiredSkills = immutable(missingRequiredSkills);
        this.preferredSkillMatches = immutable(preferredSkillMatches);
        this.evidenceHighlights = immutable(evidenceHighlights);
    }

    public int deterministicScore() {
        return clamp((int) Math.round(roleFitScore * 0.40
                + requiredSkillScore * 0.35
                + preferredSkillScore * 0.15
                + evidenceFitScore * 0.10));
    }

    private static List<String> immutable(List<String> values) {
        return Collections.unmodifiableList(new ArrayList<>(values == null ? List.of() : values));
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }
}
