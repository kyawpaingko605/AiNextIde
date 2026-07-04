package com.nextide.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.*;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.nextide.R;
import com.nextide.databinding.FragmentBuildLogBinding;
import com.nextide.model.BuildResult;

public class BuildLogFragment extends Fragment {
    private FragmentBuildLogBinding binding;

    public static BuildLogFragment newInstance() { return new BuildLogFragment(); }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBuildLogBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.tvBuildLog.setMovementMethod(new ScrollingMovementMethod());
        binding.tvBuildLog.setHorizontallyScrolling(true);
        binding.btnClear.setOnClickListener(v -> clearLog());
        
        // Copy Button Listener
        binding.btnCopyLog.setOnClickListener(v -> {
            String logText = binding.tvBuildLog.getText().toString().trim();
            if (!logText.isEmpty()) {
                Context context = getContext();
                if (context != null) {
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Build Logs", logText);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(context, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(getContext(), "No logs to copy", Toast.LENGTH_SHORT).show();
            }
        });

        updateStatus(null);
    }

    public void appendLog(String text) {
        if (binding == null || !isAdded()) return;
        binding.tvBuildLog.append(text);
        
        if (binding.tvBuildLog.getLayout() != null) {
            int scrollAmount = binding.tvBuildLog.getLayout().getLineTop(binding.tvBuildLog.getLineCount()) 
                    - binding.tvBuildLog.getHeight();
            if (scrollAmount > 0) {
                binding.tvBuildLog.scrollTo(0, scrollAmount);
            }
        }
    }

    public void clearLog() {
        if (binding == null) return;
        binding.tvBuildLog.setText("");
        updateStatus(null);
    }

    public void updateStatus(BuildResult result) {
        if (binding == null || !isAdded()) return;
        
        Context context = getContext();
        if (context == null) return;

        if (result == null) {
            binding.tvStatus.setText("IDLE");
            binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.status_idle));
            return;
        }
        
        switch (result.getStatus()) {
            case RUNNING:
                binding.tvStatus.setText("BUILDING…");
                binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.status_running));
                break;
            case SUCCESS:
                binding.tvStatus.setText("SUCCESS");
                binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.status_success));
                break;
            case FAILED:
                binding.tvStatus.setText("FAILED");
                binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.status_failed));
                break;
        }
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
