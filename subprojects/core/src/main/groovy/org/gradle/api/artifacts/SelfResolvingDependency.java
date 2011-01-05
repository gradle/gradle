/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.Buildable;

import java.io.File;
import java.util.Set;

/**
 * A {@code SelfResolvingDependency} is a {@link Dependency} which is able to resolve itself, independent of a
 * repository.
 */
public interface SelfResolvingDependency extends Dependency, Buildable {
    /**
     * Resolves this dependency. A {@link org.gradle.api.artifacts.ProjectDependency} is resolved with transitive equals true
     * by this method. 
     *
     * @return The files which make up this dependency.
     * @see #resolve(boolean) 
     */
    Set<File> resolve();

    /**
     * Resolves this dependency by specifying the transitive mode. This mode has only an effect if the self resolved dependency
     * is of type {@link org.gradle.api.artifacts.ProjectDependency}. In this case, if transitive is <code>false</code>,
     * only the self resolving dependencies of the project configuration which are no project dependencies are resolved. If transitive
     * is set to true, other project dependencies belonging to the configuration of the resolved project dependency are
     * resolved recursively. 
     *
     * @param transitive Whether to resolve transitively. Has only an effect on a {@link org.gradle.api.artifacts.ProjectDependency} 
     * @return The files which make up this dependency.
     */
    Set<File> resolve(boolean transitive);
}
