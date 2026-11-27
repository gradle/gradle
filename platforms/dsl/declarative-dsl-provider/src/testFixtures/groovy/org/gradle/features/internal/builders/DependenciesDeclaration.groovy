/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.internal.builders

/**
 * Describes a dependencies block within a definition, containing named {@code DependencyCollector} accessors.
 *
 * <p>When declared on a definition, generates a nested {@code Dependencies} interface with
 * one {@code DependencyCollector get<Name>()} method per declared collector.</p>
 */
class DependenciesDeclaration {
    /** The generated interface name (default: "LibraryDependencies"). */
    String interfaceName = "LibraryDependencies"

    /** The list of dependency collector names (e.g., "api", "implementation"). */
    List<String> collectors = []

    /** Declares a named dependency collector. */
    void dependencyCollector(String name) {
        collectors.add(name)
    }
}
