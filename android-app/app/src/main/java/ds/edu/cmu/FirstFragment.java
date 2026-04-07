package ds.edu.cmu;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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

        configureExperienceSpinner();
        binding.recyclerJobs.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerJobs.setAdapter(jobAdapter);
        binding.recyclerJobs.setHasFixedSize(false);

        restoreLastInputs();
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

    private void submitSearch() {
        String userId = textOf(binding.inputUserId);
        String role = textOf(binding.inputRole);
        String location = textOf(binding.inputLocation);
        String experienceLevel = String.valueOf(binding.spinnerExperience.getSelectedItem());

        if (userId.isEmpty() || role.isEmpty() || location.isEmpty()) {
            showStatus(getString(R.string.validation_message));
            return;
        }

        saveLastInputs(userId, role, location, experienceLevel);
        setLoading(true, getString(R.string.loading_recommendations));

        RecommendationRequest request = new RecommendationRequest();
        request.userId = userId;
        request.role = role;
        request.location = location;
        request.experienceLevel = experienceLevel;

        apiClient.fetchRecommendations(request, new ApiClient.ApiCallback<RecommendationResponse>() {
            @Override
            public void onSuccess(RecommendationResponse result) {
                requireActivity().runOnUiThread(() -> renderRecommendations(result));
            }

            @Override
            public void onError(String message) {
                requireActivity().runOnUiThread(() -> {
                    setLoading(false, message);
                    jobAdapter.submitList(null);
                });
            }
        });
    }

    private void renderRecommendations(RecommendationResponse result) {
        setLoading(false, "");
        List<JobRecommendation> jobs = result == null ? null : result.jobs;
        jobAdapter.submitList(jobs);

        int count = result == null ? 0 : result.recommendedCount;
        long latencyMs = (result == null || result.meta == null) ? 0 : result.meta.apiLatencyMs;
        if (count == 0) {
            showStatus(getString(R.string.no_recommendations_found));
        } else {
            showStatus(getString(R.string.recommendations_loaded_message, count, latencyMs));
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
                String.valueOf(binding.spinnerExperience.getSelectedItem()));

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

    private String textOf(android.widget.EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private void restoreLastInputs() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, 0);
        binding.inputUserId.setText(prefs.getString(KEY_USER_ID, ""));
        binding.inputRole.setText(prefs.getString(KEY_ROLE, ""));
        binding.inputLocation.setText(prefs.getString(KEY_LOCATION, ""));

        String savedExperience = prefs.getString(KEY_EXPERIENCE, getString(R.string.experience_any));
        ArrayAdapter<?> adapter = (ArrayAdapter<?>) binding.spinnerExperience.getAdapter();
        if (adapter == null) {
            return;
        }
        for (int i = 0; i < adapter.getCount(); i++) {
            Object item = adapter.getItem(i);
            if (item != null && savedExperience.equals(item.toString())) {
                binding.spinnerExperience.setSelection(i);
                break;
            }
        }
    }

    private void saveLastInputs(String userId, String role, String location, String experienceLevel) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, 0);
        prefs.edit()
                .putString(KEY_USER_ID, userId)
                .putString(KEY_ROLE, role)
                .putString(KEY_LOCATION, location)
                .putString(KEY_EXPERIENCE, experienceLevel)
                .apply();
    }
}
