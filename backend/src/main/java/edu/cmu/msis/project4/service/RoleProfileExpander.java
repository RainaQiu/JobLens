package edu.cmu.msis.project4.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import edu.cmu.msis.project4.model.SearchProfile;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Loads bounded role and skill aliases used by retrieval and deterministic matching. */
public class RoleProfileExpander implements SearchProfile.ExpansionProvider {
    private static final String ROLE_RESOURCE = "/matching/role-aliases.json";
    private static final String SKILL_RESOURCE = "/matching/skill-aliases.json";
    private static final int MAX_QUERY_VARIANTS = 3;

    private final List<RoleDefinition> roles;
    private final Map<String, String> skillAliases;

    public RoleProfileExpander() {
        Gson gson = new Gson();
        this.roles = readList(gson, ROLE_RESOURCE, new TypeToken<List<RoleDefinition>>() { }.getType());
        List<SkillDefinition> skills = readList(gson, SKILL_RESOURCE,
                new TypeToken<List<SkillDefinition>>() { }.getType());
        Map<String, String> aliases = new LinkedHashMap<>();
        for (SkillDefinition skill : skills) {
            if (skill == null || skill.canonical == null || skill.canonical.isBlank()) {
                continue;
            }
            String canonical = normalize(skill.canonical);
            aliases.put(canonical, canonical);
            if (skill.aliases != null) {
                for (String alias : skill.aliases) {
                    if (alias != null && !alias.isBlank()) {
                        aliases.put(normalize(alias), canonical);
                    }
                }
            }
        }
        this.skillAliases = Collections.unmodifiableMap(aliases);
    }

    @Override
    public SearchProfile.RoleExpansion expand(String role, String specialization, String resumeText) {
        String normalizedRole = normalize(role);
        RoleDefinition best = bestRole(normalizedRole);
        String family = best == null || isBlank(best.family) ? titleCase(role) : best.family;
        String detectedSpecialization = detectSpecialization(best, normalizedRole, specialization);

        LinkedHashSet<String> queries = new LinkedHashSet<>();
        if (!isBlank(detectedSpecialization)) {
            queries.add(detectedSpecialization + " " + family);
        }
        queries.add(family);
        if (best != null && best.aliases != null) {
            for (String alias : best.aliases) {
                if (!isBlank(alias)) {
                    queries.add(alias);
                }
                if (queries.size() >= MAX_QUERY_VARIANTS) {
                    break;
                }
            }
        }
        if (queries.isEmpty()) {
            queries.add(isBlank(role) ? "" : role.trim());
        }

        Set<String> required = canonicalSkills(best == null ? null : best.requiredSkills);
        Set<String> preferred = canonicalSkills(best == null ? null : best.preferredSkills);
        return new SearchProfile.RoleExpansion(
                family,
                detectedSpecialization,
                new ArrayList<>(queries),
                required,
                preferred);
    }

    public String canonicalSkill(String skill) {
        String normalized = normalize(skill);
        if (normalized.isBlank()) {
            return "";
        }
        String exact = skillAliases.get(normalized);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, String> entry : skillAliases.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return normalized;
    }

    public Set<String> canonicalSkillsInText(String text) {
        String normalized = normalize(text);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String alias : skillAliases.keySet()) {
            if (normalized.contains(alias)) {
                result.add(skillAliases.get(alias));
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private RoleDefinition bestRole(String normalizedRole) {
        RoleDefinition best = null;
        int bestScore = 0;
        for (RoleDefinition candidate : roles) {
            if (candidate == null) {
                continue;
            }
            int score = phraseScore(normalizedRole, normalize(candidate.family));
            if (candidate.aliases != null) {
                for (String alias : candidate.aliases) {
                    score = Math.max(score, phraseScore(normalizedRole, normalize(alias)));
                }
            }
            if (candidate.specializations != null) {
                for (Map.Entry<String, List<String>> entry : candidate.specializations.entrySet()) {
                    score = Math.max(score, phraseScore(normalizedRole, normalize(entry.getKey())));
                    if (entry.getValue() != null) {
                        for (String alias : entry.getValue()) {
                            score = Math.max(score, phraseScore(normalizedRole, normalize(alias)));
                        }
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private int phraseScore(String input, String phrase) {
        if (isBlank(input) || isBlank(phrase)) {
            return 0;
        }
        if (input.equals(phrase)) {
            return 1000;
        }
        if (input.contains(phrase)) {
            return 600 + phrase.length();
        }
        if (phrase.contains(input)) {
            return 300 + input.length();
        }
        int overlap = 0;
        for (String token : phrase.split("\\s+")) {
            if (input.contains(token)) {
                overlap++;
            }
        }
        return overlap;
    }

    private String detectSpecialization(RoleDefinition role, String normalizedRole, String requested) {
        if (role == null || role.specializations == null) {
            return isBlank(requested) ? "" : titleCase(requested);
        }
        for (Map.Entry<String, List<String>> entry : role.specializations.entrySet()) {
            if (normalizedRole.contains(normalize(entry.getKey()))) {
                return entry.getKey();
            }
            if (entry.getValue() != null) {
                for (String alias : entry.getValue()) {
                    if (normalizedRole.contains(normalize(alias))) {
                        return entry.getKey();
                    }
                }
            }
        }
        return isBlank(requested) ? "" : titleCase(requested);
    }

    private Set<String> canonicalSkills(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String canonical = canonicalSkill(value);
                if (!canonical.isBlank()) {
                    result.add(canonical);
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9+#.]+", " ").trim().replaceAll("\\s+", " ");
    }

    private String titleCase(String value) {
        if (isBlank(value)) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (String token : value.trim().split("\\s+")) {
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
        }
        return result.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private <T> T readList(Gson gson, String resource, java.lang.reflect.Type type) {
        try (InputStream input = RoleProfileExpander.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing matching resource: " + resource);
            }
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                return gson.fromJson(reader, type);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not load matching resource: " + resource, e);
        }
    }

    private static class RoleDefinition {
        String family;
        List<String> aliases;
        Map<String, List<String>> specializations;
        List<String> requiredSkills;
        List<String> preferredSkills;
    }

    private static class SkillDefinition {
        String canonical;
        List<String> aliases;
    }
}
