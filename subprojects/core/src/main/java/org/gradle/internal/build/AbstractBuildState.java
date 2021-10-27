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

package org.gradle.internal.build;

import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.initialization.IncludedBuildSpec;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;

import java.util.function.Consumer;

public abstract class AbstractBuildState implements BuildState {
    @Override
    public DisplayName getDisplayName() {
        return Describables.of(getBuildIdentifier());
    }

    @Override
    public String toString() {
        return getDisplayName().getDisplayName();
    }

    @Override
    public void assertCanAdd(IncludedBuildSpec includedBuildSpec) {
        throw new UnsupportedOperationException("Cannot include build '" + includedBuildSpec.rootDir.getName() + "' in " + getBuildIdentifier() + ". This is not supported yet.");
    }

    @Override
    public boolean isImportableBuild() {
        return true;
    }

    protected abstract ProjectStateRegistry getProjectStateRegistry();

    @Override
    public BuildProjectRegistry getProjects() {
        return getProjectStateRegistry().projectsFor(getBuildIdentifier());
    }

    protected abstract BuildLifecycleController getBuildController();

    @Override
    public void ensureProjectsLoaded() {
        getBuildController().getLoadedSettings();
    }

    @Override
    public void ensureProjectsConfigured() {
        getBuildController().getConfiguredBuild();
    }

    @Override
    public SettingsInternal getLoadedSettings() throws IllegalStateException {
        return getBuildController().getLoadedSettings();
    }

    @Override
    public void populateWorkGraph(Consumer<? super TaskExecutionGraphInternal> action) {
        BuildLifecycleController buildController = getBuildController();
        buildController.prepareToScheduleTasks();
        buildController.populateWorkGraph(action);
    }
}
