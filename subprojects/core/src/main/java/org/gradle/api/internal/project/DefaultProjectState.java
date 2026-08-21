/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.project.IsolatedProject;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.model.StateTransitionControllerFactory;
import org.gradle.internal.project.ImmutableProjectDescriptor;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.work.Synchronizer;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.internal.CollectionUtils;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

class DefaultProjectState implements ProjectState, Closeable {

    private final BuildState owner;
    private final ImmutableProjectDescriptor descriptor;
    private final ProjectIdentity identity;
    private final IProjectFactory projectFactory;
    private final ProjectLifecycleController controller;
    private final ProjectStateLookup projectStateLookup;

    private final ResourceLock projectLock;
    private final ResourceLock taskLock;

    private final ProjectLockSynchronizer synchronizer;

    DefaultProjectState(
        BuildState owner,
        ImmutableProjectDescriptor descriptor,
        IProjectFactory projectFactory,
        StateTransitionControllerFactory stateTransitionControllerFactory,
        ServiceRegistry buildServices,
        WorkerLeaseService workerLeaseService,
        ProjectStateLookup projectStateLookup
    ) {
        this.owner = owner;
        this.descriptor = descriptor;
        this.identity = descriptor.getIdentity();
        this.projectFactory = projectFactory;
        this.projectStateLookup = projectStateLookup;

        this.projectLock = workerLeaseService.getProjectLock(owner.getIdentityPath(), identity.getBuildTreePath());
        this.taskLock = workerLeaseService.getTaskExecutionLock(owner.getIdentityPath(), identity.getBuildTreePath());

        ResourceLock allProjectsLock = workerLeaseService.getAllProjectsLock(owner.getIdentityPath());
        this.synchronizer = new ProjectLockSynchronizer(workerLeaseService, projectLock, allProjectsLock);
        this.controller = new ProjectLifecycleController(getDisplayName(), stateTransitionControllerFactory, synchronizer, buildServices);
    }

    @Override
    public BuildState getOwner() {
        return owner;
    }

    // region About this project

    @Override
    public ProjectIdentity getIdentity() {
        return identity;
    }

    @Override
    public File getProjectDir() {
        return descriptor.getProjectDir();
    }

    @Override
    public ProjectComponentIdentifier getComponentIdentifier() {
        return new DefaultProjectComponentIdentifier(identity);
    }

    @Override
    public ImmutableProjectDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public IsolatedProject getIsolated() {
        return new DefaultIsolatedProject(this);
    }

    @Override
    public String toString() {
        return getDisplayName().getDisplayName();
    }

    // endregion

    // region Project hierarchy

    @Override
    @Nullable
    public ProjectState getParent() {
        ProjectIdentity parentIdentity = descriptor.getParent();
        if (parentIdentity == null) {
            return null;
        }

        return getState(parentIdentity);
    }

    @Override
    public Set<ProjectState> getChildProjects() {
        Set<ProjectState> children = new TreeSet<>(Comparator.comparing(ProjectState::getIdentityPath));
        for (ProjectIdentity child : descriptor.getChildren()) {
            children.add(getState(child));
        }
        return children;
    }

    @Override
    public Collection<ProjectState> getUnorderedChildProjects() {
        return CollectionUtils.transform(descriptor.getChildren(), this::getState);
    }

    private ProjectState getState(ProjectIdentity identity) {
        ProjectState state = projectStateLookup.findProject(identity.getBuildTreePath());
        if (state == null) {
            throw new IllegalStateException("Project '" + identity.getBuildTreePath() + "' is not found in the registry");
        }
        return state;
    }

    @Override
    public boolean hasChildren() {
        return !descriptor.getChildren().isEmpty();
    }

    // endregion

    // region Lifecycle management

    @Override
    public boolean isCreated() {
        return controller.isCreated();
    }

    @Override
    public void createMutableModel(ClassLoaderScope selfClassLoaderScope, ClassLoaderScope baseClassLoaderScope) {
        controller.createMutableModel(descriptor, owner, this, selfClassLoaderScope, baseClassLoaderScope, projectFactory);
    }

    @Override
    public ProjectInternal getMutableModel() {
        return controller.getModel().unsafeGet();
    }

