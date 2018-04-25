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
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.initialization.ConfigurableIncludedBuild;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ForeignBuildIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.TaskReference;
import org.gradle.initialization.GradleLauncher;
import org.gradle.initialization.NestedBuildFactory;
import org.gradle.internal.Pair;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Set;

import static org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier.newProjectId;

public class DefaultIncludedBuild implements IncludedBuildState, ConfigurableIncludedBuild, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIncludedBuild.class);

    private final BuildIdentifier buildIdentifier;
    private final BuildDefinition buildDefinition;
    private final boolean isImplicit;
    private final NestedBuildFactory gradleLauncherFactory;
    private final WorkerLeaseRegistry.WorkerLease parentLease;
    private final List<Action<? super DependencySubstitutions>> dependencySubstitutionActions = Lists.newArrayList();

    private boolean resolvedDependencySubstitutions;

    private GradleLauncher gradleLauncher;
    private boolean discardLauncher;
    private String name;
    private Set<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> availableModules;

    public DefaultIncludedBuild(BuildIdentifier buildIdentifier, BuildDefinition buildDefinition, boolean isImplicit, NestedBuildFactory launcherFactory, WorkerLeaseRegistry.WorkerLease parentLease) {
        this.buildIdentifier = buildIdentifier;
        this.buildDefinition = buildDefinition;
        this.isImplicit = isImplicit;
        this.gradleLauncherFactory = launcherFactory;
        this.parentLease = parentLease;
    }

    @Override
    public boolean isImplicitBuild() {
        return isImplicit;
    }

    @Override
    public ConfigurableIncludedBuild getModel() {
        return this;
    }

    @Override
    public File getProjectDir() {
        return buildDefinition.getBuildRootDir();
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
    public BuildIdentifier getBuildIdentifier() {
        return buildIdentifier;
    }

    @Override
    public Path getIdentityPathForProject(Path projectPath) {
        GradleInternal parentBuild = getLoadedSettings().getGradle().getParent();
        Path rootPath;
        if (parentBuild == null) {
            rootPath = Path.ROOT.child(getName());
        } else {
            rootPath = parentBuild.getIdentityPath().child(getName());
        }
        return rootPath.append(projectPath);
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
        if (availableModules == null) {
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
        ProjectComponentIdentifier componentIdentifier = idForProjectInThisBuild(project.getPath());
        ModuleVersionIdentifier moduleId = originalComponent.getModuleVersionId();
        LOGGER.info("Registering " + project + " in composite build. Will substitute for module '" + moduleId.getModule() + "'.");
        availableModules.add(Pair.of(moduleId, componentIdentifier));
    }

    public ProjectComponentIdentifier idForProjectInThisBuild(String projectPath) {
        // Need to use a 'foreign' build id to make BuildIdentifier.isCurrentBuild work in the root build
        return new DefaultProjectComponentIdentifier(new ForeignBuildIdentifier(getName()), projectPath);
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
        if (gradleLauncher == null || discardLauncher) {
            return;
        }
        gradleLauncher.finishBuild();
    }

    public synchronized void addTasks(Iterable<String> taskPaths) {
        getGradleLauncher().scheduleTasks(taskPaths);
    }

    private GradleLauncher getGradleLauncher() {
        if (gradleLauncher == null) {
            gradleLauncher = gradleLauncherFactory.nestedInstance(buildDefinition.newInstance());
        }
        return gradleLauncher;
    }

    @Override
    public synchronized void execute(final Iterable<String> tasks, final Object listener) {
        cleanupLauncherIfRequired();

        final GradleLauncher launcher = getGradleLauncher();
        launcher.addListener(listener);
        launcher.scheduleTasks(tasks);
        WorkerLeaseService workerLeaseService = launcher.getGradle().getServices().get(WorkerLeaseService.class);
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

    private void cleanupLauncherIfRequired() {
        if (gradleLauncher != null && discardLauncher) {
            // Have already used the launcher to run tasks, need to replace it
            try {
                gradleLauncher.stop();
            } finally {
                gradleLauncher = null;
                discardLauncher = false;
            }
        }
    }

    private void markAsNotReusable() {
        // Hang on to the launcher, as other builds in progress may still have references to this build, for example through dependency resolution, even though the tasks of this build have completed
        discardLauncher = true;
    }

    @Override
    public String toString() {
        return String.format("includedBuild[%s]", getProjectDir());
    }

    @Override
    public void stop() {
        try {
            if (gradleLauncher != null) {
                gradleLauncher.stop();
            }
        } finally {
            gradleLauncher = null;
            discardLauncher = false;
        }
    }
}
