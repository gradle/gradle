/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.project;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.initialization.DependenciesAccessors;
import org.gradle.initialization.ProjectDescriptorInternal;
import org.gradle.internal.DisplayName;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.management.DependencyResolutionManagementInternal;
import org.gradle.internal.model.StateTransitionController;
import org.gradle.internal.model.StateTransitionControllerFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.scripts.ProjectScopedScriptResolution;
import org.gradle.internal.service.CloseableServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.ProjectScopeServices;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.File;

/**
 * Controls the lifecycle of the mutable {@link ProjectInternal} instance for a project, plus its services.
 */
public class ProjectLifecycleController implements Closeable {

    private final StateTransitionController<State> controller;

    private @Nullable DefaultProject project;
    private @Nullable CloseableServiceRegistry projectScopeServices;

    private enum State implements StateTransitionController.State {
        NotCreated, Created, Configured
    }

    public ProjectLifecycleController(
        DisplayName displayName,
        StateTransitionControllerFactory factory
    ) {
        this.controller = factory.newController(displayName, State.NotCreated);
    }

    public boolean isCreated() {
        return project != null;
    }

    public void assertConfigured() {
        controller.assertInStateOrLater(State.Configured);
    }

    public void createMutableModel(
        ProjectDescriptorInternal descriptor,
        BuildState build,
        ProjectState owner,
        ClassLoaderScope selfClassLoaderScope,
        ClassLoaderScope baseClassLoaderScope
    ) {
        controller.transition(State.NotCreated, State.Created, () -> {
            this.project = createProject(descriptor, build, owner, selfClassLoaderScope, baseClassLoaderScope);
            this.projectScopeServices = project.getCloseableServices();
        });
    }

    private static DefaultProject createProject(
        ProjectDescriptorInternal descriptor,
        BuildState build,
        ProjectState owner,
        ClassLoaderScope selfClassLoaderScope,
        ClassLoaderScope baseClassLoaderScope
    ) {
        GradleInternal gradle = build.getMutableModel();
        ServiceRegistry buildServices = gradle.getServices();

        ServiceRegistryFactory serviceRegistryFactory = domainObject -> ProjectScopeServices.create(buildServices, (ProjectInternal) domainObject);

        // Need to wrap resolution of the build file to associate the build file with the correct project
        File buildFile = buildServices.get(ProjectScopedScriptResolution.class).resolveScriptsForProject(owner.getIdentity(), descriptor::getBuildFile);

        Instantiator instantiator = buildServices.get(InstantiatorFactory.class).decorateScheme().instantiator();
        DefaultProject project = instantiator.newInstance(DefaultProject.class,
            buildFile,
            owner,
            serviceRegistryFactory,
            selfClassLoaderScope,
            baseClassLoaderScope
        );

        // TODO: We should find a proper home for all of these side-effects instead of doing them here.
        buildServices.get(DependencyResolutionManagementInternal.class).configureProject(project);
        project.beforeEvaluate(p -> {
            buildServices.get(DependenciesAccessors.class).createExtensions(project);
        });
        gradle.getProjectRegistry().addProject(project);

        return project;
    }

    public ProjectInternal getMutableModel() {
        controller.assertInStateOrLater(State.Created);
        return project;
    }

    public void ensureSelfConfigured() {
        controller.maybeTransitionIfNotCurrentlyTransitioning(State.Created, State.Configured, () -> project.evaluateUnchecked());
    }

    public void ensureTasksDiscovered() {
        ensureSelfConfigured();
        project.getTasks().discoverTasks();
        project.bindAllModelRules();
    }

    @Override
    public void close() {
        if (project != null) {
            try {
                projectScopeServices.close();
            } finally {
                project = null;
                projectScopeServices = null;
            }
        }
    }

}
