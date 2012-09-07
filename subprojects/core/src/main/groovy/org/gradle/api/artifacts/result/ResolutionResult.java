/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.artifacts.result;

import org.gradle.api.Incubating;

/**
 * Contains the information about the resolution result.
 * Gives access to the resolved dependency graph.
 * In future it will contain more convenience methods and
 * other useful information about the resolution results.
 */
@Incubating
public interface ResolutionResult {

    /**
     * Gives access to the resolved dependency graph.
     * You can walk the graph recursively from the root to obtain information about resolved dependencies.
     * For example, Gradle's built-in 'dependencies' uses it to render the dependency tree.
     *
     * @return the root node of the resolved dependency graph
     */
    ResolvedModuleVersionResult getRoot();
}
