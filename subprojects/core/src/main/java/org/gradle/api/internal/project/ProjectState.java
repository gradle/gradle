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

import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.internal.DisplayName;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.model.ModelContainer;
import org.gradle.internal.project.ImmutableProjectDescriptor;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.ThreadSafe;
import java.io.File;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Encapsulates the identity and state of a particular project in a build tree.
 */
@ThreadSafe
public interface ProjectState extends ModelContainer<ProjectInternal> {

    /**
 * The build that contains this project.
 *
 * @return the containing {@link BuildState}
 */
    BuildState getOwner();

    // region About this project

    /**
 * Provides the project's identity, including its position in the build tree and its owning build.
 *
 * @return the project's identity containing its build-tree path and owning build
 */
    ProjectIdentity getIdentity();

    /**
     * Provide the project's identity path within the build tree.
     *
     * @return the build-tree path that uniquely identifies this project
     */
    default Path getIdentityPath() {
        return getIdentity().getBuildTreePath();
    }

    /**
     * The project path within its containing build.
     *
     * @return the project's path inside the containing build; this path is not guaranteed to be unique within a build tree — use {@link #getIdentityPath()} for a globally unique build-tree path
     */
    default Path getProjectPath() {
        return getIdentity().getProjectPath();
    }

    /**
     * The project's name.
     *
     * @return the project name; may not be unique
     */
    default String getName() {
        return getIdentity().getProjectName();
    }

    /**
     * The project's nesting level within its multi-project hierarchy.
     *
     * 0 indicates the root project, 1 indicates a direct child of the root, and so on.
     * The value is computed per-build from the project's project path.
     *
     * @return the nesting level (0 for root, 1 for direct children, etc.)
     */
    default int getDepth() {
        return getProjectPath().segmentCount();
    }

    /**
     * Provide the display name for this project.
     *
     * @return the project's display name, represented by this project's identity
     */
    default DisplayName getDisplayName() {
        return getIdentity();
    }

    /**
 * The directory that contains the project's sources and build files.
 *
 * @return the project's base directory on the filesystem
 */
    File getProjectDir();

    /**
 * The identifier of the default software component produced by this project.
 *
 * @return the ProjectComponentIdentifier for the project's default component
 */
    ProjectComponentIdentifier getComponentIdentifier();

    /**
 * Accesses the immutable descriptor for this project.
 *
 * @return the project's immutable descriptor
 */
    ImmutableProjectDescriptor getDescriptor();

    // endregion

    // region Project hierarchy

    /**
     * The parent project in the build tree.
     *
     * @return the parent ProjectState, or {@code null} if this project is a root project
     */
    @Nullable
    ProjectState getParent();

    /**
 * Provides the direct child projects in a defined public iteration order.
 *
 * @return the direct child projects in a defined public iteration order
 */
    Set<ProjectState> getChildProjects();

    /**
 * Direct children of this project in no particular order.
 *
 * @return an Iterable of direct child {@link ProjectState} instances; iteration order is not defined
 */
    Iterable<ProjectState> getUnorderedChildProjects();

    /**
 * Indicates whether the project has direct child projects.
 *
 * @return {@code true} if the project has one or more direct child projects, {@code false} otherwise.
 */
    boolean hasChildren();

    // endregion

    // region Lifecycle management

    /**
     * Is the mutable model for this project available?
     */
    boolean isCreated();

    /**
     * Creates the mutable model for this project.
     */
    void createMutableModel(ClassLoaderScope selfClassLoaderScope, ClassLoaderScope baseClassLoaderScope);

    /**
     * Configures the mutable model for this project, if not already.
     *
     * May also configure the parent of this project.
     */
    void ensureConfigured();

    /**
     * Configures the mutable model for this project, if not already.
     *
     * @throws IllegalStateException when the parent of this model is not already configured.
     */
    void ensureSelfConfigured();

    /**
 * Ensures this project's tasks have been discovered.
 *
 * If task discovery has already been performed for this project, this method does nothing.
 */
    void ensureTasksDiscovered();

    // endregion

    /**
     * Returns the mutable model for this project. This should not be used directly. This property is here to help with migration away from direct usage.
     * If you are using the mutable state of many projects, consider using one of the methods from {@link org.gradle.internal.build.BuildProjectRegistry}
     * that provide {@link org.gradle.internal.build.AllProjectsAccess} instead, which checks the lock state.
     */
    ProjectInternal getMutableModel();

    /**
     * Returns the mutable model for this project, just as {@link #getMutableModel()}, but works even in the presence of failures, for example when the project failed to configure.
     *
     * Should not be used in general, it's specific to obtaining partial TAPI models in the presence of failures.
     */
    ProjectInternal getMutableModelEvenAfterFailure();

    /**
     * Returns the lock that will be acquired when accessing the mutable state of this project via {@link #applyToMutableState(Consumer)} and {@link #fromMutableState(Function)}.
     * A caller can optionally acquire this lock before calling one of these accessor methods, in order to avoid those methods blocking.
     *
     * <p>When parallel execution is disabled, the lock is shared between projects within a build, and each build in the build tree has its own shared lock.
     */
    ResourceLock getAccessLock();

    /**
     * Returns the lock that should be acquired by non-isolated tasks from this project prior to execution.
     *
     * <p>This lock allows both access to the project state and the right to execute as a task. Acquiring this lock also acquires the lock returned by {@link #getAccessLock()}.
     *
     * <p>When a task reaches across project boundaries, the project state lock is released but the task execution lock is not. This prevents other tasks from the same project from starting but
     * allows tasks in other projects to access the state of this project without deadlocks in cases where there are dependency cycles between projects. It also allows other non-taask work
     * to run while the task is blocked.
     *
     * <p>When parallel execution is not enabled, the lock is shared between projects within a build, and each build in the build tree has its own shared lock.
     */
    ResourceLock getTaskExecutionLock();
}
