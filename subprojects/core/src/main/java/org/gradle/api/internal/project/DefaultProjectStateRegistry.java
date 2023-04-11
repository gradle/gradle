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
package org.gradle.api.internal.project;

import com.google.common.collect.Maps;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.initialization.DefaultProjectDescriptor;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.build.BuildProjectRegistry;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.model.CalculatedModelValue;
import org.gradle.internal.model.ModelContainer;
import org.gradle.internal.model.StateTransitionControllerFactory;
import org.gradle.internal.resources.ProjectLeaseRegistry;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

public class DefaultProjectStateRegistry implements ProjectStateRegistry, Closeable {
    private final WorkerLeaseService workerLeaseService;
    private final Object lock = new Object();
    private final Map<Path, ProjectStateImpl> projectsByPath = Maps.newLinkedHashMap();
    private final Map<ProjectComponentIdentifier, ProjectStateImpl> projectsById = Maps.newHashMap();
    private final Map<BuildIdentifier, DefaultBuildProjectRegistry> projectsByBuild = Maps.newHashMap();

    public DefaultProjectStateRegistry(WorkerLeaseService workerLeaseService) {
        this.workerLeaseService = workerLeaseService;
    }

    @Override
    public void registerProjects(BuildState owner, ProjectRegistry<DefaultProjectDescriptor> projectRegistry) {
        Set<DefaultProjectDescriptor> allProjects = projectRegistry.getAllProjects();
        synchronized (lock) {
            DefaultBuildProjectRegistry buildProjectRegistry = getBuildProjectRegistry(owner);
            if (!buildProjectRegistry.projectsByPath.isEmpty()) {
                throw new IllegalStateException("Projects for " + owner.getDisplayName() + " have already been registered.");
            }
            for (DefaultProjectDescriptor descriptor : allProjects) {
                addProject(owner, buildProjectRegistry, descriptor);
            }
        }
    }

    private DefaultBuildProjectRegistry getBuildProjectRegistry(BuildState owner) {
        DefaultBuildProjectRegistry buildProjectRegistry = projectsByBuild.get(owner.getBuildIdentifier());
        if (buildProjectRegistry == null) {
            buildProjectRegistry = new DefaultBuildProjectRegistry(owner, workerLeaseService);
            projectsByBuild.put(owner.getBuildIdentifier(), buildProjectRegistry);
        }
        return buildProjectRegistry;
    }

    @Override
    public ProjectState registerProject(BuildState owner, DefaultProjectDescriptor projectDescriptor) {
        synchronized (lock) {
            DefaultBuildProjectRegistry buildProjectRegistry = getBuildProjectRegistry(owner);
            return addProject(owner, buildProjectRegistry, projectDescriptor);
        }
    }

    @Override
    public void discardProjectsFor(BuildState build) {
        DefaultBuildProjectRegistry registry = projectsByBuild.get(build.getBuildIdentifier());
        if (registry != null) {
            for (ProjectStateImpl project : registry.projectsByPath.values()) {
                projectsById.remove(project.identifier);
                projectsByPath.remove(project.identityPath);
            }
            CompositeStoppable.stoppable(registry.projectsByPath.values()).stop();
            registry.projectsByPath.clear();
        }
    }

    private ProjectState addProject(BuildState owner, DefaultBuildProjectRegistry projectRegistry, DefaultProjectDescriptor descriptor) {
        Path projectPath = descriptor.path();
        Path identityPath = owner.calculateIdentityPathForProject(projectPath);
        String name = descriptor.getName();
        ProjectComponentIdentifier projectIdentifier = new DefaultProjectComponentIdentifier(owner.getBuildIdentifier(), identityPath, projectPath, name);
        ServiceRegistry buildServices = owner.getMutableModel().getServices();
        IProjectFactory projectFactory = buildServices.get(IProjectFactory.class);
        StateTransitionControllerFactory stateTransitionControllerFactory = buildServices.get(StateTransitionControllerFactory.class);
        ProjectStateImpl projectState = new ProjectStateImpl(owner, identityPath, projectPath, descriptor.getName(), projectIdentifier, descriptor, projectFactory, stateTransitionControllerFactory, buildServices);
        projectsByPath.put(identityPath, projectState);
        projectsById.put(projectIdentifier, projectState);
        projectRegistry.add(projectPath, projectState);
        return projectState;
    }

