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
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.tasks.TaskReference;
import org.gradle.initialization.IncludedBuildSpec;
import org.gradle.internal.build.BuildLifecycleController;
import org.gradle.internal.build.BuildLifecycleControllerFactory;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildWorkGraph;
import org.gradle.internal.build.DefaultBuildWorkGraph;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.buildtree.BuildTreeState;
import org.gradle.internal.composite.IncludedBuildInternal;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.util.Path;

import java.io.File;

public class DefaultIncludedBuild extends AbstractCompositeParticipantBuildState implements IncludedBuildState, Stoppable {
    private final BuildIdentifier buildIdentifier;
    private final Path identityPath;
    private final BuildDefinition buildDefinition;
    private final boolean isImplicit;
    private final BuildState owner;
    private final ProjectStateRegistry projectStateRegistry;

    private final BuildLifecycleController buildLifecycleController;
    private final IncludedBuildImpl model;
    private final DefaultBuildWorkGraph workGraph;

    public DefaultIncludedBuild(
        BuildIdentifier buildIdentifier,
        Path identityPath,
        BuildDefinition buildDefinition,
        boolean isImplicit,
        BuildState owner,
        BuildTreeState buildTree,
        BuildLifecycleControllerFactory buildLifecycleControllerFactory,
        ProjectStateRegistry projectStateRegistry,
        Instantiator instantiator
    ) {
        this.buildIdentifier = buildIdentifier;
        this.identityPath = identityPath;
        this.buildDefinition = buildDefinition;
        this.isImplicit = isImplicit;
        this.owner = owner;
        this.projectStateRegistry = projectStateRegistry;
        BuildScopeServices buildScopeServices = new BuildScopeServices(buildTree.getServices());
        // Use a defensive copy of the build definition, as it may be mutated during build execution
        this.buildLifecycleController = buildLifecycleControllerFactory.newInstance(buildDefinition.newInstance(), this, owner, buildScopeServices);
        this.workGraph = new DefaultBuildWorkGraph(buildLifecycleController.getGradle().getTaskGraph(), projectStateRegistry, buildLifecycleController);
        this.model = instantiator.newInstance(IncludedBuildImpl.class, this);
    }

    @Override
    protected BuildLifecycleController getBuildController() {
        return buildLifecycleController;
    }

    @Override
    protected ProjectStateRegistry getProjectStateRegistry() {
        return projectStateRegistry;
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
    public boolean isImportableBuild() {
        return !isImplicit;
    }

    @Override
    public IncludedBuildInternal getModel() {
        return model;
    }

    @Override
    public boolean isPluginBuild() {
        return buildDefinition.isPluginBuild();
    }

    File getProjectDir() {
        return buildDefinition.getBuildRootDir();
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
    public Path calculateIdentityPathForProject(Path projectPath) {
        return getIdentityPath().append(projectPath);
    }

    @Override
    public Action<? super DependencySubstitutions> getRegisteredDependencySubstitutions() {
        return buildDefinition.getDependencySubstitutions();
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
    public ExecutionResult<Void> finishBuild() {
        return buildLifecycleController.finishBuild(null);
    }

    @Override
    public BuildWorkGraph getWorkGraph() {
        return workGraph;
    }

    @Override
    public void stop() {
        buildLifecycleController.stop();
    }

    protected GradleInternal getGradle() {
        return buildLifecycleController.getGradle();
    }

    @Override
    public GradleInternal getMutableModel() {
        return buildLifecycleController.getGradle();
    }

    public static class IncludedBuildImpl implements IncludedBuildInternal {
        private final DefaultIncludedBuild buildState;

        public IncludedBuildImpl(DefaultIncludedBuild buildState) {
            this.buildState = buildState;
        }

        @Override
        public String getName() {
            return buildState.getName();
        }

        @Override
        public File getProjectDir() {
            return buildState.getProjectDir();
        }

        @Override
        public TaskReference task(String path) {
            Preconditions.checkArgument(path.startsWith(":"), "Task path '%s' is not a qualified task path (e.g. ':task' or ':project:task').", path);
            return new IncludedBuildTaskReference(buildState, path);
        }

        @Override
        public BuildState getTarget() {
            return buildState;
        }
    }
}
