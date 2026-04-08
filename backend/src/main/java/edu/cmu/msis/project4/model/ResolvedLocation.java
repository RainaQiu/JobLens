package edu.cmu.msis.project4.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Author: Raina Qiu (yuluq)
 * Holds normalized location information before job search execution.
 */
public class ResolvedLocation {
    public String rawInput;
    public String resolvedLocation;
    public String resolvedLabel;
    public String locationType;
    public String countryCode;
    public String requestedSearchScope;
    public String searchStrategy;
    public List<String> searchLocations = new ArrayList<>();

    public boolean isMultiLocation() {
        return searchLocations != null && searchLocations.size() > 1;
    }
}
