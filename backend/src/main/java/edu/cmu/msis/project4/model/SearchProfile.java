package edu.cmu.msis.project4.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Normalized request data shared by retrieval, filtering, and ranking. */
public final class SearchProfile {
    public final String role;
    public final String specialization;
    public final String location;
    public final String searchScope;
    public final String resumeText;
    public final CareerTrack careerTrack;
    public final List<String> queryVariants;
    public final Set<String> requiredSkillHints;
    public final Set<String> preferredSkillHints;
    public final String roleFamily;

    private SearchProfile(
            String role,
            String specialization,
            String location,
            String searchScope,
            String resumeText,
            CareerTrack careerTrack,
            RoleExpansion expansion) {
        this.role = role;
        this.specialization = specialization;
        this.location = location;
        this.searchScope = searchScope;
        this.resumeText = resumeText;
        this.careerTrack = careerTrack;
        this.roleFamily = expansion.canonicalFamily;
        this.queryVariants = expansion.queryVariants;
        this.requiredSkillHints = expansion.requiredSkillHints;
        this.preferredSkillHints = expansion.preferredSkillHints;
    }

    public static SearchProfile from(RecommendationRequest request, ExpansionProvider expander) {
        RecommendationRequest safe = request == null ? new RecommendationRequest() : request;
        String role = trim(safe.role);
        String specialization = trim(safe.specialization);
        RoleExpansion expansion = expander == null
                ? new RoleExpansion(role, specialization, List.of(role), Set.of(), Set.of())
                : expander.expand(role, specialization, safe.resumeText);
        return new SearchProfile(
                role,
                specialization,
                trim(safe.location),
                trim(safe.searchScope),
                safe.resumeText == null ? "" : safe.resumeText,
                CareerTrack.parse(safe.careerTrack, safe.experienceLevel),
                expansion == null
                        ? new RoleExpansion(role, specialization, List.of(role), Set.of(), Set.of())
                        : expansion);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    @FunctionalInterface
    public interface ExpansionProvider {
        RoleExpansion expand(String role, String specialization, String resumeText);
    }

    public static final class RoleExpansion {
        public final String canonicalFamily;
        public final String canonicalSpecialization;
        public final List<String> queryVariants;
        public final Set<String> requiredSkillHints;
        public final Set<String> preferredSkillHints;

        public RoleExpansion(
                String canonicalFamily,
                String canonicalSpecialization,
                List<String> queryVariants,
                Set<String> requiredSkillHints,
                Set<String> preferredSkillHints) {
            this.canonicalFamily = canonicalFamily == null ? "" : canonicalFamily;
            this.canonicalSpecialization = canonicalSpecialization == null ? "" : canonicalSpecialization;
            this.queryVariants = Collections.unmodifiableList(new ArrayList<>(
                    queryVariants == null ? List.of() : queryVariants));
            this.requiredSkillHints = Collections.unmodifiableSet(new LinkedHashSet<>(
                    requiredSkillHints == null ? Set.of() : requiredSkillHints));
            this.preferredSkillHints = Collections.unmodifiableSet(new LinkedHashSet<>(
                    preferredSkillHints == null ? Set.of() : preferredSkillHints));
        }
    }
}
