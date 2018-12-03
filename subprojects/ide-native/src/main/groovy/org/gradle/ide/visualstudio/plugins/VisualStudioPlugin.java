/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.ide.visualstudio.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.ide.visualstudio.VisualStudioExtension;
import org.gradle.ide.visualstudio.VisualStudioProject;
import org.gradle.ide.visualstudio.VisualStudioRootExtension;
import org.gradle.ide.visualstudio.VisualStudioSolution;
import org.gradle.ide.visualstudio.internal.CppApplicationVisualStudioTargetBinary;
import org.gradle.ide.visualstudio.internal.CppSharedLibraryVisualStudioTargetBinary;
import org.gradle.ide.visualstudio.internal.CppStaticLibraryVisualStudioTargetBinary;
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioExtension;
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioProject;
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioRootExtension;
import org.gradle.ide.visualstudio.internal.VisualStudioExtensionInternal;
import org.gradle.ide.visualstudio.internal.VisualStudioProjectInternal;
import org.gradle.ide.visualstudio.internal.VisualStudioSolutionInternal;
import org.gradle.ide.visualstudio.plugins.VisualStudioPluginRules.VisualStudioExtensionRules;
import org.gradle.ide.visualstudio.plugins.VisualStudioPluginRules.VisualStudioPluginProjectRules;
import org.gradle.ide.visualstudio.plugins.VisualStudioPluginRules.VisualStudioPluginRootRules;
import org.gradle.ide.visualstudio.tasks.GenerateFiltersFileTask;
import org.gradle.ide.visualstudio.tasks.GenerateProjectFileTask;
import org.gradle.ide.visualstudio.tasks.GenerateSolutionFileTask;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.language.cpp.CppLibrary;
import org.gradle.language.cpp.CppSharedLibrary;
import org.gradle.language.cpp.CppStaticLibrary;
import org.gradle.plugins.ide.internal.IdeArtifactRegistry;
import org.gradle.plugins.ide.internal.IdePlugin;

import javax.inject.Inject;


/**
 * A plugin for creating a Visual Studio solution for a gradle project.
 */
@Incubating
public class VisualStudioPlugin extends IdePlugin {
    private static final String LIFECYCLE_TASK_NAME = "visualStudio";

    private final Instantiator instantiator;
    private final FileResolver fileResolver;
    private final IdeArtifactRegistry artifactRegistry;
    private CollectionCallbackActionDecorator collectionCallbackActionDecorator;

    @Inject
    public VisualStudioPlugin(Instantiator instantiator, FileResolver fileResolver, IdeArtifactRegistry artifactRegistry, CollectionCallbackActionDecorator collectionCallbackActionDecorator) {
        this.instantiator = instantiator;
        this.fileResolver = fileResolver;
        this.artifactRegistry = artifactRegistry;
        this.collectionCallbackActionDecorator = collectionCallbackActionDecorator;
    }

    @Override
    protected String getLifecycleTaskName() {
        return LIFECYCLE_TASK_NAME;
    }

