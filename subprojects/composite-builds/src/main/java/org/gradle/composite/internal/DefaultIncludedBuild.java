/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.composite.internal;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencySubstitutions;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.TaskReference;
import org.gradle.initialization.GradleLauncher;
import org.gradle.internal.Factory;
import org.gradle.internal.Pair;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.internal.work.WorkerLeaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Set;

import static org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier.newProjectId;

public class DefaultIncludedBuild implements IncludedBuildInternal, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIncludedBuild.class);

    private final File projectDir;
    private final Factory<GradleLauncher> gradleLauncherFactory;
    private final WorkerLeaseRegistry.WorkerLease parentLease;
    private final List<Action<? super DependencySubstitutions>> dependencySubstitutionActions = Lists.newArrayList();

    private boolean resolvedDependencySubstitutions;

    private GradleLauncher gradleLauncher;
    private String name;
    private Set<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> availableModules;

    public DefaultIncludedBuild(File projectDir, Factory<GradleLauncher> launcherFactory, WorkerLeaseRegistry.WorkerLease parentLease) {
        this.projectDir = projectDir;
        this.gradleLauncherFactory = launcherFactory;
        this.parentLease = parentLease;
    }

    public File getProjectDir() {
        return projectDir;
    }

    @Override
    public TaskReference task(String path) {
        Preconditions.checkArgument(path.startsWith(":"), "Task path '%s' is not a qualified task path (e.g. ':task' or ':project:task').", path);
        return new IncludedBuildTaskReference(getName(), path);
    }

    @Override
    public String getName() {
        if (name == null) {
            name = getLoadedSettings().getRootProject().getName();
        }
        return name;
    }

    @Override
    public void dependencySubstitution(Action<? super DependencySubstitutions> action) {
        if (resolvedDependencySubstitutions) {
            throw new IllegalStateException("Cannot configure included build after dependency substitutions are resolved.");
        }
        dependencySubstitutionActions.add(action);
    }

    @Override
    public List<Action<? super DependencySubstitutions>> getRegisteredDependencySubstitutions() {
        resolvedDependencySubstitutions = true;
        return dependencySubstitutionActions;
    }

    @Override
    public Set<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> getAvailableModules() {
        // TODO: Synchronization
        if (availableModules==null) {
            Gradle gradle = getConfiguredBuild();
            availableModules = Sets.newLinkedHashSet();
            for (Project project : gradle.getRootProject().getAllprojects()) {
                registerProject(availableModules, (ProjectInternal) project);
            }
        }
        return availableModules;
    }

    private void registerProject(Set<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> availableModules, ProjectInternal project) {
        LocalComponentRegistry localComponentRegistry = project.getServices().get(LocalComponentRegistry.class);
        ProjectComponentIdentifier originalIdentifier = newProjectId(project);
        DefaultLocalComponentMetadata originalComponent = (DefaultLocalComponentMetadata) localComponentRegistry.getComponent(originalIdentifier);
        ProjectComponentIdentifier componentIdentifier = newProjectId(this, project.getPath());
        ModuleVersionIdentifier moduleId = originalComponent.getId();
        LOGGER.info("Registering " + project + " in composite build. Will substitute for module '" + moduleId.getModule() + "'.");
        availableModules.add(Pair.of(moduleId, componentIdentifier));
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        return getGradleLauncher().getLoadedSettings();
    }

    @Override
    public GradleInternal getConfiguredBuild() {
        return getGradleLauncher().getConfiguredBuild();
    }

    @Override
    public void finishBuild() {
        // If the gradleLauncher is null, then we've already finished building.
        if (gradleLauncher == null) {
            return;
        }
        getGradleLauncher().finishBuild();
    }

    public synchronized void addTasks(Iterable<String> taskPaths) {
        getGradleLauncher().scheduleTasks(taskPaths);
    }

    private GradleLauncher getGradleLauncher() {
        if (gradleLauncher == null) {
            gradleLauncher = gradleLauncherFactory.create();
        }
        return gradleLauncher;
    }

    @Override
    public synchronized void execute(final Iterable<String> tasks, final Object listener) {
        final GradleLauncher launcher = getGradleLauncher();
        launcher.addListener(listener);
        launcher.scheduleTasks(tasks);
        WorkerLeaseService workerLeaseService = gradleLauncher.getGradle().getServices().get(WorkerLeaseService.class);
        try {
            workerLeaseService.withSharedLease(parentLease, new Runnable() {
                @Override
                public void run() {
                    launcher.executeTasks();
                }
            });
        } finally {
            markAsNotReusable();
        }
    }

    private void markAsNotReusable() {
        gradleLauncher.stop();
        gradleLauncher = null;
    }

    @Override
    public String toString() {
        return String.format("includedBuild[%s]", projectDir.getName());
    }

    @Override
    public void stop() {
        if (gradleLauncher!=null) {
            gradleLauncher.stop();
        }
    }
}
