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

import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.initialization.IncludedBuildSpec;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.buildtree.BuildTreeState;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.service.scopes.BuildScopeServices;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.util.function.Function;

public abstract class AbstractBuildState implements BuildState, Closeable {
    private final BuildScopeServices buildServices;
    private final Lazy<BuildLifecycleController> buildLifecycleController;
    private final Lazy<ProjectStateRegistry> projectStateRegistry;
    private final Lazy<BuildWorkGraphController> workGraphController;

    public AbstractBuildState(BuildTreeState buildTree, BuildDefinition buildDefinition, @Nullable BuildState parent) {
        // Create the controllers using the services of the nested tree
        BuildModelControllerServices buildModelControllerServices = buildTree.getServices().get(BuildModelControllerServices.class);
        BuildModelControllerServices.Supplier supplier = buildModelControllerServices.servicesForBuild(buildDefinition, this, parent);
        buildServices = prepareServices(buildTree, buildDefinition, supplier);
        buildLifecycleController = Lazy.locking().of(() -> buildServices.get(BuildLifecycleController.class));
        projectStateRegistry = Lazy.locking().of(() -> buildServices.get(ProjectStateRegistry.class));
        workGraphController = Lazy.locking().of(() -> buildServices.get(BuildWorkGraphController.class));
    }

    protected BuildScopeServices prepareServices(BuildTreeState buildTree, BuildDefinition buildDefinition, BuildModelControllerServices.Supplier supplier) {
        return new BuildScopeServices(buildTree.getServices(), supplier);
    }

    protected BuildScopeServices getBuildServices() {
        return buildServices;
    }

    @Override
    public void close() throws IOException {
        buildServices.close();
    }

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

    protected ProjectStateRegistry getProjectStateRegistry() {
        return projectStateRegistry.get();
    }

    @Override
    public BuildProjectRegistry getProjects() {
        return getProjectStateRegistry().projectsFor(getBuildIdentifier());
    }

    protected BuildLifecycleController getBuildController() {
        return buildLifecycleController.get();
    }

    @Override
    public void ensureProjectsLoaded() {
        getBuildController().loadSettings();
    }

    @Override
    public void ensureProjectsConfigured() {
        getBuildController().configureProjects();
    }

    @Override
    public GradleInternal getMutableModel() {
        return getBuildController().getGradle();
    }

    @Override
    public BuildWorkGraphController getWorkGraph() {
        return workGraphController.get();
    }

    @Override
    public <T> T withToolingModels(Function<? super BuildToolingModelController, T> action) {
        return getBuildController().withToolingModels(action);
    }
}
