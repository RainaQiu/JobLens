package edu.cmu.msis.project4.model;

/** Persisted profile used by the daily recommendation workflow. */
public class UserPreference {
    public String userId;
    public String email;
    public String role;
    public String location;
    public String experienceLevel;
    public String searchScope;
    public String resumeText;
    public boolean active = true;
}
