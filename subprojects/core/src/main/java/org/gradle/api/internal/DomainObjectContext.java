/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.model.ModelContainer;
import org.gradle.util.Path;

import javax.annotation.Nullable;

/**
 * Represents a position in the tree of builds/projects.
 */
public interface DomainObjectContext {

    /**
     * Creates a path from the root of the build tree to the current context + name.
     */
    Path identityPath(String name);

    /**
     * Creates a path from the root of the project tree to the current context + name.
     */
    Path projectPath(String name);

    /**
     * If this context represents a project, its path.
     */
    @Nullable
    Path getProjectPath();

    /**
     * If this context represents a project, the project.
     */
    @Nullable
    ProjectInternal getProject();

    /**
     * The container that holds the model for this context, to allow synchronized access to the model.
     */
    ModelContainer<?> getModel();

    /**
     * The path to the build that is associated with this object.
     */
    Path getBuildPath();

    /**
     * Whether the context is a script.
     *
     * Some objects are associated with a script, that is associated with a domain object.
     */
    boolean isScript();

    /**
     * Whether the context is a root script.
     *
     * `Settings` is such a context.
     *
     * Some objects are associated with a script, that is associated with a domain object.
     */
    boolean isRootScript();

    /**
     * Indicates if the context is plugin resolution
     */
    boolean isPluginContext();

    /**
     * Returns true if the context represents a detached state, for
     * example detached dependency resolution
     */
    default boolean isDetachedState() {
        return false;
    }
}
