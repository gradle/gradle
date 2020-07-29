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
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.DependencySubstitutions;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.initialization.IncludedBuild;
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
import org.gradle.initialization.IncludedBuildSpec;
import org.gradle.initialization.NestedBuildFactory;
import org.gradle.internal.Pair;
import org.gradle.internal.build.AbstractBuildState;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Set;

public class DefaultIncludedBuild extends AbstractBuildState implements IncludedBuildState, IncludedBuild, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIncludedBuild.class);

    private final BuildIdentifier buildIdentifier;
    private final Path identityPath;
    private final BuildDefinition buildDefinition;
    private final boolean isImplicit;
    private final BuildState owner;
    private final WorkerLeaseRegistry.WorkerLease parentLease;

    private final GradleLauncher gradleLauncher;
    private Set<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> availableModules;

    public DefaultIncludedBuild(BuildIdentifier buildIdentifier, Path identityPath, BuildDefinition buildDefinition, boolean isImplicit, BuildState owner, WorkerLeaseRegistry.WorkerLease parentLease) {
        this.buildIdentifier = buildIdentifier;
        this.identityPath = identityPath;
        this.buildDefinition = buildDefinition;
        this.isImplicit = isImplicit;
        this.owner = owner;
        this.parentLease = parentLease;
        // Use a defensive copy of the build definition, as it may be mutated during build execution
        this.gradleLauncher = owner.getNestedBuildFactory().nestedInstance(buildDefinition.newInstance(), this);
    }

    @Override
    public BuildIdentifier getBuildIdentifier() {
        return buildIdentifier;
    }

    @Override
    public File getRootDirectory() {
        return buildDefinition.getBuildRootDir();
    }

    @Override
    public Path getIdentityPath() {
        return identityPath;
    }

    @Override
    public boolean isImplicitBuild() {
        return isImplicit;
    }

    @Override
    public IncludedBuild getModel() {
        return this;
    }

    @Override
    public File getProjectDir() {
        return buildDefinition.getBuildRootDir();
    }

    @Override
    public TaskReference task(String path) {
        Preconditions.checkArgument(path.startsWith(":"), "Task path '%s' is not a qualified task path (e.g. ':task' or ':project:task').", path);
        return new IncludedBuildTaskReference(this, path);
    }

    @Override
    public String getName() {
        return identityPath.getName();
    }

    @Override
    public NestedBuildFactory getNestedBuildFactory() {
        return gradleLauncher.getGradle().getServices().get(NestedBuildFactory.class);
    }

    @Override
    public void assertCanAdd(IncludedBuildSpec includedBuildSpec) {
        if (isImplicit) {
            // Not yet supported for implicit included builds
            super.assertCanAdd(includedBuildSpec);
        }
    }

    @Override
    public File getBuildRootDir() {
        return buildDefinition.getBuildRootDir();
    }

    @Override
    public Path getCurrentPrefixForProjectsInChildBuilds() {
        return owner.getCurrentPrefixForProjectsInChildBuilds().child(buildIdentifier.getName());
    }

    @Override
    public Path getIdentityPathForProject(Path projectPath) {
        return getIdentityPath().append(projectPath);
    }

    @Override
    public Action<? super DependencySubstitutions> getRegisteredDependencySubstitutions() {
        return buildDefinition.getDependencySubstitutions();
    }

    @Override
    public synchronized Set<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> getAvailableModules() {
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
        ProjectComponentIdentifier projectIdentifier = new DefaultProjectComponentIdentifier(buildIdentifier, project.getIdentityPath(), project.getProjectPath(), project.getName());
        DefaultLocalComponentMetadata originalComponent = (DefaultLocalComponentMetadata) localComponentRegistry.getComponent(projectIdentifier);
        ModuleVersionIdentifier moduleId = originalComponent.getModuleVersionId();
        LOGGER.info("Registering " + project + " in composite build. Will substitute for module '" + moduleId.getModule() + "'.");
        availableModules.add(Pair.of(moduleId, projectIdentifier));
    }

    @Override
    public ProjectComponentIdentifier idToReferenceProjectFromAnotherBuild(ProjectComponentIdentifier identifier) {
        // Need to use a 'foreign' build id to make BuildIdentifier.isCurrentBuild and BuildIdentifier.name work in dependency results
        DefaultProjectComponentIdentifier original = (DefaultProjectComponentIdentifier) identifier;
        return new DefaultProjectComponentIdentifier(new ForeignBuildIdentifier(buildIdentifier.getName(), getName()), original.getIdentityPath(), original.projectPath(), original.getProjectName());
    }

    @Override
    public SettingsInternal loadSettings() {
        return gradleLauncher.getLoadedSettings();
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        return gradleLauncher.getGradle().getSettings();
    }

    @Override
    public GradleInternal getConfiguredBuild() {
        return gradleLauncher.getConfiguredBuild();
    }

    @Override
    public <T> T withState(Transformer<T, ? super GradleInternal> action) {
        // This should apply some locking, but most access to the build state does not happen via this method yet
        return action.transform(gradleLauncher.getGradle());
    }

    @Override
    public void finishBuild() {
        gradleLauncher.finishBuild();
    }

    @Override
    public synchronized void addTasks(Iterable<String> taskPaths) {
        gradleLauncher.scheduleTasks(taskPaths);
    }

    @Override
    public synchronized void execute(final Iterable<String> tasks, final Object listener) {
        gradleLauncher.addListener(listener);
        gradleLauncher.scheduleTasks(tasks);
        WorkerLeaseService workerLeaseService = gradleLauncher.getGradle().getServices().get(WorkerLeaseService.class);
        workerLeaseService.withSharedLease(parentLease, new Runnable() {
            @Override
            public void run() {
                gradleLauncher.executeTasks();
            }
        });
    }

    @Override
    public void stop() {
        gradleLauncher.stop();
    }
}