    @Override
    public Collection<ProjectStateImpl> getAllProjects() {
        synchronized (lock) {
            return projectsByPath.values();
        }
    }

    // TODO - can kill this method, as the caller can use ProjectInternal.getOwner() instead
    @Override
    public ProjectState stateFor(Project project) {
        return ((ProjectInternal) project).getOwner();
    }

    @Override
    public ProjectState stateFor(ProjectComponentIdentifier identifier) {
        synchronized (lock) {
            ProjectStateImpl projectState = projectsById.get(identifier);
            if (projectState == null) {
                throw new IllegalArgumentException(identifier.getDisplayName() + " not found.");
            }
            return projectState;
        }
    }

    @Override
    public BuildProjectRegistry projectsFor(BuildIdentifier buildIdentifier) throws IllegalArgumentException {
        synchronized (lock) {
            BuildProjectRegistry registry = projectsByBuild.get(buildIdentifier);
            if (registry == null) {
                throw new IllegalArgumentException("Projects for " + buildIdentifier + " have not been registered yet.");
            }
            return registry;
        }
    }

    @Nullable
    @Override
    public BuildProjectRegistry findProjectsFor(BuildIdentifier buildIdentifier) {
        synchronized (lock) {
            return projectsByBuild.get(buildIdentifier);
        }
    }

    @Override
    public <T> T allowUncontrolledAccessToAnyProject(Factory<T> factory) {
        return workerLeaseService.allowUncontrolledAccessToAnyProject(factory);
    }

    @Override
    public void close() {
        CompositeStoppable.stoppable(projectsByPath.values()).stop();
    }

    private static class DefaultBuildProjectRegistry implements BuildProjectRegistry {
        private final BuildState owner;
        private final WorkerLeaseService workerLeaseService;
        private final Map<Path, ProjectStateImpl> projectsByPath = Maps.newLinkedHashMap();

        public DefaultBuildProjectRegistry(BuildState owner, WorkerLeaseService workerLeaseService) {
            this.owner = owner;
            this.workerLeaseService = workerLeaseService;
        }

        public void add(Path projectPath, ProjectStateImpl projectState) {
            projectsByPath.put(projectPath, projectState);
        }

        @Override
        public ProjectState getRootProject() {
            return getProject(Path.ROOT);
        }

        @Override
        public ProjectState getProject(Path projectPath) {
            ProjectStateImpl projectState = projectsByPath.get(projectPath);
            if (projectState == null) {
                throw new IllegalArgumentException("Project with path '" + projectPath + "' not found in " + owner.getDisplayName() + ".");
            }
            return projectState;
        }

        @Nullable
        @Override
        public ProjectState findProject(Path projectPath) {
            return projectsByPath.get(projectPath);
        }

        @Override
        public Set<? extends ProjectState> getAllProjects() {
            TreeSet<ProjectState> projects = new TreeSet<>(Comparator.comparing(ProjectState::getIdentityPath));
            projects.addAll(projectsByPath.values());
            return projects;
        }

        @Override
        public void withMutableStateOfAllProjects(Runnable runnable) {
            withMutableStateOfAllProjects(Factories.toFactory(runnable));
        }

        @Override
        public <T> T withMutableStateOfAllProjects(Factory<T> factory) {
            ResourceLock allProjectsLock = workerLeaseService.getAllProjectsLock(owner.getIdentityPath());
            Collection<? extends ResourceLock> locks = workerLeaseService.getCurrentProjectLocks();
            return workerLeaseService.withReplacedLocks(locks, allProjectsLock, factory);
        }
    }

