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
import org.gradle.internal.resources.ResourceLock;
import org.gradle.util.Path;

import javax.annotation.Nullable;
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
    DisplayName getDisplayName();

    /**
     * Returns the containing build of this project.
     */
    BuildState getOwner();

    /**
     * Returns the parent of this project in the project tree. Note that this isn't the same as {@link Project#getParent()}.
     */
    @Nullable
    ProjectState getParent();

    /**
     * Returns the direct children of this project, in public iteration order.
     */
    Set<ProjectState> getChildProjects();

    /**
     * Returns the name of this project (which may not necessarily be unique).
     */
    String getName();

    /**
     * Returns an identifying path for this project in the build tree.
     */
    Path getIdentityPath();

    /**
     * Returns a path for this project within its containing build. These are not unique within a build tree.
     */
    Path getProjectPath();

    /**
     * Returns the project directory.
     */
    File getProjectDir();

    /**
     * Returns the identifier of the default component produced by this project.
     */
    ProjectComponentIdentifier getComponentIdentifier();

    /**
     * Creates the mutable model for this project.
     */
    void createMutableModel(ClassLoaderScope selfClassLoaderScope, ClassLoaderScope baseClassLoaderScope);

    /**
     * Configures the mutable model for this project, if not already.
     */
    void ensureConfigured();

    /**
     * Configure the mutable model for this project and discovers any registered tasks, if not already done.
     */
    void ensureTasksDiscovered();

    /**
     * Returns the mutable model for this project. This should not be used directly. This property is here to help with migration away from direct usage.
     */
    ProjectInternal getMutableModel();

    /**
     * Returns the lock that will be acquired when accessing the mutable state of this project via {@link #applyToMutableState(Consumer)} and {@link #fromMutableState(Function)}.
     * A caller can optionally acquire this lock before calling one of these accessor methods, in order to avoid those methods blocking.
     *
     * <p>Note that the lock may be shared between projects.
     */
    ResourceLock getAccessLock();
}
