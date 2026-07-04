package com.nextide;

import android.os.Bundle;
import android.view.Menu; 
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.tabs.TabLayout;
import com.nextide.adapter.ProjectAdapter;
import com.nextide.build.BuildManager;
import com.nextide.databinding.ActivityMainBinding;
import com.nextide.dialog.NewProjectDialog;
import com.nextide.dialog.AiSettingsDialog; 
import com.nextide.fragment.BuildLogFragment;
import com.nextide.fragment.EditorFragment;
import com.nextide.fragment.FileExplorerFragment;
import com.nextide.model.BuildResult;
import com.nextide.model.FileNode;
import com.nextide.model.Project;
import com.nextide.util.FileUtils;
import com.nextide.util.AiManager; 
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ActionBarDrawerToggle drawerToggle;

    private List<Project> projects = new ArrayList<>();
    private ProjectAdapter projectAdapter;
    private Project activeProject;

    private FileExplorerFragment fileExplorerFragment;
    private BuildLogFragment buildLogFragment;

    private final List<EditorFragment> editorTabs = new ArrayList<>();

    private final BuildManager buildManager = new BuildManager();
    private BuildResult lastBuildResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        setupDrawer();
        
        setupProjectList();
        setupFab();
        
        showWelcome(); 
    }

    // ── Drawer ────────────────────────────────────────────────────────
    private void setupDrawer() {
        if (binding == null) return;
        drawerToggle = new ActionBarDrawerToggle(this, binding.drawerLayout,
                binding.toolbar, R.string.drawer_open, R.string.drawer_close);
        binding.drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();
    }

    // ── Project list in left nav drawer ──────────────────────────────
    private void setupProjectList() {
        if (binding == null) return;
        
        projects = FileUtils.loadProjects(this);
        if (projects == null) {
            projects = new ArrayList<>();
        }
        
        projectAdapter = new ProjectAdapter(projects);
        projectAdapter.setOnProjectClickListener(new ProjectAdapter.OnProjectClickListener() {
            @Override
            public void onProjectClick(Project project) {
                openProject(project);
                if (binding != null) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START);
                }
            }
            @Override
            public boolean onProjectLongClick(Project project) {
                confirmDeleteProject(project);
                return true;
            }
        });
        binding.navProjectList.setLayoutManager(new LinearLayoutManager(this));
        binding.navProjectList.setAdapter(projectAdapter);
    }

    // ── Editor area (TabLayout + FrameLayout) ─────────────────────────
    private void setupEditor() {
        if (binding == null) return;
        
        if (fileExplorerFragment == null) {
            fileExplorerFragment = FileExplorerFragment.newInstance();
            fileExplorerFragment.setOnFileSelectedListener(this::openFile);
        }
            
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container_file_explorer, fileExplorerFragment)
                .commitAllowingStateLoss();

        binding.tabStrip.clearOnTabSelectedListeners();
        binding.tabStrip.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                switchToTab(tab.getPosition());
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupBuildLog() {
        if (binding == null) return;
        
        if (buildLogFragment == null) {
            buildLogFragment = BuildLogFragment.newInstance();
        }
            
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container_build_log, buildLogFragment)
                .commitAllowingStateLoss();

        binding.btnToggleBuildLog.setOnClickListener(v -> toggleBuildLog());
        binding.btnBuild.setOnClickListener(v -> triggerBuild());
    }

    // ── Click Listeners Centralized Setup ─────────────────────────────
    private void setupFab() {
        if (binding == null) return;
        
        binding.fabNewProject.setOnClickListener(v -> showNewProjectDialog());
        
        binding.btnNavNewProject.setOnClickListener(v -> {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
            showNewProjectDialog();
        });

        if (binding.btnWelcomeNew != null) {
            binding.btnWelcomeNew.setOnClickListener(v -> showNewProjectDialog());
        }

        if (binding.btnWelcomeOpen != null) {
            binding.btnWelcomeOpen.setOnClickListener(v -> {
                binding.drawerLayout.openDrawer(GravityCompat.START);
            });
        }
    }

    public void showNewProjectDialogFromWelcome(View v) {
        showNewProjectDialog();
    }

    public void openDrawerFromWelcome(View v) {
        if (binding != null) {
            binding.drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    // ── Welcome / IDE screen toggle ────────────────────────────────────
    private void showWelcome() {
        if (binding == null) return;
        binding.viewWelcome.setVisibility(View.VISIBLE);
        binding.viewIde.setVisibility(View.GONE);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Next IDE");
            int size = (projects != null) ? projects.size() : 0;
            getSupportActionBar().setSubtitle(size + " project(s)");
        }
    }

    private void showIde() {
        if (binding == null) return;
        binding.viewWelcome.setVisibility(View.GONE);
        binding.viewIde.setVisibility(View.VISIBLE);
    }

    // ── Project operations ─────────────────────────────────────────────
    private void openProject(Project project) {
        if (project == null || binding == null) return;
        
        this.activeProject = project;
        project.setLastModified(System.currentTimeMillis());
        FileUtils.saveProjects(this, projects);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(project.getName());
            getSupportActionBar().setSubtitle(project.getLanguageDisplay());
        }

        showIde();

        setupEditor();
        setupBuildLog();

        editorTabs.clear();
        binding.tabStrip.removeAllTabs();
        clearEditorPane();

        if (fileExplorerFragment != null) {
            fileExplorerFragment.loadProject(project);
        }
        if (buildLogFragment != null) {
            buildLogFragment.clearLog();
        }
    }

    private void showNewProjectDialog() {
        NewProjectDialog dialog = new NewProjectDialog();
        dialog.setOnProjectCreatedListener((name, language) -> {
            File dir = new File(FileUtils.getProjectsRootDir(this), name);
            if (dir.exists()) {
                Toast.makeText(this, "Project already exists", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                FileUtils.scaffoldProject(dir, language);
                Project p = new Project(name, language, dir.getAbsolutePath());
                projects.add(0, p);
                FileUtils.saveProjects(this, projects);
                if (projectAdapter != null) {
                    projectAdapter.notifyItemInserted(0);
                }
                updateWelcomeStats();
                openProject(p);
            } catch (IOException e) {
                Toast.makeText(this, "Failed to create project: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        dialog.show(getSupportFragmentManager(), "new_project");
    }

    private void confirmDeleteProject(Project project) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + project.getName() + "?")
                .setMessage("All files will be permanently deleted.")
                .setPositiveButton("Delete", (d, w) -> {
                    FileUtils.deleteRecursive(project.getDirectory());
                    int idx = projects.indexOf(project);
                    if (idx >= 0) {
                        projects.remove(idx);
                        if (projectAdapter != null) {
                            projectAdapter.notifyItemRemoved(idx);
                        }
                        FileUtils.saveProjects(this, projects);
                    }
                    if (project.equals(activeProject)) showWelcome();
                    updateWelcomeStats();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateWelcomeStats() {
        if (binding == null) return;
        if (getSupportActionBar() != null && binding.viewWelcome.getVisibility() == View.VISIBLE) {
            int size = (projects != null) ? projects.size() : 0;
            getSupportActionBar().setSubtitle(size + " project(s)");
        }
    }

    // ── File / Tab operations ─────────────────────────────────────────
    private void openFile(FileNode node) {
        if (node == null || binding == null) return;
        
        for (int i = 0; i < editorTabs.size(); i++) {
            if (editorTabs.get(i).getFilePath().equals(node.getPath())) {
                binding.tabStrip.selectTab(binding.tabStrip.getTabAt(i));
                return;
            }
        }

        EditorFragment fragment = EditorFragment.newInstance(node);
        editorTabs.add(fragment);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container_editor, fragment)
                .commitAllowingStateLoss();

        TabLayout.Tab tab = binding.tabStrip.newTab();
        tab.setText(node.getName());
        tab.setTag(fragment);
        binding.tabStrip.addTab(tab, true);

        binding.tvEditorPlaceholder.setVisibility(View.GONE);
    }

    private void switchToTab(int position) {
        if (binding == null) return;
        if (position < 0 || position >= editorTabs.size()) return;
        EditorFragment fragment = editorTabs.get(position);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container_editor, fragment)
                .commitAllowingStateLoss();
    }

    private void clearEditorPane() {
        if (binding != null) {
            binding.tvEditorPlaceholder.setVisibility(View.VISIBLE);
        }
    }

    // ── Build & AI Auto-Fix System ────────────────────────────────────
    private void triggerBuild() {
        if (activeProject == null || binding == null) {
            Toast.makeText(this, "No project open", Toast.LENGTH_SHORT).show();
            return;
        }
        for (EditorFragment ef : editorTabs) {
            if (ef.isModified()) ef.saveFile();
        }

        if (buildLogFragment != null) {
            buildLogFragment.clearLog();
        }
        setBuildLogVisible(true);
        binding.btnBuild.setEnabled(false);

        // 🟢 ပြင်ဆင်ချက်: BuildManager တောင်းဆိုထားသည့်အတိုင်း ပထမဆုံး Parameter တွင် MainActivity.this ကို ပြန်လည်ဖြည့်စွက်ပေးလိုက်ပါပြီ
        lastBuildResult = buildManager.triggerBuild(MainActivity.this, activeProject, new BuildManager.BuildListener() {
            @Override
            public void onLogAppended(String line) {
                runOnUiThread(() -> {
                    if (buildLogFragment != null) {
                        buildLogFragment.appendLog(line);
                    }
                });
            }
            @Override
            public void onBuildFinished(BuildResult result) {
                runOnUiThread(() -> {
                    if (buildLogFragment != null) {
                        buildLogFragment.updateStatus(result);
                    }
                    if (binding != null) {
                        binding.btnBuild.setEnabled(true);
                    }
                    
                    if (result != null && result.getStatus() == BuildResult.Status.SUCCESS) {
                        Toast.makeText(MainActivity.this, "Build succeeded!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Build failed. AI is analyzing logs...", Toast.LENGTH_SHORT).show();
                        
                        File mainFile = new File(activeProject.getDirectory(), "src/main/java/com/nextide/app/MainActivity.java");
                        String errorLog = (result != null) ? result.toString() : "Unknown build compile error.";
                        
                        AiManager.requestAutoFix(MainActivity.this, mainFile, errorLog, new AiManager.AiFixListener() {
                            @Override
                            public void onFixSuccess(String fixedCode) {
                                try {
                                    FileUtils.writeFile(mainFile, fixedCode);
                                    
                                    runOnUiThread(() -> {
                                        Toast.makeText(MainActivity.this, "AI Auto-Fixed Successfully! Please Rebuild.", Toast.LENGTH_LONG).show();
                                        if (fileExplorerFragment != null) {
                                            fileExplorerFragment.loadProject(activeProject);
                                        }
                                    });
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onFixFailed(String reason) {
                                runOnUiThread(() -> {
                                    Toast.makeText(MainActivity.this, "AI Fix Error: " + reason, Toast.LENGTH_LONG).show();
                                });
                            }
                        });
                    }
                });
            }
        });
        
        if (buildLogFragment != null && lastBuildResult != null) {
            buildLogFragment.updateStatus(lastBuildResult);
        }
    }

    private void toggleBuildLog() {
        if (binding == null) return;
        View panel = binding.buildLogPanel;
        if (panel.getVisibility() == View.VISIBLE) {
            setBuildLogVisible(false);
        } else {
            setBuildLogVisible(true);
        }
    }

    private void setBuildLogVisible(boolean visible) {
        if (binding == null) return;
        binding.buildLogPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.btnToggleBuildLog.setText(visible ? "▼ Build Log" : "▶ Build Log");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_ai_settings) {
            new AiSettingsDialog().show(getSupportFragmentManager(), "ai_settings");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (binding == null) {
            super.onBackPressed();
            return;
        }
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
        } else if (binding.viewIde.getVisibility() == View.VISIBLE) {
            new AlertDialog.Builder(this)
                    .setMessage("Go back to project list?")
                    .setPositiveButton("Yes", (d, w) -> showWelcome())
                    .setNegativeButton("No", null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }
}
