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

import ds.edu.cmu.model.JobRecommendation;

/**
 * Author: Raina Qiu (yuluq)
 */
public class JobRecommendationAdapter extends RecyclerView.Adapter<JobRecommendationAdapter.JobViewHolder> {
    private final List<JobRecommendation> items = new ArrayList<>();

    public void submitList(List<JobRecommendation> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public JobViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_job, parent, false);
        return new JobViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull JobViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class JobViewHolder extends RecyclerView.ViewHolder {
        private final TextView textTitle;
        private final TextView textCompany;
        private final TextView textLocation;
        private final TextView textPostedAt;
        private final TextView textMeta;
        private final TextView textApplySource;
        private final Button buttonApply;

        JobViewHolder(@NonNull View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(R.id.text_job_title);
            textCompany = itemView.findViewById(R.id.text_job_company);
            textLocation = itemView.findViewById(R.id.text_job_location);
            textPostedAt = itemView.findViewById(R.id.text_job_posted_at);
            textMeta = itemView.findViewById(R.id.text_job_meta);
            textApplySource = itemView.findViewById(R.id.text_job_apply_source);
            buttonApply = itemView.findViewById(R.id.button_job_apply);
        }

        void bind(JobRecommendation job) {
            textTitle.setText(valueOrFallback(job == null ? "" : job.title,
                    itemView.getContext().getString(R.string.job_title_fallback)));
            textCompany.setText(valueOrFallback(job == null ? "" : job.company,
                    itemView.getContext().getString(R.string.job_company_fallback)));
            textLocation.setText(valueOrFallback(job == null ? "" : job.location,
                    itemView.getContext().getString(R.string.job_location_fallback)));
            textPostedAt.setText(valueOrFallback(job == null ? "" : job.postedAt,
                    itemView.getContext().getString(R.string.job_posted_fallback)));

            String metaText = joinMeta(job == null ? "" : job.workMode, job == null ? "" : job.employmentType);
            if (blank(metaText)) {
                textMeta.setVisibility(View.GONE);
            } else {
                textMeta.setVisibility(View.VISIBLE);
                textMeta.setText(metaText);
            }

            String applySource = valueOrFallback(job == null ? "" : job.applySource,
                    itemView.getContext().getString(R.string.job_apply_source_fallback));
            textApplySource.setText(itemView.getContext().getString(R.string.job_source_template, applySource));

            String destination = primaryDestination(job);
            buttonApply.setEnabled(!blank(destination));
            buttonApply.setText(buttonLabel(job, destination));
            buttonApply.setOnClickListener(v -> {
                if (blank(destination)) {
                    return;
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(destination));
                itemView.getContext().startActivity(intent);
            });
        }

        private String buttonLabel(JobRecommendation job, String destination) {
            if (blank(destination)) {
                return itemView.getContext().getString(R.string.apply_link_unavailable);
            }
            return blank(job == null ? "" : job.applyLink)
                    ? itemView.getContext().getString(R.string.open_job_page)
                    : itemView.getContext().getString(R.string.apply_now);
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

        private String primaryDestination(JobRecommendation job) {
            if (job == null) {
                return "";
            }
            if (!blank(job.applyLink)) {
                return job.applyLink;
            }
            return valueOrFallback(job.shareLink, "");
        }

        private static String valueOrFallback(String value, String fallback) {
            return blank(value) ? fallback : value;
        }

        private static boolean blank(String value) {
            return value == null || value.trim().isEmpty();
        }
    }
}