    @Override
    protected void onApply(final Project target) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);

        // Create Visual Studio project extensions
        final VisualStudioExtensionInternal extension;
        if (isRoot()) {
            extension = (VisualStudioExtensionInternal) project.getExtensions().create(VisualStudioRootExtension.class, "visualStudio", DefaultVisualStudioRootExtension.class, project.getName(), instantiator, target.getObjects(), fileResolver, artifactRegistry, collectionCallbackActionDecorator);
            final VisualStudioSolution solution = ((VisualStudioRootExtension) extension).getSolution();
            getLifecycleTask().configure(new Action<Task>() {
                @Override
                public void execute(Task task) {
                    task.dependsOn(solution);
                }
            });
            addWorkspace(solution);
        } else {
            extension = (VisualStudioExtensionInternal) project.getExtensions().create(VisualStudioExtension.class, "visualStudio", DefaultVisualStudioExtension.class, instantiator, fileResolver, artifactRegistry, collectionCallbackActionDecorator);
            getLifecycleTask().configure(new Action<Task>() {
                @Override
                public void execute(Task task) {
                    task.dependsOn(extension.getProjects());
                }
            });
        }
        includeBuildFileInProject(extension);

        // Create tasks for solutions, projects and filters
        createTasksForVisualStudio(extension);

        // Current Model
        applyVisualStudioCurrentModelRules(extension);

        // SoftwareModel
        applyVisualStudioSoftwareModelRules();
    }

    private void applyVisualStudioCurrentModelRules(final VisualStudioExtensionInternal extension) {
        project.getComponents().withType(CppApplication.class).all(new Action<CppApplication>() {
            @Override
            public void execute(final CppApplication cppApplication) {
                cppApplication.getBinaries().whenElementFinalized(CppExecutable.class, new Action<CppExecutable>() {
                    @Override
                    public void execute(CppExecutable executable) {
                        extension.getProjectRegistry().addProjectConfiguration(new CppApplicationVisualStudioTargetBinary(project.getName(), project.getPath(), cppApplication, executable));
                    }
                });
            }
        });
        project.getComponents().withType(CppLibrary.class).all(new Action<CppLibrary>() {
            @Override
            public void execute(final CppLibrary cppLibrary) {
                cppLibrary.getBinaries().whenElementFinalized(CppSharedLibrary.class, new Action<CppSharedLibrary>() {
                    @Override
                    public void execute(CppSharedLibrary library) {
                        extension.getProjectRegistry().addProjectConfiguration(new CppSharedLibraryVisualStudioTargetBinary(project.getName(), project.getPath(), cppLibrary, library));
                    }
                });
                cppLibrary.getBinaries().whenElementFinalized(CppStaticLibrary.class, new Action<CppStaticLibrary>() {
                    @Override
                    public void execute(CppStaticLibrary library) {
                        extension.getProjectRegistry().addProjectConfiguration(new CppStaticLibraryVisualStudioTargetBinary(project.getName(), project.getPath(), cppLibrary, library));
                    }
                });
            }
        });
    }

    private void applyVisualStudioSoftwareModelRules() {
        project.getPluginManager().apply(VisualStudioExtensionRules.class);

        if (isRoot()) {
            project.getPluginManager().apply(VisualStudioPluginRootRules.class);
        }

        project.getPlugins().withType(ComponentModelBasePlugin.class).all(new Action<ComponentModelBasePlugin>() {
            @Override
            public void execute(ComponentModelBasePlugin componentModelBasePlugin) {
                project.getPluginManager().apply(VisualStudioPluginProjectRules.class);
            }
        });
    }

    private void includeBuildFileInProject(VisualStudioExtensionInternal extension) {
        extension.getProjectRegistry().all(new Action<DefaultVisualStudioProject>() {
            @Override
            public void execute(DefaultVisualStudioProject vsProject) {
                if (project.getBuildFile() != null) {
                    vsProject.addSourceFile(project.getBuildFile());
                }
            }
        });
    }

    private void createTasksForVisualStudio(VisualStudioExtensionInternal extension) {
        extension.getProjectRegistry().all(new Action<DefaultVisualStudioProject>() {
            @Override
            public void execute(DefaultVisualStudioProject vsProject) {
                addTasksForVisualStudioProject(vsProject);
            }
        });

        if (isRoot()) {
            VisualStudioRootExtension rootExtension = (VisualStudioRootExtension) extension;
            VisualStudioSolutionInternal vsSolution = (VisualStudioSolutionInternal) rootExtension.getSolution();

            vsSolution.builtBy(createSolutionTask(vsSolution));
        }

        configureCleanTask();
    }

    private void addTasksForVisualStudioProject(VisualStudioProjectInternal vsProject) {
        vsProject.builtBy(createProjectsFileTask(vsProject), createFiltersFileTask(vsProject));

        Task lifecycleTask = project.getTasks().maybeCreate(vsProject.getComponentName() + "VisualStudio");
        lifecycleTask.dependsOn(vsProject);
    }

    private void configureCleanTask() {
        final TaskProvider<Delete> cleanTask = (TaskProvider<Delete>) getCleanTask();

        cleanTask.configure(new Action<Delete>() {
            @Override
            public void execute(Delete cleanTask) {
                cleanTask.delete(project.getTasks().withType(GenerateSolutionFileTask.class));
                cleanTask.delete(project.getTasks().withType(GenerateFiltersFileTask.class));
                cleanTask.delete(project.getTasks().withType(GenerateProjectFileTask.class));
            }
        });
    }

    private Task createSolutionTask(VisualStudioSolution solution) {
        GenerateSolutionFileTask solutionFileTask = project.getTasks().create(solution.getName() + "VisualStudioSolution", GenerateSolutionFileTask.class);
        solutionFileTask.setVisualStudioSolution(solution);
        return solutionFileTask;
    }

    private Task createProjectsFileTask(VisualStudioProject vsProject) {
        GenerateProjectFileTask task = project.getTasks().create(vsProject.getName() + "VisualStudioProject", GenerateProjectFileTask.class);
        task.setVisualStudioProject(vsProject);
        task.initGradleCommand();
        return task;
    }

    private Task createFiltersFileTask(VisualStudioProject vsProject) {
        GenerateFiltersFileTask task = project.getTasks().create(vsProject.getName() + "VisualStudioFilters", GenerateFiltersFileTask.class);
        task.setVisualStudioProject(vsProject);
        return task;
    }
}