    @Override
    public ProjectInternal getMutableModelEvenAfterFailure() {
        return controller.getModelEvenAfterFailure().unsafeGet();
    }

    @Override
    public void ensureConfigured() {
        // Need to configure intermediate parent projects for configure-on-demand
        ProjectState parent = getParent();
        if (parent != null) {
            parent.ensureConfigured();
        }
        controller.ensureSelfConfigured();
    }

    @Override
    public void ensureSelfConfigured() {
        ProjectState parent = getParent();
        if (parent != null) {
            ((DefaultProjectState) parent).controller.assertConfigured();
        }
        controller.ensureSelfConfigured();
    }

    @Override
    public void ensureTasksDiscovered() {
        controller.ensureSelfConfigured();
        applyToMutableState(project -> {
            project.getTasks().discoverTasks();
            project.bindAllModelRules();
        });
    }

    // endregion

    @Override
    public ResourceLock getAccessLock() {
        return projectLock;
    }

    @Override
    public ResourceLock getTaskExecutionLock() {
        return taskLock;
    }

    @Override
    public void applyToMutableState(Consumer<? super ProjectInternal> action) {
        controller.getModel().runWithValue(action);
    }

    @Override
    public <S extends @Nullable Object> S fromMutableState(Function<? super ProjectInternal, ? extends S> function) {
        return controller.getModel().fromValue(function);
    }

    @Override
    public <S extends @Nullable Object> S fromMutableStateEvenAfterFailure(Function<? super ProjectInternal, ? extends S> function) {
        return controller.getModelEvenAfterFailure().fromValue(function);
    }

    @Override
    public <S> S forceAccessToMutableState(Function<? super ProjectInternal, ? extends S> factory) {
        return synchronizer.withForcedAccess(() -> controller.getModel().fromValue(factory));
    }

    @Override
    public boolean hasMutableState() {
        return synchronizer.hasLock();
    }

    @Override
    public void close() {
        controller.close();
    }

    private static class ProjectLockSynchronizer implements Synchronizer {

        private final WorkerLeaseService workerLeaseService;
        private final ResourceLock projectLock;
        private final ResourceLock allProjectsLock;

        private final Set<Thread> canDoAnythingToThisProject = new CopyOnWriteArraySet<>();

        public ProjectLockSynchronizer(
            WorkerLeaseService workerLeaseService,
            ResourceLock projectLock,
            ResourceLock allProjectsLock
        ) {
            this.workerLeaseService = workerLeaseService;
            this.projectLock = projectLock;
            this.allProjectsLock = allProjectsLock;
        }

        @Override
        public void withLock(Runnable action) {
            withLock(() -> {
                action.run();
                return null;
            });
        }

        @Override
        public <T extends @Nullable Object> T withLock(Supplier<T> action) {
            Thread currentThread = Thread.currentThread();
            if (canDoAnythingToThisProject.contains(currentThread)) {
                // Current thread is allowed to access anything at any time, so run the action
                return action.get();
            }

            Collection<? extends ResourceLock> currentLocks = workerLeaseService.getCurrentProjectLocks();
            if (currentLocks.contains(projectLock) || currentLocks.contains(allProjectsLock)) {
                // if we already hold the project lock for this project
                if (currentLocks.size() == 1) {
                    // the lock for this project is the only lock we hold, can run the action
                    return action.get();
                } else {
                    throw new IllegalStateException("Current thread holds more than one project lock. It should hold only one project lock at any given time.");
                }
            } else {
                return workerLeaseService.withReplacedLocks(currentLocks, projectLock, action::get);
            }
        }

        public <T> T withForcedAccess(Supplier<T> action) {
            Thread currentThread = Thread.currentThread();
            boolean added = canDoAnythingToThisProject.add(currentThread);
            try {
                return action.get();
            } finally {
                if (added) {
                    canDoAnythingToThisProject.remove(currentThread);
                }
            }
        }

        public boolean hasLock() {
            Thread currentThread = Thread.currentThread();
            if (canDoAnythingToThisProject.contains(currentThread)) {
                return true;
            }
            Collection<? extends ResourceLock> locks = workerLeaseService.getCurrentProjectLocks();
            return locks.contains(projectLock) || locks.contains(allProjectsLock);
        }

    }

}
