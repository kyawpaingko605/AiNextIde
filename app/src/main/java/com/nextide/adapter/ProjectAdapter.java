package com.nextide.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.nextide.R;
import com.nextide.model.Project;
import java.util.List;

public class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.ViewHolder> {
    public interface OnProjectClickListener {
        void onProjectClick(Project project);
        boolean onProjectLongClick(Project project);
    }

    private final List<Project> projects;
    private OnProjectClickListener listener;

    public ProjectAdapter(List<Project> projects) { this.projects = projects; }

    public void setOnProjectClickListener(OnProjectClickListener l) { this.listener = l; }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_project, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Project p = projects.get(position);
        holder.bind(p);
        holder.itemView.setOnClickListener(v -> { if (listener != null) listener.onProjectClick(p); });
        holder.itemView.setOnLongClickListener(v -> listener != null && listener.onProjectLongClick(p));
    }

    @Override public int getItemCount() { return projects.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView name, language, stats, date;

        ViewHolder(View v) {
            super(v);
            name = v.findViewById(R.id.project_name);
            language = v.findViewById(R.id.project_language);
            stats = v.findViewById(R.id.project_stats);
            date = v.findViewById(R.id.project_date);
        }

        void bind(Project p) {
            name.setText(p.getName());
            language.setText(p.getLanguageDisplay());
            stats.setText(p.getFileCount() + " files");
            date.setText(p.getFormattedDate());
        }
    }
}