    private class ProjectStateImpl implements ProjectState, Closeable {
        private final Path projectPath;
        private final String projectName;
        private final ProjectComponentIdentifier identifier;
        private final DefaultProjectDescriptor descriptor;
        private final IProjectFactory projectFactory;
        private final BuildState owner;
        private final Path identityPath;
        private final ResourceLock allProjectsLock;
        private final ResourceLock projectLock;
        private final ResourceLock taskLock;
        private final Set<Thread> canDoAnythingToThisProject = new CopyOnWriteArraySet<>();
        private final ProjectLifecycleController controller;
        private final Lazy<Integer> depth = Lazy.unsafe().of(() -> getParent() != null ? getParent().getDepth() + 1 : 0);

        ProjectStateImpl(
            BuildState owner,
            Path identityPath,
            Path projectPath,
            String projectName,
            ProjectComponentIdentifier identifier,
            DefaultProjectDescriptor descriptor,
            IProjectFactory projectFactory,
            StateTransitionControllerFactory stateTransitionControllerFactory,
            ServiceRegistry buildServices
        ) {
            this.owner = owner;
            this.identityPath = identityPath;
            this.projectPath = projectPath;
            this.projectName = projectName;
            this.identifier = identifier;
            this.descriptor = descriptor;
            this.projectFactory = projectFactory;
            this.allProjectsLock = workerLeaseService.getAllProjectsLock(owner.getIdentityPath());
            this.projectLock = workerLeaseService.getProjectLock(owner.getIdentityPath(), identityPath);
            this.taskLock = workerLeaseService.getTaskExecutionLock(owner.getIdentityPath(), identityPath);
            this.controller = new ProjectLifecycleController(getDisplayName(), stateTransitionControllerFactory, buildServices);
        }

        @Override
        public DisplayName getDisplayName() {
            if (identityPath.equals(Path.ROOT)) {
                return Describables.withTypeAndName("root project", projectName);
            } else {
                return Describables.withTypeAndName("project", identityPath.getPath());
            }
        }

        @Override
        public String toString() {
            return getDisplayName().getDisplayName();
        }

        @Override
        public BuildState getOwner() {
            return owner;
        }

        @Nullable
        @Override
        public ProjectState getParent() {
            return identityPath.getParent() == null ? null : projectsByPath.get(identityPath.getParent());
        }

        @Nullable
        @Override
        public ProjectState getBuildParent() {
            if (descriptor.getParent() != null) {
                // Identity path of parent can be different to identity path parent, if the names are tweaked in the settings file
                // Ideally they would be exactly the same, always
                Path parentPath = owner.calculateIdentityPathForProject(descriptor.getParent().path());
                ProjectStateImpl parentState = projectsByPath.get(parentPath);
                if (parentState == null) {
                    throw new IllegalStateException("Parent project " + parentPath + " is not registered for project " + identityPath);
                }
                return parentState;
            } else {
                return null;
            }
        }

        @Override
        public Set<ProjectState> getChildProjects() {
            Set<ProjectState> children = new TreeSet<>(Comparator.comparing(ProjectState::getIdentityPath));
            for (DefaultProjectDescriptor child : descriptor.children()) {
                children.add(projectsByPath.get(owner.calculateIdentityPathForProject(child.path())));
            }
            return children;
        }

        @Override
        public String getName() {
            return projectName;
        }

        @Override
        public Path getIdentityPath() {
            return identityPath;
        }

        @Override
        public Path getProjectPath() {
            return projectPath;
        }

        @Override
        public File getProjectDir() {
            return descriptor.getProjectDir();
        }

        @Override
        public int getDepth() {
            return depth.get();
        }

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
            return controller.getMutableModel();
        }

        @Override
        public void ensureConfigured() {
            // Need to configure intermediate parent projects for configure-on-demand
            ProjectState parent = getBuildParent();
            if (parent != null) {
                parent.ensureConfigured();
            }
            controller.ensureSelfConfigured();
        }

        @Override
        public void ensureTasksDiscovered() {
            controller.ensureTasksDiscovered();
        }

