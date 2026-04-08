package ds.edu.cmu;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
        private final TextView textApplySource;
        private final TextView textMeta;
        private final Button buttonApply;

        HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(R.id.text_history_title);
            textCompany = itemView.findViewById(R.id.text_history_company);
            textLocation = itemView.findViewById(R.id.text_history_location);
            textPostedAt = itemView.findViewById(R.id.text_history_posted_at);
            textRecommendedAt = itemView.findViewById(R.id.text_history_recommended_at);
            textApplySource = itemView.findViewById(R.id.text_history_apply_source);
            textMeta = itemView.findViewById(R.id.text_history_meta);
            buttonApply = itemView.findViewById(R.id.button_history_apply);
        }

        void bind(HistoryItem item) {
            textTitle.setText(valueOrFallback(item == null ? "" : item.title,
                    itemView.getContext().getString(R.string.job_title_fallback)));
            textCompany.setText(valueOrFallback(item == null ? "" : item.company,
                    itemView.getContext().getString(R.string.job_company_fallback)));
            textLocation.setText(valueOrFallback(item == null ? "" : item.location,
                    itemView.getContext().getString(R.string.job_location_fallback)));
            textPostedAt.setText(valueOrFallback(item == null ? "" : item.postedAt,
                    itemView.getContext().getString(R.string.job_posted_fallback)));
            textRecommendedAt.setText(itemView.getContext().getString(
                    R.string.history_recommended_template,
                    valueOrFallback(item == null ? "" : item.recommendedAt, "-")));

            String applySource = valueOrFallback(item == null ? "" : item.applySource,
                    itemView.getContext().getString(R.string.job_apply_source_fallback));
            textApplySource.setText(itemView.getContext().getString(R.string.job_source_template, applySource));

            String metaText = joinMeta(item == null ? "" : item.workMode, item == null ? "" : item.employmentType);
            if (blank(metaText)) {
                textMeta.setVisibility(View.GONE);
            } else {
                textMeta.setVisibility(View.VISIBLE);
                textMeta.setText(metaText);
            }

            String destination = primaryDestination(item);
            buttonApply.setEnabled(!blank(destination));
            buttonApply.setText(buttonLabel(item, destination));
            buttonApply.setOnClickListener(v -> {
                if (blank(destination)) {
                    return;
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(destination));
                itemView.getContext().startActivity(intent);
            });
        }

        private String joinMeta(String left, String right) {
            if (!blank(left) && !blank(right)) {
                return itemView.getContext().getString(R.string.job_meta_template, left, right);
            }
            if (!blank(left)) {
                return itemView.getContext().getString(R.string.job_meta_single, left);
            }
            if (!blank(right)) {
                return itemView.getContext().getString(R.string.job_meta_single, right);
            }
            return "";
        }

        private String primaryDestination(HistoryItem item) {
            if (item == null) {
                return "";
            }
            if (!blank(item.applyLink)) {
                return item.applyLink;
            }
            return valueOrFallback(item.shareLink, "");
        }

        private String buttonLabel(HistoryItem item, String destination) {
            if (blank(destination)) {
                return itemView.getContext().getString(R.string.apply_link_unavailable);
            }
            return blank(item == null ? "" : item.applyLink)
                    ? itemView.getContext().getString(R.string.open_job_page)
                    : itemView.getContext().getString(R.string.apply_now);
        }

        private static String valueOrFallback(String value, String fallback) {
            return blank(value) ? fallback : value;
        }

        private static boolean blank(String value) {
            return value == null || value.trim().isEmpty();
        }
    }
}
