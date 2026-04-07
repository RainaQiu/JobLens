package ds.edu.cmu;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.List;

import ds.edu.cmu.api.ApiClient;
import ds.edu.cmu.databinding.FragmentSecondBinding;
import ds.edu.cmu.model.HistoryItem;

public class SecondFragment extends Fragment {
    private static final String PREFS_NAME = "joblens_prefs";
    private static final String KEY_USER_ID = "last_user_id";

    private FragmentSecondBinding binding;
    private final ApiClient apiClient = new ApiClient();
    private final HistoryItemAdapter historyAdapter = new HistoryItemAdapter();

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentSecondBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.recyclerHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerHistory.setAdapter(historyAdapter);

        binding.buttonRefreshHistory.setOnClickListener(v -> loadHistory());
        binding.buttonBackToSearch.setOnClickListener(v ->
                NavHostFragment.findNavController(SecondFragment.this)
                        .navigate(R.id.action_SecondFragment_to_FirstFragment));

        loadHistory();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void loadHistory() {
        String userId = lastUserId();
        binding.textCurrentUser.setText(getString(R.string.current_user_template, userId.isEmpty() ? "-" : userId));

        if (userId.isEmpty()) {
            historyAdapter.submitList(null);
            binding.textHistoryStatus.setText(getString(R.string.history_requires_user));
            return;
        }

        binding.buttonRefreshHistory.setEnabled(false);
        binding.textHistoryStatus.setText(getString(R.string.loading_history));

        apiClient.fetchHistory(userId, new ApiClient.ApiCallback<List<HistoryItem>>() {
            @Override
            public void onSuccess(List<HistoryItem> result) {
                requireActivity().runOnUiThread(() -> renderHistory(result));
            }

            @Override
            public void onError(String message) {
                requireActivity().runOnUiThread(() -> {
                    binding.buttonRefreshHistory.setEnabled(true);
                    historyAdapter.submitList(null);
                    binding.textHistoryStatus.setText(message);
                });
            }
        });
    }

    private void renderHistory(List<HistoryItem> items) {
        binding.buttonRefreshHistory.setEnabled(true);
        historyAdapter.submitList(items);

        if (items == null || items.isEmpty()) {
            binding.textHistoryStatus.setText(getString(R.string.history_empty_message));
        } else {
            binding.textHistoryStatus.setText(getString(R.string.history_loaded_message, items.size()));
        }
    }

    private String lastUserId() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, 0);
        return prefs.getString(KEY_USER_ID, "").trim();
    }
}
