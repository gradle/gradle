/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.language;

import org.gradle.api.Incubating;
import org.gradle.language.base.LanguageSourceSet;

import java.util.Collection;
import java.util.Map;

/**
 * A source set that depends on one or more {@link org.gradle.nativebinaries.NativeDependencySet}s to be built.
 */
@Incubating
public interface DependentSourceSet extends LanguageSourceSet {
    /**
     * The libraries that this source set requires.
     */
    Collection<?> getLibs();

    /**
     * Adds a library that this source set requires. This method accepts the following types:
     *
     * <ul>
     *     <li>A {@link org.gradle.nativebinaries.Library}</li>
     *     <li>A {@link org.gradle.nativebinaries.NativeDependencySet}</li>
     *     <li>A {@link java.util.Map} containing the library selector.</li>
     * </ul>
     *
     * The Map notation supports the following String attributes:
     *
     * <ul>
     *     <li>project: the path to the project containing the library (optional, defaults to current project)</li>
     *     <li>library: the name of the library (required)</li>
     *     <li>linkage: the library linkage required ['shared'/'static'] (optional, defaults to 'shared')</li>
     * </ul>
     */
    void lib(Object library);

    /**
     * Add a dependency to this source set.
     */
    void dependency(Map<?, ?> dep);
}
