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

import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.internal.DisplayName;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.logging.LoggingManagerFactory;
import org.gradle.internal.model.ObjectGuard;
import org.gradle.internal.model.StateTransitionController;
import org.gradle.internal.model.StateTransitionControllerFactory;
import org.gradle.internal.project.ImmutableProjectDescriptor;
import org.gradle.internal.service.CloseableServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.ProjectScopeServices;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.work.Synchronizer;

import java.io.Closeable;

/**
 * Controls the lifecycle of the mutable {@link ProjectInternal} instance for a project, plus its services.
 */
@ServiceScope(Scope.Project.class)
public class ProjectLifecycleController implements Closeable {

    private final ServiceRegistry buildServices;
    private final StateTransitionController<State> controller;
    private final ObjectGuard<ProjectInternal> model;

    private enum State implements StateTransitionController.State {
        NotCreated, Created, Configured
    }

    public ProjectLifecycleController(DisplayName displayName, StateTransitionControllerFactory factory, Synchronizer projectLockSynchronizer, ServiceRegistry buildServices) {
        this.buildServices = buildServices;
        this.model = new ObjectGuard<>(projectLockSynchronizer);
        this.controller = factory.newController(displayName, State.NotCreated);
    }

    public boolean isCreated() {
        return controller.hasSeenStateIgnoringFailures(State.Created) && model.hasValue();
    }

    public void assertConfigured() {
        controller.assertHasSeenState(State.Configured);
    }

    public void createMutableModel(
        ImmutableProjectDescriptor descriptor,
        BuildState build,
        ProjectState owner,
        ClassLoaderScope selfClassLoaderScope,
        ClassLoaderScope baseClassLoaderScope,
        IProjectFactory projectFactory
    ) {
        controller.transition(State.NotCreated, State.Created, () -> {
            ServiceRegistryFactory serviceRegistryFactory = domainObject -> {
                LoggingManagerFactory loggingManagerFactory = buildServices.get(LoggingManagerFactory.class);
                return ProjectScopeServices.create(buildServices, (ProjectInternal) domainObject, loggingManagerFactory);
            };
            ProjectInternal project = projectFactory.createProject(build, descriptor, owner, serviceRegistryFactory, selfClassLoaderScope, baseClassLoaderScope);
            model.initialize(project);
        });
    }

    public ObjectGuard<ProjectInternal> getModel() {
        controller.assertHasSeenState(State.Created);
        return model;
    }

    public ObjectGuard<ProjectInternal> getModelEvenAfterFailure() {
        controller.assertHasSeenStateIgnoringFailures(State.Created);
        return model;
    }

    public void ensureSelfConfigured() {
        controller.maybeTransitionIfNotCurrentlyTransitioning(State.Created, State.Configured, () -> model.runWithValue(ProjectInternal::evaluateUnchecked));
    }

    @Override
    public void close() {
        model.destroy(project -> {
            ((CloseableServiceRegistry) project.getServices()).close();
        });
    }

}
