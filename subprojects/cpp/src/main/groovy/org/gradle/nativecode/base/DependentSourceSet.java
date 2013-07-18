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
package org.gradle.nativecode.base;

import java.util.Collection;
import java.util.Map;

/**
 * A source set that depends on one or more {@link NativeDependencySet}s to be built.
 */
public interface DependentSourceSet {
    /**
     * The libraries that this source set requires.
     */
    Collection<NativeDependencySet> getLibs();

    /**
     * Adds a library that this source set requires. This method accepts the following types:
     *
     * <ul>
     *     <li>A {@link Library}</li>
     *     <li>A {@link LibraryBinary}</li>
     *     <li>A {@link NativeDependencySet}</li>
     * </ul>
     */
    void lib(Object library);

    /**
     * Add a dependency to this source set.
     */
    void dependency(Map<?, ?> dep);
}
