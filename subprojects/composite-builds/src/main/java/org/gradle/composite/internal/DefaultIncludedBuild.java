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
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.DependencySubstitutions;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.tasks.TaskReference;
import org.gradle.initialization.DefaultGradleLauncher;
import org.gradle.initialization.GradleLauncher;
import org.gradle.initialization.IncludedBuildSpec;
import org.gradle.initialization.NestedBuildFactory;
import org.gradle.internal.Pair;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.Path;

import java.io.File;
import java.util.Set;

public class DefaultIncludedBuild extends AbstractCompositeParticipantBuildState implements IncludedBuildState, IncludedBuild, Stoppable {
    private final BuildIdentifier buildIdentifier;
    private final Path identityPath;
    private final BuildDefinition buildDefinition;
    private final boolean isImplicit;
    private final BuildState owner;
    private final WorkerLeaseRegistry.WorkerLease parentLease;

    private final GradleLauncher gradleLauncher;
    private Set<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> availableModules;
    private boolean configuredByCache;

    public DefaultIncludedBuild(
        BuildIdentifier buildIdentifier,
        Path identityPath,
        BuildDefinition buildDefinition,
        boolean isImplicit,
        BuildState owner,
        WorkerLeaseRegistry.WorkerLease parentLease
    ) {
        this.buildIdentifier = buildIdentifier;
        this.identityPath = identityPath;
        this.buildDefinition = buildDefinition;
        this.isImplicit = isImplicit;
        this.owner = owner;
        this.parentLease = parentLease;
        // Use a defensive copy of the build definition, as it may be mutated during build execution
        this.gradleLauncher = owner.getNestedBuildFactory().nestedInstance(buildDefinition.newInstance(), this);
    }

    public void setConfiguredByCache() {
        configuredByCache = true;
        ((DefaultGradleLauncher) gradleLauncher).setConfiguredByCache();
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
        return gradleService(NestedBuildFactory.class);
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
    public SettingsInternal loadSettings() {
        return gradleLauncher.getLoadedSettings();
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        return gradle().getSettings();
    }

    @Override
    public GradleInternal getConfiguredBuild() {
        return gradleLauncher.getConfiguredBuild();
    }

    @Override
    public GradleInternal getBuild() {
        return getConfiguredBuild();
    }

    @Override
    public <T> T withState(Transformer<T, ? super GradleInternal> action) {
        // This should apply some locking, but most access to the build state does not happen via this method yet
        return action.transform(gradle());
    }

    @Override
    public void finishBuild() {
        gradleLauncher.finishBuild();
    }

    @Override
    public synchronized void addTasks(Iterable<String> taskPaths) {
        scheduleTasks(taskPaths);
    }

    @Override
    public synchronized void execute(final Iterable<String> tasks, final Object listener) {
        gradleLauncher.addListener(listener);
        scheduleTasks(tasks);
        WorkerLeaseService workerLeaseService = gradleService(WorkerLeaseService.class);
        workerLeaseService.withSharedLease(
            parentLease,
            gradleLauncher::executeTasks
        );
    }

    @Override
    public void stop() {
        gradleLauncher.stop();
    }

    private void scheduleTasks(Iterable<String> taskPaths) {
        if (configuredByCache) {
            return;
        }
        gradleLauncher.scheduleTasks(taskPaths);
    }

    private <T> T gradleService(Class<T> serviceType) {
        return gradle().getServices().get(serviceType);
    }

    private GradleInternal gradle() {
        return gradleLauncher.getGradle();
    }
}
