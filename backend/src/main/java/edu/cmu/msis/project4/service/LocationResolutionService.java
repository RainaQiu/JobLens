package edu.cmu.msis.project4.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.cmu.msis.project4.config.AppConfig;
import edu.cmu.msis.project4.model.ResolvedLocation;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Author: Raina Qiu (yuluq)
 * Resolves free-form user locations into a better search strategy for Google Jobs.
 */
public class LocationResolutionService {
    public static final String SCOPE_AUTO = "AUTO";
    public static final String SCOPE_SPECIFIC = "SPECIFIC";
    public static final String SCOPE_NATIONWIDE_US = "NATIONWIDE_US";

    private static final String UNITED_STATES = "United States";
    private static final Set<String> US_ALIASES = Set.of(
            "us", "u.s.", "usa", "u.s.a.", "united states", "united states of america");

    private static final Map<String, String> US_STATE_NAMES = Map.ofEntries(
            Map.entry("AL", "Alabama"), Map.entry("AK", "Alaska"), Map.entry("AZ", "Arizona"),
            Map.entry("AR", "Arkansas"), Map.entry("CA", "California"), Map.entry("CO", "Colorado"),
            Map.entry("CT", "Connecticut"), Map.entry("DE", "Delaware"), Map.entry("FL", "Florida"),
            Map.entry("GA", "Georgia"), Map.entry("HI", "Hawaii"), Map.entry("ID", "Idaho"),
            Map.entry("IL", "Illinois"), Map.entry("IN", "Indiana"), Map.entry("IA", "Iowa"),
            Map.entry("KS", "Kansas"), Map.entry("KY", "Kentucky"), Map.entry("LA", "Louisiana"),
            Map.entry("ME", "Maine"), Map.entry("MD", "Maryland"), Map.entry("MA", "Massachusetts"),
            Map.entry("MI", "Michigan"), Map.entry("MN", "Minnesota"), Map.entry("MS", "Mississippi"),
            Map.entry("MO", "Missouri"), Map.entry("MT", "Montana"), Map.entry("NE", "Nebraska"),
            Map.entry("NV", "Nevada"), Map.entry("NH", "New Hampshire"), Map.entry("NJ", "New Jersey"),
            Map.entry("NM", "New Mexico"), Map.entry("NY", "New York"), Map.entry("NC", "North Carolina"),
            Map.entry("ND", "North Dakota"), Map.entry("OH", "Ohio"), Map.entry("OK", "Oklahoma"),
            Map.entry("OR", "Oregon"), Map.entry("PA", "Pennsylvania"), Map.entry("RI", "Rhode Island"),
            Map.entry("SC", "South Carolina"), Map.entry("SD", "South Dakota"), Map.entry("TN", "Tennessee"),
            Map.entry("TX", "Texas"), Map.entry("UT", "Utah"), Map.entry("VT", "Vermont"),
            Map.entry("VA", "Virginia"), Map.entry("WA", "Washington"), Map.entry("WV", "West Virginia"),
            Map.entry("WI", "Wisconsin"), Map.entry("WY", "Wyoming"), Map.entry("DC", "District of Columbia")
    );

