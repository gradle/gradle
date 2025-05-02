/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.Action;

import java.util.Collection;
import java.util.Map;

/**
 * Describes metadata about a dependency - direct dependencies or dependency constraints - declared in a resolved component's metadata.
 *
 * @param <T> type of the dependency metadata in this collection
 * @since 4.4
 */
public interface DependenciesMetadata<T extends DependencyMetadata> extends Collection<T> {

    /**
     * Add a dependency or a dependency constraint using the string notation: <code><i>group</i>:<i>name</i>:<i>version</i></code>.
     *
     * @param dependencyNotation the dependency
     */
    void add(String dependencyNotation);

    /**
     * Add a dependency or a dependency constraint using the map notation: <code>group: <i>group</i>, name: <i>name</i>, version: <i>version</i></code>.
     *
     * @param dependencyNotation the dependency
     */
    void add(Map<String, String> dependencyNotation);

    /**
     * Add a dependency or a dependency constraint using the string notation: <code><i>group</i>:<i>name</i>:<i>version</i></code>.
     *
     * @param dependencyNotation the dependency
     * @param configureAction action to configure details of the dependency - see {@link DependencyMetadata}
     */
    void add(String dependencyNotation, Action<? super T> configureAction);

    /**
     * Add a dependency or a dependency constraint using the map notation: <code>group: <i>group</i>, name: <i>name</i>, version: <i>version</i></code>.
     *
     * @param dependencyNotation the dependency
     * @param configureAction action to configure details of the dependency - see {@link DependencyMetadata}
     */
    void add(Map<String, String> dependencyNotation, Action<? super T> configureAction);
}
