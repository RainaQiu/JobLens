package ds.edu.cmu;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ds.edu.cmu.model.HistoryItem;

/**
 * Author: Raina Qiu (yuluq)
 */
public class HistoryItemAdapter extends RecyclerView.Adapter<HistoryItemAdapter.HistoryViewHolder> {
    private final List<HistoryItem> items = new ArrayList<>();

    public void submitList(List<HistoryItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        private final TextView textTitle;
        private final TextView textCompany;
        private final TextView textLocation;
        private final TextView textPostedAt;
        private final TextView textRecommendedAt;
        private final TextView textApplyLink;

        HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(R.id.text_history_title);
            textCompany = itemView.findViewById(R.id.text_history_company);
            textLocation = itemView.findViewById(R.id.text_history_location);
            textPostedAt = itemView.findViewById(R.id.text_history_posted_at);
            textRecommendedAt = itemView.findViewById(R.id.text_history_recommended_at);
            textApplyLink = itemView.findViewById(R.id.text_history_apply_link);
        }

        void bind(HistoryItem item) {
            textTitle.setText(item == null || blank(item.title) ? "Untitled role" : item.title);
            textCompany.setText(label("Company", valueOrFallback(item == null ? "" : item.company, "Unknown")));
            textLocation.setText(label("Location", valueOrFallback(item == null ? "" : item.location, "Unknown")));
            textPostedAt.setText(label("Posted", valueOrFallback(item == null ? "" : item.postedAt, "Unknown")));
            textRecommendedAt.setText(label("Recommended", valueOrFallback(item == null ? "" : item.recommendedAt, "Unknown")));
            textApplyLink.setText(label("Apply link", valueOrFallback(item == null ? "" : item.applyLink, "Not provided")));
        }

        private static String label(String label, String value) {
            return label + ": " + value;
        }

        private static String valueOrFallback(String value, String fallback) {
            return blank(value) ? fallback : value;
        }

        private static boolean blank(String value) {
            return value == null || value.trim().isEmpty();
        }
    }
}

