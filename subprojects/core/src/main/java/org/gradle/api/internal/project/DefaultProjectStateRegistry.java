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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.initialization.DefaultProjectDescriptor;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.build.BuildProjectRegistry;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.model.CalculatedModelValue;
import org.gradle.internal.model.ModelContainer;
import org.gradle.internal.resources.ProjectLeaseRegistry;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

public class DefaultProjectStateRegistry implements ProjectStateRegistry {
    private final WorkerLeaseService workerLeaseService;
    private final Object lock = new Object();
    private final Map<Path, ProjectStateImpl> projectsByPath = Maps.newLinkedHashMap();
    private final Map<ProjectComponentIdentifier, ProjectStateImpl> projectsById = Maps.newHashMap();
    private final Map<BuildIdentifier, DefaultBuildProjectRegistry> projectsByBuild = Maps.newHashMap();
    private final AtomicReference<Thread> ownerOfAllProjects = new AtomicReference<>();

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
            buildProjectRegistry = new DefaultBuildProjectRegistry(owner);
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

    private ProjectState addProject(BuildState owner, DefaultBuildProjectRegistry projectRegistry, DefaultProjectDescriptor descriptor) {
        Path projectPath = descriptor.path();
        Path identityPath = owner.calculateIdentityPathForProject(projectPath);
        String name = descriptor.getName();
        ProjectComponentIdentifier projectIdentifier = new DefaultProjectComponentIdentifier(owner.getBuildIdentifier(), identityPath, projectPath, name);
        IProjectFactory projectFactory = owner.getMutableModel().getServices().get(IProjectFactory.class);
        ProjectStateImpl projectState = new ProjectStateImpl(owner, identityPath, projectPath, descriptor.getName(), projectIdentifier, descriptor, projectFactory);
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

    @Override
    public void withMutableStateOfAllProjects(Runnable runnable) {
        withMutableStateOfAllProjects(Factories.toFactory(runnable));
    }

    @Override
    public <T> T withMutableStateOfAllProjects(Factory<T> factory) {
        if (!ownerOfAllProjects.compareAndSet(null, Thread.currentThread())) {
            // Already own all the projects
            if (ownerOfAllProjects.get() == Thread.currentThread()) {
                return factory.create();
            }
            throw new IllegalStateException(String.format("Another thread (%s) currently holds the state lock for all projects.", ownerOfAllProjects));
        }
        try {
            return factory.create();
        } finally {
            ownerOfAllProjects.set(null);
        }
    }

    @Override
    public void blocking(Runnable runnable) {
        Thread owner = ownerOfAllProjects.get();
        if (owner == Thread.currentThread()) {
            ownerOfAllProjects.set(null);
            try {
                runnable.run();
            } finally {
                ownerOfAllProjects.set(owner);
            }
        } else {
            workerLeaseService.blocking(runnable);
        }
    }

    @Override
    public <T> T allowUncontrolledAccessToAnyProject(Factory<T> factory) {
        return workerLeaseService.allowUncontrolledAccessToAnyProject(factory);
    }

    private static class DefaultBuildProjectRegistry implements BuildProjectRegistry {
        private final BuildState owner;
        private final Map<Path, ProjectStateImpl> projectsByPath = Maps.newLinkedHashMap();

        public DefaultBuildProjectRegistry(BuildState owner) {
            this.owner = owner;
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
    }

    private class ProjectStateImpl implements ProjectState {
        private final Path projectPath;
        private final String projectName;
        private final ProjectComponentIdentifier identifier;
        private final DefaultProjectDescriptor descriptor;
        private final IProjectFactory projectFactory;
        private final BuildState owner;
        private final Path identityPath;
        private final ResourceLock projectLock;
        private final Set<Thread> canDoAnythingToThisProject = new CopyOnWriteArraySet<>();
        private ProjectInternal project;

        ProjectStateImpl(BuildState owner, Path identityPath, Path projectPath, String projectName, ProjectComponentIdentifier identifier, DefaultProjectDescriptor descriptor, IProjectFactory projectFactory) {
            this.owner = owner;
            this.identityPath = identityPath;
            this.projectPath = projectPath;
            this.projectName = projectName;
            this.identifier = identifier;
            this.descriptor = descriptor;
            this.projectFactory = projectFactory;
            this.projectLock = workerLeaseService.getProjectLock(owner.getIdentityPath(), identityPath);
        }

        @Override
        public DisplayName getDisplayName() {
            if (projectPath.equals(Path.ROOT)) {
                return Describables.quoted("root project", projectName);
            }
            return Describables.of(identifier);
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

        @Override
        public Set<ProjectState> getChildProjects() {
            Set<ProjectState> children = new TreeSet<>(Comparator.comparing(ProjectState::getIdentityPath));
            for (ProjectDescriptor child : descriptor.getChildren()) {
                children.add(projectsByPath.get(identityPath.child(child.getName())));
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
        public void createMutableModel(ClassLoaderScope selfClassLoaderScope, ClassLoaderScope baseClassLoaderScope) {
            synchronized (this) {
                if (this.project != null) {
                    throw new IllegalStateException(String.format("The project object for project %s has already been attached.", getIdentityPath()));
                }

                ProjectInternal parent;
                if (descriptor.getParent() != null) {
                    // Identity path of parent can be different to identity path parent, if the names are tweaked in the settings file
                    // They should be exactly the same, always
                    Path parentPath = owner.calculateIdentityPathForProject(descriptor.getParent().path());
                    ProjectStateImpl parentState = projectsByPath.get(parentPath);
                    if (parentState == null) {
                        throw new IllegalStateException("Parent project " + parentPath + " is not registered for project " + identityPath);
                    }
                    parent = parentState.getMutableModel();
                } else {
                    parent = null;
                }
                this.project = projectFactory.createProject(owner.getMutableModel(), descriptor, this, parent, selfClassLoaderScope, baseClassLoaderScope);
            }
        }

        @Override
        public ProjectInternal getMutableModel() {
            synchronized (this) {
                if (project == null) {
                    throw new IllegalStateException(String.format("The project object for project %s has not been attached yet.", getIdentityPath()));
                }
                return project;
            }
        }

        @Override
        public void ensureConfigured() {
            synchronized (this) {
                getMutableModel().evaluate();
            }
        }

        @Override
        public void ensureTasksDiscovered() {
            synchronized (this) {
                ProjectInternal project = getMutableModel();
                project.evaluate();
                project.getTasks().discoverTasks();
                project.bindAllModelRules();
            }
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

            Thread currentOwner = ownerOfAllProjects.get();
            if (currentOwner != null) {
                if (currentOwner == currentThread) {
                    // we hold the lock for all projects, can run the function
                    return function.apply(getMutableModel());
                }
                throw new IllegalStateException(String.format("Cannot acquire state lock for %s as another thread (%s) currently holds the state lock for all projects.", project, currentOwner));
            }

            Collection<? extends ResourceLock> currentLocks = workerLeaseService.getCurrentProjectLocks();
            if (currentLocks.contains(projectLock)) {
                // if we already hold the project lock for this project
                if (currentLocks.size() == 1) {
                    // the lock for this project is the only lock we hold, can run the function
                    return function.apply(getMutableModel());
                } else {
                    currentLocks = Lists.newArrayList(currentLocks);
                    currentLocks.remove(projectLock);
                    // release any other project locks we might happen to hold
                    return workerLeaseService.withoutLocks(currentLocks, () -> function.apply(getMutableModel()));
                }
            } else {
                // we don't currently hold the project lock
                if (!currentLocks.isEmpty()) {
                    // we hold other project locks that we should release first
                    return workerLeaseService.withoutLocks(currentLocks, () -> withProjectLock(projectLock, function));
                } else {
                    // we just need to get the lock for this project
                    return withProjectLock(projectLock, function);
                }
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

        private <S> S withProjectLock(ResourceLock projectLock, final Function<? super ProjectInternal, ? extends S> function) {
            return workerLeaseService.withLocks(Collections.singleton(projectLock), () -> function.apply(getMutableModel()));
        }

        @Override
        public boolean hasMutableState() {
            Thread currentThread = Thread.currentThread();
            return canDoAnythingToThisProject.contains(currentThread) || workerLeaseService.isAllowedUncontrolledAccessToAnyProject() || ownerOfAllProjects.get() == currentThread || workerLeaseService.getCurrentProjectLocks().contains(projectLock);
        }

        @Override
        public <T> CalculatedModelValue<T> newCalculatedValue(@Nullable T initialValue) {
            return new CalculatedModelValueImpl<>(this, workerLeaseService, initialValue);
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
            projectLeaseRegistry.withoutProjectLock(lock::lock);
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
