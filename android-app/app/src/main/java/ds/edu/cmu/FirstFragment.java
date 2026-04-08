package ds.edu.cmu;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.List;

import ds.edu.cmu.api.ApiClient;
import ds.edu.cmu.databinding.FragmentFirstBinding;
import ds.edu.cmu.model.JobRecommendation;
import ds.edu.cmu.model.RecommendationRequest;
import ds.edu.cmu.model.RecommendationResponse;

public class FirstFragment extends Fragment {
    private static final String PREFS_NAME = "joblens_prefs";
    private static final String KEY_USER_ID = "last_user_id";
    private static final String KEY_ROLE = "last_role";
    private static final String KEY_LOCATION = "last_location";
    private static final String KEY_EXPERIENCE = "last_experience";
    private static final String KEY_SEARCH_SCOPE = "last_search_scope";
    private static final String SCOPE_AUTO = "AUTO";
    private static final String SCOPE_SPECIFIC = "SPECIFIC";
    private static final String SCOPE_NATIONWIDE_US = "NATIONWIDE_US";

    private FragmentFirstBinding binding;
    private final ApiClient apiClient = new ApiClient();
    private final JobRecommendationAdapter jobAdapter = new JobRecommendationAdapter();

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        configureSearchScopeSpinner();
        configureExperienceSpinner();
        binding.recyclerJobs.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerJobs.setAdapter(jobAdapter);
        binding.recyclerJobs.setHasFixedSize(false);

        restoreLastInputs();
        binding.textSearchSummary.setText(getString(R.string.search_summary_placeholder));
        binding.buttonSearch.setOnClickListener(v -> submitSearch());
        binding.buttonOpenHistory.setOnClickListener(v -> openHistory());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void configureExperienceSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.experience_levels,
                android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerExperience.setAdapter(adapter);
    }

    private void configureSearchScopeSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.search_scope_labels,
                android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerSearchScope.setAdapter(adapter);
    }

    private void submitSearch() {
        String userId = textOf(binding.inputUserId);
        String role = textOf(binding.inputRole);
        String searchScope = selectedSearchScopeCode();
        String location = textOf(binding.inputLocation);
        String experienceLevel = String.valueOf(binding.spinnerExperience.getSelectedItem());

        if (SCOPE_NATIONWIDE_US.equals(searchScope) && location.isEmpty()) {
            location = "United States";
            binding.inputLocation.setText(location);
        }

        if (userId.isEmpty() || role.isEmpty() || location.isEmpty()) {
            showStatus(getString(R.string.validation_message));
            return;
        }

        saveLastInputs(userId, role, location, experienceLevel, searchScope);
        setLoading(true, getString(R.string.loading_recommendations));
        binding.textSearchSummary.setText(getString(R.string.search_summary_placeholder));

        RecommendationRequest request = new RecommendationRequest();
        request.userId = userId;
        request.role = role;
        request.location = location;
        request.experienceLevel = experienceLevel;
        request.searchScope = searchScope;

        apiClient.fetchRecommendations(request, new ApiClient.ApiCallback<RecommendationResponse>() {
            @Override
            public void onSuccess(RecommendationResponse result) {
                requireActivity().runOnUiThread(() -> renderRecommendations(result));
            }

            @Override
            public void onError(String message) {
                requireActivity().runOnUiThread(() -> {
                    setLoading(false, message);
                    binding.textSearchSummary.setText(getString(R.string.search_summary_placeholder));
                    jobAdapter.submitList(null);
                });
            }
        });
    }

    private void renderRecommendations(RecommendationResponse result) {
        setLoading(false, "");
        List<JobRecommendation> jobs = result == null ? null : result.jobs;
        jobAdapter.submitList(jobs);
        String searchSummary = (result == null || result.meta == null || result.meta.searchSummary == null
                || result.meta.searchSummary.trim().isEmpty())
                ? getString(R.string.search_summary_placeholder)
                : result.meta.searchSummary;
        binding.textSearchSummary.setText(searchSummary);

        int count = result == null ? 0 : result.recommendedCount;
        long latencyMs = (result == null || result.meta == null) ? 0 : result.meta.apiLatencyMs;
        int searchedLocations = (result == null || result.meta == null) ? 0 : result.meta.searchedLocationsCount;
        if (count == 0) {
            showStatus(getString(R.string.no_recommendations_found));
        } else {
            showStatus(getString(R.string.recommendations_loaded_message, count, latencyMs, searchedLocations));
        }
    }

    private void openHistory() {
        String userId = textOf(binding.inputUserId);
        if (userId.isEmpty()) {
            showStatus(getString(R.string.user_id_needed_for_history));
            return;
        }

        saveLastInputs(
                userId,
                textOf(binding.inputRole),
                textOf(binding.inputLocation),
                String.valueOf(binding.spinnerExperience.getSelectedItem()),
                selectedSearchScopeCode());

        NavHostFragment.findNavController(FirstFragment.this)
                .navigate(R.id.action_FirstFragment_to_SecondFragment);
    }

    private void setLoading(boolean isLoading, String statusText) {
        binding.buttonSearch.setEnabled(!isLoading);
        binding.buttonOpenHistory.setEnabled(!isLoading);
        showStatus(statusText);
    }

    private void showStatus(String message) {
        binding.textStatus.setText(message);
    }

    private String textOf(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private void restoreLastInputs() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, 0);
        binding.inputUserId.setText(prefs.getString(KEY_USER_ID, ""));
        binding.inputRole.setText(prefs.getString(KEY_ROLE, ""));
        binding.inputLocation.setText(prefs.getString(KEY_LOCATION, ""));

        selectSpinnerValue(binding.spinnerSearchScope.getAdapter(), prefs.getString(KEY_SEARCH_SCOPE, SCOPE_AUTO),
                binding.spinnerSearchScope);
        String savedExperience = prefs.getString(KEY_EXPERIENCE, getString(R.string.experience_any));
        selectSpinnerValue(binding.spinnerExperience.getAdapter(), savedExperience, binding.spinnerExperience);
    }

    private void saveLastInputs(
            String userId,
            String role,
            String location,
            String experienceLevel,
            String searchScope) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, 0);
        prefs.edit()
                .putString(KEY_USER_ID, userId)
                .putString(KEY_ROLE, role)
                .putString(KEY_LOCATION, location)
                .putString(KEY_EXPERIENCE, experienceLevel)
                .putString(KEY_SEARCH_SCOPE, searchScope)
                .apply();
    }

    private String selectedSearchScopeCode() {
        int position = binding.spinnerSearchScope.getSelectedItemPosition();
        switch (position) {
            case 1:
                return SCOPE_SPECIFIC;
            case 2:
                return SCOPE_NATIONWIDE_US;
            default:
                return SCOPE_AUTO;
        }
    }

    private void selectSpinnerValue(android.widget.SpinnerAdapter adapter, String targetValue, android.widget.Spinner spinner) {
        if (adapter == null || targetValue == null) {
            return;
        }
        for (int i = 0; i < adapter.getCount(); i++) {
            Object item = adapter.getItem(i);
            if (item == null) {
                continue;
            }

            String candidate = item.toString();
            if (candidate.equals(targetValue)
                    || (SCOPE_AUTO.equals(targetValue) && candidate.equals(getString(R.string.scope_auto)))
                    || (SCOPE_SPECIFIC.equals(targetValue) && candidate.equals(getString(R.string.scope_specific)))
                    || (SCOPE_NATIONWIDE_US.equals(targetValue) && candidate.equals(getString(R.string.scope_nationwide_us)))) {
                spinner.setSelection(i);
                return;
            }
        }
    }
}
