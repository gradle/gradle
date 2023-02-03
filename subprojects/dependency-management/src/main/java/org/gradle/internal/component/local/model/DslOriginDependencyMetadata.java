/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.component.local.model;

import org.gradle.api.artifacts.Dependency;
import org.gradle.internal.component.model.DependencyMetadata;

/**
 * A dependency declared using the dependency DSL.
 */
public interface DslOriginDependencyMetadata extends DependencyMetadata {

    /**
     * Provide access to the source `Dependency` instance for this `DependencyMetadata`.
     * This is used for:
     * - Accessing the `ClientModule` instance for a 'client module' dependency.
     * - Binding the source dependency to the first-order resolved components in `ResolvedConfiguration`.
     *
     * The goal is to eventually replace these uses, and remove this type.
     */
    Dependency getSource();
}
