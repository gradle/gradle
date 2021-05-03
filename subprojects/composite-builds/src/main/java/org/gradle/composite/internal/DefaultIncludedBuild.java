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
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.tasks.TaskReference;
import org.gradle.internal.build.BuildModelControllerServices;
import org.gradle.internal.build.BuildLifecycleControllerFactory;
import org.gradle.initialization.IncludedBuildSpec;
import org.gradle.internal.build.BuildLifecycleController;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.buildtree.BuildTreeController;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.Path;

import java.io.File;
import java.util.function.Consumer;

public class DefaultIncludedBuild extends AbstractCompositeParticipantBuildState implements IncludedBuildState, IncludedBuild, Stoppable {
    private final BuildIdentifier buildIdentifier;
    private final Path identityPath;
    private final BuildDefinition buildDefinition;
    private final boolean isImplicit;
    private final BuildState owner;
    private final WorkerLeaseRegistry.WorkerLease parentLease;

    private final BuildLifecycleController buildLifecycleController;

    public DefaultIncludedBuild(
        BuildIdentifier buildIdentifier,
        Path identityPath,
        BuildDefinition buildDefinition,
        boolean isImplicit,
        BuildState owner,
        BuildTreeController buildTree,
        WorkerLeaseRegistry.WorkerLease parentLease,
        BuildLifecycleControllerFactory buildLifecycleControllerFactory,
        BuildModelControllerServices buildModelControllerServices
    ) {
        this.buildIdentifier = buildIdentifier;
        this.identityPath = identityPath;
        this.buildDefinition = buildDefinition;
        this.isImplicit = isImplicit;
        this.owner = owner;
        this.parentLease = parentLease;
        this.buildLifecycleController = createGradleLauncher(owner, buildTree, buildLifecycleControllerFactory, buildModelControllerServices);
    }

    protected BuildLifecycleController createGradleLauncher(BuildState owner, BuildTreeController buildTree, BuildLifecycleControllerFactory buildLifecycleControllerFactory, BuildModelControllerServices buildModelControllerServices) {
        // Use a defensive copy of the build definition, as it may be mutated during build execution
        BuildScopeServices buildScopeServices = new BuildScopeServices(buildTree.getServices());
        buildModelControllerServices.supplyBuildScopeServices(buildScopeServices);
        return buildLifecycleControllerFactory.newInstance(buildDefinition.newInstance(), this, owner.getMutableModel(), buildScopeServices);
    }

    protected BuildDefinition getBuildDefinition() {
        return buildDefinition;
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
    public boolean isPluginBuild() {
        return buildDefinition.isPluginBuild();
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
    public boolean hasInjectedSettingsPlugins() {
        return !buildDefinition.getInjectedPluginRequests().isEmpty();
    }

    @Override
    public SettingsInternal loadSettings() {
        return buildLifecycleController.getLoadedSettings();
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        return getGradle().getSettings();
    }

    @Override
    public GradleInternal getConfiguredBuild() {
        return buildLifecycleController.getConfiguredBuild();
    }

    @Override
    public GradleInternal getBuild() {
        return getConfiguredBuild();
    }

    @Override
    public <T> T withState(Transformer<T, ? super GradleInternal> action) {
        // This should apply some locking, but most access to the build state does not happen via this method yet
        return action.transform(getGradle());
    }

    @Override
    public void finishBuild(Consumer<? super Throwable> collector) {
        buildLifecycleController.finishBuild(null, collector);
    }

    @Override
    public synchronized void addTasks(Iterable<String> taskPaths) {
        scheduleTasks(taskPaths);
    }

    @Override
    public synchronized void execute(final Iterable<String> tasks, final Object listener) {
        buildLifecycleController.addListener(listener);
        scheduleTasks(tasks);
        WorkerLeaseService workerLeaseService = gradleService(WorkerLeaseService.class);
        workerLeaseService.withSharedLease(
            parentLease,
            buildLifecycleController::executeTasks
        );
    }

    @Override
    public void stop() {
        buildLifecycleController.stop();
    }

    protected void scheduleTasks(Iterable<String> tasks) {
        buildLifecycleController.scheduleTasks(tasks);
    }

    protected GradleInternal getGradle() {
        return buildLifecycleController.getGradle();
    }

    @Override
    public GradleInternal getMutableModel() {
        return buildLifecycleController.getGradle();
    }

    private <T> T gradleService(Class<T> serviceType) {
        return getGradle().getServices().get(serviceType);
    }
}
