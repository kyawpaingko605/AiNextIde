package com.nextide.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.nextide.R;
import com.nextide.model.FileNode;
import java.util.ArrayList;
import java.util.List;

public class FileTreeAdapter extends RecyclerView.Adapter<FileTreeAdapter.ViewHolder> {
    public interface OnFileClickListener {
        void onFileClick(FileNode node);
        boolean onFileLongClick(FileNode node);
    }

    private final List<FileNode> flatList = new ArrayList<>();
    private final FileNode root;
    private OnFileClickListener listener;

    public FileTreeAdapter(FileNode root) {
        this.root = root;
        root.loadChildren();
        buildFlatList(root.getChildren());
    }

    public void setOnFileClickListener(OnFileClickListener l) { this.listener = l; }

    private void buildFlatList(List<FileNode> nodes) {
        for (FileNode node : nodes) {
            flatList.add(node);
            if (node.isDirectory() && node.isExpanded()) {
                buildFlatList(node.getChildren());
            }
        }
    }

    private void rebuildFlatList() {
        flatList.clear();
        buildFlatList(root.getChildren());
        notifyDataSetChanged();
    }

    public void refresh() {
        root.loadChildren();
        rebuildFlatList();
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileNode node = flatList.get(position);
        holder.bind(node);
        holder.itemView.setOnClickListener(v -> {
            if (node.isDirectory()) {
                if (!node.isExpanded()) node.loadChildren();
                node.setExpanded(!node.isExpanded());
                rebuildFlatList();
            } else {
                if (listener != null) listener.onFileClick(node);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) return listener.onFileLongClick(node);
            return false;
        });
    }

    @Override public int getItemCount() { return flatList.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView name;
        private final ImageView arrow;

        ViewHolder(View v) {
            super(v);
            icon = v.findViewById(R.id.file_icon);
            name = v.findViewById(R.id.file_name);
            arrow = v.findViewById(R.id.file_arrow);
        }

        void bind(FileNode node) {
            name.setText(node.getName());
            
            // 📂 Tree Indentation (ဘယ်ဘက်ကနေ ဖိုဒါအဆင့်အလိုက် ဝင်သွားအောင် နေရာချခြင်း)
            // သင့်ရဲ့ layout_marginStart သို့မဟုတ် padding နဲ့ ကိုက်ညီအောင် icon ကို တွန်းပေးပါမည်
            itemView.setPaddingRelative(node.getDepth() * 32, 0, 0, 0);

            if (node.isDirectory()) {
                // 📁 ကွန်ပျူတာစတိုင် ဝါရွှေရောင် Folder အရောင်ခွဲခြင်း
                icon.setImageResource(R.drawable.ic_folder);
                icon.setColorFilter(Color.parseColor("#FFCA28")); // Golden Yellow
                
                // 📂 မြှားလေးကို ဖော်ပြပြီး ဖွင့်/ပိတ်အလိုက် လှည့်ပေးခြင်း
                arrow.setVisibility(View.VISIBLE);
                arrow.setRotation(node.isExpanded() ? 90f : 0f);
                arrow.setColorFilter(Color.parseColor("#90A4AE")); // Slate Gray
            } else {
                // 📄 ဖိုင်အမျိုးအစားအလိုက် အရောင်ခွဲခြားခြင်း
                icon.setImageResource(getFileIcon(node.getLanguage()));
                icon.setColorFilter(getFileColor(node.getLanguage()));
                
                // ဖိုင်ဖြစ်၍ မြှားလေးကို ဖျောက်ထားမည်
                arrow.setVisibility(View.GONE);
            }
        }

        private int getFileIcon(String lang) {
            if (lang == null) return R.drawable.ic_file_text;
            switch (lang.toLowerCase()) {
                case "java": case "kotlin": 
                case "python": return R.drawable.ic_file_code;
                case "xml": return R.drawable.ic_file_xml;
                case "json": return R.drawable.ic_file_json;
                default: return R.drawable.ic_file_text;
            }
        }

        // 🎨 ဖိုင် extension အလိုက် ကွန်ပျူတာလို ခွဲခြားပေးမည့် သီးသန့်အရောင် Palette
        private int getFileColor(String lang) {
            if (lang == null) return Color.parseColor("#90A4AE");
            switch (lang.toLowerCase()) {
                case "java": 
                    return Color.parseColor("#F44336"); // Java Red
                case "kotlin": 
                    return Color.parseColor("#7F52FF"); // Kotlin Purple
                case "xml": 
                    return Color.parseColor("#FF9800"); // XML Orange
                case "json": 
                    return Color.parseColor("#4CAF50"); // JSON Green
                case "python": 
                    return Color.parseColor("#0288D1"); // Python Blue
                default: 
                    return Color.parseColor("#90A4AE"); // Default Text File Gray
            }
        }
    }
}
