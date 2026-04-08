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

import java.util.ArrayList;
import java.util.List;

import ds.edu.cmu.api.ApiClient;
import ds.edu.cmu.databinding.FragmentSecondBinding;
import ds.edu.cmu.model.HistoryItem;

/**
 * Author: Raina Qiu (yuluq)
 */
public class SecondFragment extends Fragment {
    private static final String PREFS_NAME = "joblens_prefs";
    private static final String KEY_USER_ID = "last_user_id";
    private static final int PAGE_SIZE = 10;

    private FragmentSecondBinding binding;
    private final ApiClient apiClient = new ApiClient();
    private final HistoryItemAdapter historyAdapter = new HistoryItemAdapter();
    private final List<HistoryItem> allHistoryItems = new ArrayList<>();
    private int currentHistoryPage = 0;

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
        binding.buttonHistoryPrevPage.setOnClickListener(v -> goToPreviousHistoryPage());
        binding.buttonHistoryNextPage.setOnClickListener(v -> goToNextHistoryPage());

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
            allHistoryItems.clear();
            currentHistoryPage = 0;
            renderHistoryPage();
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
                    allHistoryItems.clear();
                    currentHistoryPage = 0;
                    renderHistoryPage();
                    binding.textHistoryStatus.setText(message);
                });
            }
        });
    }

    private void renderHistory(List<HistoryItem> items) {
        binding.buttonRefreshHistory.setEnabled(true);
        allHistoryItems.clear();
        if (items != null) {
            allHistoryItems.addAll(items);
        }
        currentHistoryPage = 0;
        renderHistoryPage();

        if (items == null || items.isEmpty()) {
            binding.textHistoryStatus.setText(getString(R.string.history_empty_message));
        } else {
            binding.textHistoryStatus.setText(getString(R.string.history_loaded_message, items.size()));
        }
    }

    private void goToPreviousHistoryPage() {
        if (currentHistoryPage <= 0) {
            return;
        }
        currentHistoryPage--;
        renderHistoryPage();
    }

    private void goToNextHistoryPage() {
        int totalPages = totalPages(allHistoryItems.size());
        if (currentHistoryPage >= totalPages - 1) {
            return;
        }
        currentHistoryPage++;
        renderHistoryPage();
    }

    private void renderHistoryPage() {
        int total = allHistoryItems.size();
        int totalPages = totalPages(total);

        if (total == 0) {
            historyAdapter.submitList(new ArrayList<>());
            binding.layoutHistoryPagination.setVisibility(View.GONE);
            binding.textHistoryPageInfo.setText(getString(R.string.page_indicator_default));
            return;
        }

        currentHistoryPage = Math.max(0, Math.min(currentHistoryPage, totalPages - 1));
        int fromIndex = currentHistoryPage * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, total);

        historyAdapter.submitList(new ArrayList<>(allHistoryItems.subList(fromIndex, toIndex)));
        binding.layoutHistoryPagination.setVisibility(total > PAGE_SIZE ? View.VISIBLE : View.GONE);
        binding.buttonHistoryPrevPage.setEnabled(currentHistoryPage > 0);
        binding.buttonHistoryNextPage.setEnabled(currentHistoryPage < totalPages - 1);
        binding.textHistoryPageInfo.setText(
                getString(R.string.page_indicator_template, currentHistoryPage + 1, totalPages));
    }

    private int totalPages(int totalItems) {
        if (totalItems <= 0) {
            return 1;
        }
        return (totalItems + PAGE_SIZE - 1) / PAGE_SIZE;
    }

    private String lastUserId() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, 0);
        return prefs.getString(KEY_USER_ID, "").trim();
    }
}
