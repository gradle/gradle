/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.initialization.IncludedBuildSpec;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.internal.build.AbstractBuildState;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.build.StandAloneNestedBuild;
import org.gradle.internal.buildtree.BuildTreeFinishExecutor;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.internal.buildtree.BuildTreeLifecycleControllerFactory;
import org.gradle.internal.buildtree.BuildTreeState;
import org.gradle.internal.buildtree.BuildTreeWorkExecutor;
import org.gradle.internal.buildtree.DefaultBuildTreeWorkExecutor;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.function.Function;

class DefaultNestedBuild extends AbstractBuildState implements StandAloneNestedBuild {
    private final Path identityPath;
    private final BuildState owner;
    private final BuildIdentifier buildIdentifier;
    private final BuildDefinition buildDefinition;
    private final BuildTreeLifecycleController buildTreeLifecycleController;

    DefaultNestedBuild(
        BuildIdentifier buildIdentifier,
        Path identityPath,
        BuildDefinition buildDefinition,
        BuildState owner,
        BuildTreeState buildTree
    ) {
        super(buildTree, buildDefinition, owner);
        this.buildIdentifier = buildIdentifier;
        this.identityPath = identityPath;
        this.buildDefinition = buildDefinition;
        this.owner = owner;

        BuildScopeServices buildScopeServices = getBuildServices();
        ExceptionAnalyser exceptionAnalyser = buildScopeServices.get(ExceptionAnalyser.class);
        BuildTreeWorkExecutor workExecutor = new DefaultBuildTreeWorkExecutor();
        BuildTreeLifecycleControllerFactory buildTreeLifecycleControllerFactory = buildScopeServices.get(BuildTreeLifecycleControllerFactory.class);

        // On completion of the action, do not finish this build. The root build will take care of finishing this build later
        BuildTreeFinishExecutor finishExecutor = new DoNothingBuildFinishExecutor(exceptionAnalyser);
        buildTreeLifecycleController = buildTreeLifecycleControllerFactory.createController(getBuildController(), workExecutor, finishExecutor);
    }

    @Override
    public BuildIdentifier getBuildIdentifier() {
        return buildIdentifier;
    }

    @Override
    public BuildDefinition getBuildDefinition() {
        return buildDefinition;
    }

    @Override
    public Path getIdentityPath() {
        return identityPath;
    }

    @Override
    public boolean isImplicitBuild() {
        return true;
    }

    @Override
    public BuildState getOwner() {
        return owner;
    }

    @Override
    public ExecutionResult<Void> finishBuild() {
        return getBuildController().finishBuild(null);
    }

    @Override
    public <T> T run(Function<? super BuildTreeLifecycleController, T> buildAction) {
        return buildAction.apply(buildTreeLifecycleController);
    }

    @Override
    public Path getCurrentPrefixForProjectsInChildBuilds() {
        return owner.getCurrentPrefixForProjectsInChildBuilds().child(buildDefinition.getName());
    }

    @Override
    public Path calculateIdentityPathForProject(Path projectPath) {
        return getBuildController().getGradle().getIdentityPath().append(projectPath);
    }

    @Override
    public File getBuildRootDir() {
        return buildDefinition.getBuildRootDir();
    }

    @Override
    public void assertCanAdd(IncludedBuildSpec includedBuildSpec) {
    }

    private static class DoNothingBuildFinishExecutor implements BuildTreeFinishExecutor {
        private final ExceptionAnalyser exceptionAnalyser;

        public DoNothingBuildFinishExecutor(ExceptionAnalyser exceptionAnalyser) {
            this.exceptionAnalyser = exceptionAnalyser;
        }

        @Override
        @Nullable
        public RuntimeException finishBuildTree(List<Throwable> failures) {
            return exceptionAnalyser.transform(failures);
        }
    }
}
