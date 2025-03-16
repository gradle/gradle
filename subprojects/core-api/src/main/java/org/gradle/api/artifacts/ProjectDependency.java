/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.artifacts;

import org.gradle.api.Project;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.HasInternalProtocol;

import java.io.File;
import java.util.Set;

/**
 * <p>A {@code ProjectDependency} is a {@link Dependency} on another project in the current project hierarchy.</p>
 */
@HasInternalProtocol
@SuppressWarnings("deprecation") // Because of SelfResolvingDependency
public interface ProjectDependency extends ModuleDependency, SelfResolvingDependency {

    /**
     * Get the path to the project that this dependency refers to relative to its owning build.
     *
     * @see Project#getPath()
     *
     * @since 8.11
     */
    String getPath();

    /**
     * Returns the project associated with this project dependency.
     *
     * @deprecated This method will be removed in Gradle 9.0. Accessing the mutable
     * state of other projects should be avoided.
     */
    @Deprecated
    Project getDependencyProject();

    /**
     * {@inheritDoc}
     */
    @Override
    ProjectDependency copy();

    /**
     * {@inheritDoc}
     *
     * @deprecated This class will no longer implement {@link SelfResolvingDependency} in Gradle 9.0
     */
    @Override
    @Deprecated
    TaskDependency getBuildDependencies();

    /**
     * {@inheritDoc}
     *
     * @deprecated This class will no longer implement {@link SelfResolvingDependency} in Gradle 9.0
     */
    @Override
    @Deprecated
    Set<File> resolve();

    /**
     * {@inheritDoc}
     *
     * @deprecated This class will no longer implement {@link SelfResolvingDependency} in Gradle 9.0
     */
    @Override
    @Deprecated
    Set<File> resolve(boolean transitive);
}