    private static final List<String> US_STATE_PRIORITY = List.of(
            "California,United States",
            "New York,United States",
            "Texas,United States",
            "Washington,United States",
            "Massachusetts,United States",
            "Pennsylvania,United States",
            "New Jersey,United States",
            "Illinois,United States",
            "Virginia,United States",
            "North Carolina,United States",
            "Georgia,United States",
            "Colorado,United States",
            "Maryland,United States",
            "Ohio,United States",
            "Florida,United States",
            "Michigan,United States",
            "Minnesota,United States",
            "Utah,United States",
            "Arizona,United States",
            "Oregon,United States",
            "Tennessee,United States",
            "Missouri,United States",
            "Indiana,United States",
            "Wisconsin,United States",
            "Connecticut,United States",
            "District of Columbia,United States",
            "Delaware,United States",
            "Kansas,United States",
            "Nebraska,United States",
            "Alabama,United States",
            "Arkansas,United States",
            "Alaska,United States",
            "Hawaii,United States",
            "Idaho,United States",
            "Iowa,United States",
            "Kentucky,United States",
            "Louisiana,United States",
            "Maine,United States",
            "Mississippi,United States",
            "Montana,United States",
            "Nevada,United States",
            "New Hampshire,United States",
            "New Mexico,United States",
            "North Dakota,United States",
            "Oklahoma,United States",
            "Rhode Island,United States",
            "South Carolina,United States",
            "South Dakota,United States",
            "Vermont,United States",
            "West Virginia,United States",
            "Wyoming,United States"
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public ResolvedLocation resolve(String rawLocation, String searchScope) throws IOException, InterruptedException {
        String normalizedScope = normalizeScope(searchScope);
        String trimmedLocation = rawLocation == null ? "" : rawLocation.trim();

        if (SCOPE_NATIONWIDE_US.equals(normalizedScope)) {
            if (trimmedLocation.isBlank() || isUnitedStatesAlias(trimmedLocation)) {
                return nationwideUnitedStates(trimmedLocation, normalizedScope);
            }

            ResolvedLocation candidate = resolveSpecific(trimmedLocation, normalizedScope);
            if ("US".equalsIgnoreCase(candidate.countryCode) || "State".equalsIgnoreCase(candidate.locationType)) {
                return nationwideUnitedStates(trimmedLocation, normalizedScope);
            }
            throw new IllegalArgumentException(
                    "Nationwide U.S. search currently supports the United States only.");
        }

        ResolvedLocation specific = resolveSpecific(trimmedLocation, normalizedScope);
        if (SCOPE_AUTO.equals(normalizedScope)
                && "Country".equalsIgnoreCase(specific.locationType)
                && "US".equalsIgnoreCase(specific.countryCode)) {
            return nationwideUnitedStates(trimmedLocation, normalizedScope);
        }
        return specific;
    }

    private ResolvedLocation resolveSpecific(String rawLocation, String requestedScope)
            throws IOException, InterruptedException {
        if (rawLocation == null || rawLocation.isBlank()) {
            throw new IllegalArgumentException("location is required.");
        }

        if (isUnitedStatesAlias(rawLocation)) {
            return singleLocation(rawLocation, UNITED_STATES, UNITED_STATES, "Country", "US", requestedScope);
        }

        String expandedLocation = expandStateAbbreviation(rawLocation);
        ResolvedLocation directState = directUsStateMatch(expandedLocation, requestedScope);
        if (directState != null) {
            return directState;
        }

        try {
            ResolvedLocation apiCandidate = lookupWithLocationsApi(expandedLocation, requestedScope);
            if (apiCandidate != null) {
                return apiCandidate;
            }
        } catch (IOException e) {
            // Fall back to the user's raw location text when location normalization is unavailable.
        }

        return singleLocation(rawLocation, expandedLocation, expandedLocation, "Freeform", "", requestedScope);
    }

    private ResolvedLocation lookupWithLocationsApi(String query, String requestedScope)
            throws IOException, InterruptedException {
        String url = "https://serpapi.com/locations.json?q=" + encode(query) + "&limit=5";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return null;
        }

        JsonElement parsed = JsonParser.parseString(response.body());
        if (!parsed.isJsonArray()) {
            return null;
        }

        JsonArray candidates = parsed.getAsJsonArray();
        if (candidates.isEmpty()) {
            return null;
        }

        JsonObject bestMatch = null;
        int bestScore = Integer.MIN_VALUE;
        String normalizedQuery = normalizeToken(query);
        for (JsonElement element : candidates) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject candidate = element.getAsJsonObject();
            String name = stringValue(candidate, "name");
            String canonicalName = stringValue(candidate, "canonical_name");
            String targetType = stringValue(candidate, "target_type");
            int score = scoreCandidate(normalizedQuery, name, canonicalName, targetType);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = candidate;
            }
        }

        if (bestMatch == null) {
            return null;
        }

        String canonicalName = stringValue(bestMatch, "canonical_name");
        String name = stringValue(bestMatch, "name");
        String targetType = stringValue(bestMatch, "target_type");
        String countryCode = stringValue(bestMatch, "country_code");

        String resolvedLocation = canonicalName.isBlank() ? name : canonicalName;
        String resolvedLabel = resolvedLocation.replace(",", ", ");
        return singleLocation(query, resolvedLocation, resolvedLabel, targetType, countryCode, requestedScope);
    }

    private int scoreCandidate(String normalizedQuery, String name, String canonicalName, String targetType) {
        int score = typeScore(targetType);
        String normalizedName = normalizeToken(name);
        String normalizedCanonical = normalizeToken(canonicalName);

        if (normalizedName.equals(normalizedQuery)) {
            score += 400;
        }
        if (normalizedCanonical.equals(normalizedQuery)) {
            score += 250;
        }
        if (normalizedCanonical.contains(normalizedQuery)) {
            score += 100;
        }
        if (normalizedName.startsWith(normalizedQuery)) {
            score += 80;
        }
        return score;
    }

    private int typeScore(String targetType) {
        if (targetType == null) {
            return 0;
        }
        switch (targetType) {
            case "Country":
                return 300;
            case "State":
                return 260;
            case "City":
                return 240;
            case "DMA Region":
                return 180;
            case "County":
                return 140;
            default:
                return 80;
        }
    }

    private ResolvedLocation directUsStateMatch(String value, String requestedScope) {
        String canonical = canonicalUsState(value);
        if (canonical == null) {
            return null;
        }
        return singleLocation(value, canonical, canonical.replace(",", ", "), "State", "US", requestedScope);
    }

    private String canonicalUsState(String value) {
        String normalized = normalizeToken(value);
        for (Map.Entry<String, String> entry : US_STATE_NAMES.entrySet()) {
            String abbreviation = entry.getKey();
            String fullName = entry.getValue();
            if (normalized.equals(normalizeToken(abbreviation)) || normalized.equals(normalizeToken(fullName))) {
                return fullName + ",United States";
            }
        }
        return null;
    }

    private String expandStateAbbreviation(String value) {
        String[] parts = value.split(",");
        if (parts.length < 2) {
            return value;
        }

        List<String> normalizedParts = new ArrayList<>();
        normalizedParts.add(parts[0].trim());
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.length() == 2) {
                String expanded = US_STATE_NAMES.get(part.toUpperCase(Locale.US));
                normalizedParts.add(expanded == null ? part : expanded);
            } else {
                normalizedParts.add(part);
            }
        }
        return String.join(", ", normalizedParts);
    }

    private ResolvedLocation nationwideUnitedStates(String rawInput, String requestedScope) {
        ResolvedLocation resolved = new ResolvedLocation();
        resolved.rawInput = rawInput == null || rawInput.isBlank() ? UNITED_STATES : rawInput;
        resolved.resolvedLocation = UNITED_STATES;
        resolved.resolvedLabel = UNITED_STATES;
        resolved.locationType = "Country";
        resolved.countryCode = "US";
        resolved.requestedSearchScope = requestedScope;
        resolved.searchStrategy = "us_nationwide";

        int maxStates = Integer.parseInt(AppConfig.get("NATIONWIDE_US_MAX_STATES", "12"));
        int limit = Math.max(1, Math.min(maxStates, US_STATE_PRIORITY.size()));
        resolved.searchLocations.addAll(US_STATE_PRIORITY.subList(0, limit));
        return resolved;
    }

    private ResolvedLocation singleLocation(
            String rawInput,
            String resolvedLocation,
            String resolvedLabel,
            String locationType,
            String countryCode,
            String requestedScope) {
        ResolvedLocation resolved = new ResolvedLocation();
        resolved.rawInput = rawInput;
        resolved.resolvedLocation = resolvedLocation;
        resolved.resolvedLabel = resolvedLabel;
        resolved.locationType = locationType == null || locationType.isBlank() ? "Specific" : locationType;
        resolved.countryCode = countryCode == null ? "" : countryCode;
        resolved.requestedSearchScope = requestedScope;
        resolved.searchStrategy = "specific_location";
        resolved.searchLocations.add(resolvedLocation);
        return resolved;
    }

    private String normalizeScope(String searchScope) {
        if (searchScope == null || searchScope.isBlank()) {
            return SCOPE_AUTO;
        }

        String normalized = searchScope.trim().toUpperCase(Locale.US);
        if (SCOPE_SPECIFIC.equals(normalized) || SCOPE_NATIONWIDE_US.equals(normalized)) {
            return normalized;
        }
        return SCOPE_AUTO;
    }

    private boolean isUnitedStatesAlias(String value) {
        return US_ALIASES.contains(normalizeToken(value));
    }

    private String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private String stringValue(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
