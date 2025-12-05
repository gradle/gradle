/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.project;

import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.internal.scripts.ScriptFileResolver;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * An immutable counterpart of the {@link org.gradle.api.initialization.ProjectDescriptor},
 * which should be used after the settings phase is completed and project hierarchy is no longer mutable.
 */
public interface ImmutableProjectDescriptor {

    /**
     * Identity of the project represented by this descriptor.
     */
    ProjectIdentity getIdentity();

    /**
     * The project directory.
     */
    File getProjectDir();

    /**
     * Build file associated with this project.
     * <p>
     * Note that there can be no build file on disk, in which case we assume
     * {@link org.gradle.internal.initialization.BuildLogicFiles#DEFAULT_BUILD_FILE} with empty content.
     *
     * @see org.gradle.internal.scripts.ScriptFileUtil#resolveBuildFile(File, ScriptFileResolver)
     */
    File getBuildFile();

    /**
     * Parent project identity, or {@code null} if this is a root project of the build.
     */
    @Nullable
    ProjectIdentity getParent();

    /**
     * Immutable list of children, sorted by their identity.
     */
    List<ProjectIdentity> getChildren();
}
