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

import com.google.errorprone.annotations.concurrent.GuardedBy;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.initialization.ProjectDescriptorInternal;
import org.gradle.initialization.ProjectDescriptorRegistry;
import org.gradle.internal.Factory;
import org.gradle.internal.build.AllProjectsAccess;
import org.gradle.internal.build.BuildProjectRegistry;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.model.StateTransitionControllerFactory;
import org.gradle.internal.project.DefaultImmutableProjectDescriptor;
import org.gradle.internal.project.ImmutableProjectDescriptor;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;

public class DefaultProjectStateRegistry implements ProjectStateRegistry, Closeable {
    private final WorkerLeaseService workerLeaseService;
    private final Object lock = new Object();
    private final Map<Path, ProjectState> projectsByPath = new LinkedHashMap<>();
    private final Map<ProjectComponentIdentifier, ProjectState> projectsById = new HashMap<>();
    private final Map<BuildIdentifier, DefaultBuildProjectRegistry> projectsByBuild = new HashMap<>();

    public DefaultProjectStateRegistry(WorkerLeaseService workerLeaseService) {
        this.workerLeaseService = workerLeaseService;
    }

    @Override
    public void registerProjects(BuildState owner, ProjectDescriptorRegistry projectRegistry) {
        synchronized (lock) {
            DefaultBuildProjectRegistry buildProjectRegistry = getBuildProjectRegistry(owner);
            if (!buildProjectRegistry.projectsByPath.isEmpty()) {
                throw new IllegalStateException("Projects for " + owner.getDisplayName() + " have already been registered.");
            }

            ProjectDescriptorInternal rootProject = projectRegistry.getRootProject();
            if (rootProject == null) {
                throw new IllegalStateException("No root project found for " + owner.getDisplayName());
            }
            registerAllProjectsRecursively(owner, buildProjectRegistry, null, rootProject);
        }
    }

    @GuardedBy("lock")
    private ProjectIdentity registerAllProjectsRecursively(
        BuildState buildState,
        DefaultBuildProjectRegistry projectRegistry,
        @Nullable ProjectIdentity parentIdentity,
        ProjectDescriptorInternal descriptor
    ) {
        Path buildPath = buildState.getIdentityPath();
        ProjectIdentity identity = projectIdentityFor(buildPath, descriptor);

        List<ProjectIdentity> children = new ArrayList<>();
        for (ProjectDescriptorInternal childDescriptor : descriptor.children()) {
            ProjectIdentity childIdentity = registerAllProjectsRecursively(buildState, projectRegistry, identity, childDescriptor);
            children.add(childIdentity);
        }

        children.sort(Comparator.comparing(ProjectIdentity::getBuildTreePath));

        // TODO:isolated avoid preserving a reference to the original mutable descriptor
        // We avoid getting the build file early here, because otherwise it will be registered as a build-scoped configuration input,
        // which makes the project invalidation less-fine grained. See `ProjectScopedScriptResolution.resolveScriptsForProject(...)`
        Supplier<File> buildFileSupplier = Lazy.locking().of(descriptor::getBuildFile);
        ImmutableProjectDescriptor immutableDescriptor = new DefaultImmutableProjectDescriptor(
            identity, descriptor.getProjectDir(), buildFileSupplier, parentIdentity, children
        );

        addProject(buildState, projectRegistry, immutableDescriptor);

        return identity;
    }

