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
import org.gradle.ide.visualstudio.internal.VisualStudioTargetBinary;
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
import org.gradle.nativeplatform.Linkage;
import org.gradle.plugins.ide.internal.IdeArtifactRegistry;
import org.gradle.plugins.ide.internal.IdePlugin;

import javax.inject.Inject;


/**
 * A plugin for creating a Visual Studio solution for a gradle project.
 */
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
            extension = (VisualStudioExtensionInternal) project.getExtensions().create(VisualStudioRootExtension.class, "visualStudio", DefaultVisualStudioRootExtension.class, project.getName(), instantiator, target.getObjects(), fileResolver, artifactRegistry, collectionCallbackActionDecorator, project.getProviders());
            final VisualStudioSolution solution = ((VisualStudioRootExtension) extension).getSolution();
            getLifecycleTask().configure(it -> it.dependsOn(solution));
            addWorkspace(solution);
        } else {
            extension = (VisualStudioExtensionInternal) project.getExtensions().create(VisualStudioExtension.class, "visualStudio", DefaultVisualStudioExtension.class, instantiator, fileResolver, artifactRegistry, collectionCallbackActionDecorator, project.getProviders());
            getLifecycleTask().configure(it -> it.dependsOn(extension.getProjects()));
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
        project.getComponents().withType(CppApplication.class).all(cppApplication -> {
            DefaultVisualStudioProject vsProject = extension.getProjectRegistry().createProject(project.getName(), cppApplication.getName());
            vsProject.getSourceFiles().from(cppApplication.getCppSource());
            vsProject.getHeaderFiles().from(cppApplication.getHeaderFiles());
            cppApplication.getBinaries().whenElementFinalized(CppExecutable.class, executable -> {
                extension.getProjectRegistry().addProjectConfiguration(new CppApplicationVisualStudioTargetBinary(project.getName(), project.getPath(), cppApplication, executable, project.getLayout()));
            });
        });
        project.afterEvaluate(proj -> {
            project.getComponents().withType(CppLibrary.class).all(cppLibrary -> {
                for (Linkage linkage : cppLibrary.getLinkage().get()) {
                    VisualStudioTargetBinary.ProjectType projectType = VisualStudioTargetBinary.ProjectType.DLL;
                    if (Linkage.STATIC.equals(linkage)) {
                        projectType = VisualStudioTargetBinary.ProjectType.LIB;
                    }
                    DefaultVisualStudioProject vsProject = extension.getProjectRegistry().createProject(project.getName() + projectType.getSuffix(), cppLibrary.getName());
                    vsProject.getSourceFiles().from(cppLibrary.getCppSource());
                    vsProject.getHeaderFiles().from(cppLibrary.getHeaderFiles());
                }
                cppLibrary.getBinaries().whenElementFinalized(CppSharedLibrary.class, library -> {
                    extension.getProjectRegistry().addProjectConfiguration(new CppSharedLibraryVisualStudioTargetBinary(project.getName(), project.getPath(), cppLibrary, library, project.getLayout()));
                });
                cppLibrary.getBinaries().whenElementFinalized(CppStaticLibrary.class, library -> {
                    extension.getProjectRegistry().addProjectConfiguration(new CppStaticLibraryVisualStudioTargetBinary(project.getName(), project.getPath(), cppLibrary, library, project.getLayout()));
                });
            });
        });
    }

    private void applyVisualStudioSoftwareModelRules() {
        project.getPluginManager().apply(VisualStudioExtensionRules.class);

        if (isRoot()) {
            project.getPluginManager().apply(VisualStudioPluginRootRules.class);
        }

        project.getPlugins().withType(ComponentModelBasePlugin.class).all(it -> project.getPluginManager().apply(VisualStudioPluginProjectRules.class));
    }

    private void includeBuildFileInProject(VisualStudioExtensionInternal extension) {
        extension.getProjectRegistry().all(vsProject -> {
            if (project.getBuildFile() != null) {
                vsProject.addSourceFile(project.getBuildFile());
            }
        });
    }

    private void createTasksForVisualStudio(VisualStudioExtensionInternal extension) {
        extension.getProjectRegistry().all(vsProject -> addTasksForVisualStudioProject(vsProject));

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

        cleanTask.configure(it -> {
            it.delete(project.getTasks().withType(GenerateSolutionFileTask.class));
            it.delete(project.getTasks().withType(GenerateFiltersFileTask.class));
            it.delete(project.getTasks().withType(GenerateProjectFileTask.class));
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
