package ds.edu.cmu;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
        private final TextView textReason;
        private final TextView textApplyLink;

        JobViewHolder(@NonNull View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(R.id.text_job_title);
            textCompany = itemView.findViewById(R.id.text_job_company);
            textLocation = itemView.findViewById(R.id.text_job_location);
            textPostedAt = itemView.findViewById(R.id.text_job_posted_at);
            textReason = itemView.findViewById(R.id.text_job_reason);
            textApplyLink = itemView.findViewById(R.id.text_job_apply_link);
        }

        void bind(JobRecommendation job) {
            textTitle.setText(job == null || blank(job.title) ? "Untitled role" : job.title);
            textCompany.setText(label("Company", valueOrFallback(job == null ? "" : job.company, "Unknown")));
            textLocation.setText(label("Location", valueOrFallback(job == null ? "" : job.location, "Unknown")));
            textPostedAt.setText(label("Posted", valueOrFallback(job == null ? "" : job.postedAt, "Unknown")));
            textReason.setText(label("Why it matched", valueOrFallback(job == null ? "" : job.reason, "Matched your search")));
            textApplyLink.setText(label("Apply link", valueOrFallback(job == null ? "" : job.applyLink, "Not provided")));
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

