package edu.cmu.msis.project4.service;

import edu.cmu.msis.project4.model.SearchProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleProfileExpanderTest {
    private final RoleProfileExpander expander = new RoleProfileExpander();

    @Test
    void expandsGenericSoftwareEngineeringWithoutUnboundedQueries() {
        SearchProfile.RoleExpansion expansion = expander.expand("Software Engineer", "", "");

        assertEquals("Software Engineer", expansion.canonicalFamily);
        assertTrue(expansion.queryVariants.contains("Software Engineer"));
        assertTrue(expansion.queryVariants.size() <= 3);
    }

    @Test
    void keepsAndroidSpecializationSeparateFromIos() {
        SearchProfile.RoleExpansion expansion = expander.expand("Android Software Engineer", "", "");

        assertEquals("Software Engineer", expansion.canonicalFamily);
        assertEquals("Android", expansion.canonicalSpecialization);
        assertFalse(expansion.queryVariants.stream().anyMatch(query -> query.toLowerCase().contains("ios")));
    }

    @Test
    void canonicalizesSkillAliases() {
        assertEquals("bigquery", expander.canonicalSkill("Google Cloud BigQuery"));
        assertEquals("machine learning", expander.canonicalSkill("machine-learning"));
    }
}
