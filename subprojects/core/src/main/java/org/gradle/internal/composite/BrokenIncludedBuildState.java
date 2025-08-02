/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.composite;

import com.google.common.base.Objects;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.DependencySubstitutions;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.tasks.TaskReference;
import org.gradle.initialization.IncludedBuildSpec;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Pair;
import org.gradle.internal.build.BuildProjectRegistry;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildToolingModelController;
import org.gradle.internal.build.BuildWorkGraphController;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.util.Path;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Set;
import java.util.function.Function;

@NullMarked
public class BrokenIncludedBuildState implements IncludedBuildState {
    private final IncludedBuildState build;
    private final Failure failure;

    public BrokenIncludedBuildState(IncludedBuildState build, Failure failure) {
        this.build = build;
        this.failure = failure;
    }

    @Override
    public String getName() {
        return build.getName();
    }

    @Override
    public File getRootDirectory() {
        return build.getRootDirectory();
    }

    @Override
    public boolean isPluginBuild() {
        return build.isPluginBuild();
    }

    @Override
    public Action<? super DependencySubstitutions> getRegisteredDependencySubstitutions() {
        return build.getRegisteredDependencySubstitutions();
    }

    @Override
    public <T> T withState(Transformer<T, ? super GradleInternal> action) {
        return build.withState(action);
    }

    @Override
    public IncludedBuildInternal getModel() {
        return new BrokenIncludedBuildInternal();
    }

    @Override
    public Set<Pair<ModuleVersionIdentifier, ProjectComponentIdentifier>> getAvailableModules() {
        return build.getAvailableModules();
    }

    @Override
    public ExecutionResult<Void> finishBuild() {
        return build.finishBuild();
    }

    @Override
    public BuildDefinition getBuildDefinition() {
        return build.getBuildDefinition();
    }

    @Override
    public DisplayName getDisplayName() {
        return build.getDisplayName();
    }

    @Override
    public BuildIdentifier getBuildIdentifier() {
        return build.getBuildIdentifier();
    }

    @Override
    public Path getIdentityPath() {
        return build.getIdentityPath();
    }

    @Override
    public @Nullable BuildState getParent() {
        return build.getParent();
    }

    @Override
    public boolean isImplicitBuild() {
        return build.isImplicitBuild();
    }

    @Override
    public boolean isImportableBuild() {
        return build.isImportableBuild();
    }

    @Override
    public void ensureProjectsLoaded() {

    }

    @Override
    public boolean isProjectsLoaded() {
        return false;
    }

    @Override
    public boolean isProjectsCreated() {
        return false;
    }

    @Override
    public void ensureProjectsConfigured() {

    }

    @Override
    public BuildProjectRegistry getProjects() {
        return build.getProjects();
    }

    @Override
    public void assertCanAdd(IncludedBuildSpec includedBuildSpec) {
        build.assertCanAdd(includedBuildSpec);
    }

    @Override
    public File getBuildRootDir() {
        return build.getBuildRootDir();
    }

    @Override
    public GradleInternal getMutableModel() {
        return build.getMutableModel();
    }

    @Override
    public BuildWorkGraphController getWorkGraph() {
        return build.getWorkGraph();
    }

    @Override
    public <T> T withToolingModels(Function<? super BuildToolingModelController, T> action) {
        return build.withToolingModels(action);
    }

    @Override
    public ExecutionResult<Void> beforeModelReset() {
        return build.beforeModelReset();
    }

    @Override
    public void resetModel() {
        build.resetModel();
    }

    @Override
    public ExecutionResult<Void> beforeModelDiscarded(boolean failed) {
        return build.beforeModelDiscarded(failed);
    }

    @Override
    public Failure getFailure() {
        return failure;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof BrokenIncludedBuildState) {
            BrokenIncludedBuildState that = (BrokenIncludedBuildState) o;
            return Objects.equal(build, that.build);
        }
        if (o instanceof IncludedBuildState) {
            return Objects.equal(build, o);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(build);
    }

    @NullMarked
    private class BrokenIncludedBuildInternal implements IncludedBuildInternal {
        @Override
        public BuildState getTarget() {
            return BrokenIncludedBuildState.this;
        }

        @Override
        public String getName() {
            return build.getModel().getName();
        }

        @Override
        public File getProjectDir() {
            return build.getModel().getProjectDir();
        }

        @Override
        public TaskReference task(String path) {
            return build.getModel().task(path);
        }
    }
}