        @Override
        public ProjectComponentIdentifier getComponentIdentifier() {
            return identifier;
        }

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
            fromMutableState(p -> {
                action.accept(p);
                return null;
            });
        }

        @Override
        public <S> S fromMutableState(Function<? super ProjectInternal, ? extends S> function) {
            Thread currentThread = Thread.currentThread();
            if (workerLeaseService.isAllowedUncontrolledAccessToAnyProject() || canDoAnythingToThisProject.contains(currentThread)) {
                // Current thread is allowed to access anything at any time, so run the function
                return function.apply(getMutableModel());
            }

            Collection<? extends ResourceLock> currentLocks = workerLeaseService.getCurrentProjectLocks();
            if (currentLocks.contains(projectLock) || currentLocks.contains(allProjectsLock)) {
                // if we already hold the project lock for this project
                if (currentLocks.size() == 1) {
                    // the lock for this project is the only lock we hold, can run the function
                    return function.apply(getMutableModel());
                } else {
                    throw new IllegalStateException("Current thread holds more than one project lock. It should hold only one project lock at any given time.");
                }
            } else {
                return workerLeaseService.withReplacedLocks(currentLocks, projectLock, () -> function.apply(getMutableModel()));
            }
        }

        @Override
        public <S> S forceAccessToMutableState(Function<? super ProjectInternal, ? extends S> factory) {
            Thread currentThread = Thread.currentThread();
            boolean added = canDoAnythingToThisProject.add(currentThread);
            try {
                return factory.apply(getMutableModel());
            } finally {
                if (added) {
                    canDoAnythingToThisProject.remove(currentThread);
                }
            }
        }

        @Override
        public boolean hasMutableState() {
            Thread currentThread = Thread.currentThread();
            if (canDoAnythingToThisProject.contains(currentThread) || workerLeaseService.isAllowedUncontrolledAccessToAnyProject()) {
                return true;
            }
            Collection<? extends ResourceLock> locks = workerLeaseService.getCurrentProjectLocks();
            return locks.contains(projectLock) || locks.contains(allProjectsLock);
        }

        @Override
        public <T> CalculatedModelValue<T> newCalculatedValue(@Nullable T initialValue) {
            return new CalculatedModelValueImpl<>(this, workerLeaseService, initialValue);
        }

        @Override
        public void close() {
            controller.close();
        }
    }

    private static class CalculatedModelValueImpl<T> implements CalculatedModelValue<T> {
        private final ProjectLeaseRegistry projectLeaseRegistry;
        private final ModelContainer<?> owner;
        private final ReentrantLock lock = new ReentrantLock();
        private volatile T value;

        public CalculatedModelValueImpl(ProjectStateImpl owner, WorkerLeaseService projectLeaseRegistry, @Nullable T initialValue) {
            this.projectLeaseRegistry = projectLeaseRegistry;
            this.value = initialValue;
            this.owner = owner;
        }

        @Override
        public T get() throws IllegalStateException {
            T currentValue = getOrNull();
            if (currentValue == null) {
                throw new IllegalStateException("No calculated value is available for " + owner);
            }
            return currentValue;
        }

        @Override
        public T getOrNull() {
            // Grab the current value, ignore updates that may be happening
            return value;
        }

        @Override
        public void set(T newValue) {
            assertCanMutate();
            value = newValue;
        }

        @Override
        public T update(Function<T, T> updateFunction) {
            acquireUpdateLock();
            try {
                T newValue = updateFunction.apply(value);
                value = newValue;
                return newValue;
            } finally {
                releaseUpdateLock();
            }
        }

        private void acquireUpdateLock() {
            // It's important that we do not block waiting for the lock while holding the project mutation lock.
            // Doing so can lead to deadlocks.

            assertCanMutate();

            if (lock.tryLock()) {
                // Update lock was not contended, can keep holding the project locks
                return;
            }

            // Another thread holds the update lock, release the project locks and wait for the other thread to finish the update
            projectLeaseRegistry.blocking(lock::lock);
        }

        private void assertCanMutate() {
            if (!owner.hasMutableState()) {
                throw new IllegalStateException("Current thread does not hold the state lock for " + owner);
            }
        }

        private void releaseUpdateLock() {
            lock.unlock();
        }
    }
}
