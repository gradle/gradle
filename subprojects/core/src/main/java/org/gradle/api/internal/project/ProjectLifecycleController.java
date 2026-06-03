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
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.util.concurrent.locks.LockSupport;

/**
 * Controls the lifecycle of the mutable {@link ProjectInternal} instance for a project, plus its services.
 */
@ServiceScope(Scope.Project.class)
public class ProjectLifecycleController implements Closeable {
    private final ServiceRegistry buildServices;
    private final StateTransitionController<State> controller;
    @Nullable
    private ProjectInternal project;
    @Nullable
    private CloseableServiceRegistry projectScopeServices;

    private enum State implements StateTransitionController.State {
        NotCreated, Created, Configured
    }

    public ProjectLifecycleController(DisplayName displayName, StateTransitionControllerFactory factory, Synchronizer synchronizer, ServiceRegistry buildServices) {
        this.buildServices = buildServices;
        controller = factory.newController(displayName, State.NotCreated, synchronizer);
    }

    public boolean isCreated() {
        return project != null;
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
            ProjectState parent = owner.getParent();
            ProjectInternal parentModel = parent == null ? null : parent.getMutableModel();
            ServiceRegistryFactory serviceRegistryFactory = domainObject -> {
                LoggingManagerFactory loggingManagerFactory = buildServices.get(LoggingManagerFactory.class);
                projectScopeServices = ProjectScopeServices.create(buildServices, (ProjectInternal) domainObject, loggingManagerFactory);
                return projectScopeServices;
            };
            project = projectFactory.createProject(build.getMutableModel(), descriptor, owner, parentModel, serviceRegistryFactory, selfClassLoaderScope, baseClassLoaderScope);
        });
    }

    public ProjectInternal getMutableModel() {
        controller.assertHasSeenState(State.Created);
        return project;
    }

    public ProjectInternal getMutableModelEvenAfterFailure() {
        controller.assertHasSeenStateIgnoringFailures(State.Created);
        return project;
    }

    public void ensureSelfConfigured() {
        // DEBUG (flaky investigation): trace the configuration decision for each worker/project.
        String dbgId = project == null ? "<uncreated>" : String.valueOf(project.getIdentityPath());
        debugLog("ensureSelfConfigured ENTER project=" + dbgId
            + " hasSeenConfigured=" + controller.hasSeenState(State.Configured)
            + " isTransitioningToConfigured=" + controller.isTransitioningTo(State.Configured));

        // Avoid taking the controller lock if already configured, to avoid contention and deadlocks.
        // ProjectState#ensureConfigured tries to configure parent projects, and child projects shouldn't
        // need to all take the lock if the parent project(s) are already configured.
        if (controller.hasSeenState(State.Configured)) {
            return;
        }
        // The check above is not atomic with the lock acquisition below. If another worker is already
        // configuring this project (e.g. when project-scoped tooling models are built in parallel and a
        // child project asks for its parent to be configured), wait for that configuration to complete
        // WITHOUT taking the project lock. Taking the lock here can deadlock: this same project lock may
        // shortly be held for a long time by another operation on this project, such as building this
        // project's tooling model under DefaultProjectState#runWithModelLock. We only take the lock when
        // no one else is configuring the project, in which case the model-building lock cannot yet be held
        // (model building requires the project to already be configured).
        while (controller.isTransitioningTo(State.Configured)) {
            if (controller.hasSeenState(State.Configured)) {
                debugLog("ensureSelfConfigured WAIT->configured project=" + dbgId);
                return;
            }
            LockSupport.parkNanos(100_000L);
        }
        debugLog("ensureSelfConfigured LOCK-PATH (maybeTransition) project=" + dbgId
            + " hasSeen=" + controller.hasSeenState(State.Configured)
            + " isTransitioning=" + controller.isTransitioningTo(State.Configured));
        controller.maybeTransitionIfNotCurrentlyTransitioning(State.Created, State.Configured, () -> project.evaluateUnchecked());
        debugLog("ensureSelfConfigured LOCK-PATH done project=" + dbgId);
    }

    // DEBUG (flaky investigation): direct stdout so it is forwarded to the tooling client and captured by CI.
    private static void debugLog(String message) {
        System.out.println("@@CFGDBG@@ " + System.currentTimeMillis() + " [" + Thread.currentThread().getName() + "] " + message);
        System.out.flush();
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
