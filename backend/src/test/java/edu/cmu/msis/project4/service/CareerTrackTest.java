package edu.cmu.msis.project4.service;

import edu.cmu.msis.project4.model.CareerTrack;
import edu.cmu.msis.project4.model.RecommendationRequest;
import edu.cmu.msis.project4.model.SearchProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CareerTrackTest {
    @Test
    void normalizesTheThreeUserFacingCareerTracks() {
        assertEquals(CareerTrack.INTERNSHIP, CareerTrack.parse("Internship", null));
        assertEquals(CareerTrack.NEW_GRADUATE, CareerTrack.parse("New Graduate / Early Career", null));
        assertEquals(CareerTrack.GENERAL_FULL_TIME, CareerTrack.parse("General Full-time", null));
        assertEquals(CareerTrack.ANY, CareerTrack.parse("Any", null));
    }

    @Test
    void mapsLegacyExperienceLevelValuesWithoutChangingExistingClients() {
        assertEquals(CareerTrack.INTERNSHIP, CareerTrack.parse(null, "Internship"));
        assertEquals(CareerTrack.NEW_GRADUATE, CareerTrack.parse(null, "Entry level"));
        assertEquals(CareerTrack.ANY, CareerTrack.parse(null, "Any"));
    }

    @Test
    void explicitCareerTrackWinsOverLegacyFieldAndBuildsSearchProfile() {
        RecommendationRequest request = new RecommendationRequest();
        request.role = "Android Software Engineer";
        request.location = "United States";
        request.careerTrack = "NEW_GRADUATE";
        request.experienceLevel = "Internship";
        request.specialization = "Android";

        SearchProfile profile = SearchProfile.from(request, (role, specialization, resumeText) ->
                new SearchProfile.RoleExpansion("Software Engineer", "Android", List.of(role), Set.of(), Set.of()));

        assertEquals(CareerTrack.NEW_GRADUATE, profile.careerTrack);
        assertEquals("Android Software Engineer", profile.role);
        assertEquals("Android", profile.specialization);
    }
}