    private static ProjectIdentity projectIdentityFor(Path buildPath, ProjectDescriptorInternal descriptor) {
        if (descriptor.path().equals(Path.ROOT)) {
            return ProjectIdentity.forRootProject(buildPath, descriptor.getName());
        } else {
            return ProjectIdentity.forSubproject(buildPath, descriptor.path());
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
    public ProjectState registerProject(BuildState owner, ImmutableProjectDescriptor projectDescriptor) {
        synchronized (lock) {
            DefaultBuildProjectRegistry buildProjectRegistry = getBuildProjectRegistry(owner);
            return addProject(owner, buildProjectRegistry, projectDescriptor);
        }
    }

    @Override
    public void discardProjectsFor(BuildState build) {
        DefaultBuildProjectRegistry registry = projectsByBuild.get(build.getBuildIdentifier());
        if (registry != null) {
            for (ProjectState project : registry.projectsByPath.values()) {
                projectsById.remove(project.getComponentIdentifier());
                projectsByPath.remove(project.getIdentityPath());
            }
            CompositeStoppable.stoppable(registry.projectsByPath.values()).stop();
            registry.projectsByPath.clear();
        }
    }

    @GuardedBy("lock")
    private ProjectState addProject(BuildState buildState, DefaultBuildProjectRegistry projectRegistry, ImmutableProjectDescriptor descriptor) {
        ServiceRegistry buildServices = buildState.getMutableModel().getServices();
        IProjectFactory projectFactory = buildServices.get(IProjectFactory.class);
        StateTransitionControllerFactory stateTransitionControllerFactory = buildServices.get(StateTransitionControllerFactory.class);
        DefaultProjectState projectState = new DefaultProjectState(buildState, descriptor, projectFactory, stateTransitionControllerFactory, buildServices, workerLeaseService, this);
        projectsByPath.put(descriptor.getIdentity().getBuildTreePath(), projectState);
        projectsById.put(projectState.getComponentIdentifier(), projectState);
        projectRegistry.add(descriptor.getIdentity().getProjectPath(), projectState);
        return projectState;
    }

    @Override
    public Collection<ProjectState> getAllProjects() {
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
            ProjectState projectState = projectsById.get(identifier);
            if (projectState == null) {
                throw new IllegalArgumentException(identifier.getDisplayName() + " not found.");
            }
            return projectState;
        }
    }

    @Override
    public ProjectState stateFor(Path identityPath) {
        synchronized (lock) {
            ProjectState projectState = projectsByPath.get(identityPath);
            if (projectState == null) {
                throw new IllegalArgumentException(identityPath.asString() + " not found.");
            }
            return projectState;
        }
    }

    @Override
    public @Nullable ProjectState findProject(Path identityPath) {
        synchronized (lock) {
            return projectsByPath.get(identityPath);
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
        private final Map<Path, ProjectState> projectsByPath = new LinkedHashMap<>();

        public DefaultBuildProjectRegistry(BuildState owner, WorkerLeaseService workerLeaseService) {
            this.owner = owner;
            this.workerLeaseService = workerLeaseService;
        }

        public void add(Path projectPath, DefaultProjectState projectState) {
            projectsByPath.put(projectPath, projectState);
        }

        @Override
        public ProjectState getRootProject() {
            return getProject(Path.ROOT);
        }

        @Override
        public ProjectState getProject(Path projectPath) {
            ProjectState projectState = projectsByPath.get(projectPath);
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
        public <T extends @Nullable Object> T fromMutableStateOfAllProjects(Function<AllProjectsAccess, T> factory) {
            ResourceLock allProjectsLock = workerLeaseService.getAllProjectsLock(owner.getIdentityPath());
            Collection<? extends ResourceLock> locks = workerLeaseService.getCurrentProjectLocks();
            return workerLeaseService.withReplacedLocks(locks, allProjectsLock, () ->
                factory.apply(new AllProjectsAccessImpl(owner, workerLeaseService, allProjectsLock))
            );
        }
    }

    private static final class AllProjectsAccessImpl implements AllProjectsAccess {
        private final BuildState owner;
        private final WorkerLeaseService workerLeaseService;
        private final ResourceLock allProjectsLock;

        private AllProjectsAccessImpl(BuildState owner, WorkerLeaseService workerLeaseService, ResourceLock allProjectsLock) {
            this.owner = owner;
            this.workerLeaseService = workerLeaseService;
            this.allProjectsLock = allProjectsLock;
        }

        @Override
        public ProjectInternal getMutableModel(ProjectState project) {
            if (!project.getOwner().getIdentityPath().equals(owner.getIdentityPath())) {
                throw new IllegalArgumentException(
                    "Attempting to access mutable state of " + project.getIdentityPath() + " using AllProjectsAccess for " + owner.getIdentityPath() + "." +
                        " AllProjectsAccess can only be used to access the mutable state of projects in the same build.");
            }
            if (!workerLeaseService.getCurrentProjectLocks().contains(allProjectsLock)) {
                throw new IllegalStateException("Cannot access mutable project state without holding the all projects lock for " + owner.getDisplayName() + ".");
            }
            // SAFETY: The caller is only allowed to call this method while holding the all projects lock
            return project.getMutableModel();
        }
    }
}
