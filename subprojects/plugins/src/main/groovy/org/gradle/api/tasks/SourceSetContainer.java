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
package org.gradle.api.tasks;

import groovy.lang.Closure;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectSet;

/**
 * A {@code SourceSetContainer} manages a set of {@link SourceSet} objects.
 */
public interface SourceSetContainer extends NamedDomainObjectContainer<SourceSet>, NamedDomainObjectSet<SourceSet> {
    /**
     * Adds a source set with the given name.
     *
     * @param name The name of the new source set.
     * @return The newly added source set.
     * @throws org.gradle.api.InvalidUserDataException when a source set with the given name already exists in this container.
     * @deprecated use {@link #create(String)} instead
     */
    @Deprecated
    SourceSet add(String name) throws InvalidUserDataException;

    /**
     * Adds a source set with the given name. The given configuration closure is executed against the source set
     * before it is returned from this method.
     *
     * @param name The name of the new source set.
     * @param configureClosure The closure to use to configure the source set.
     * @return The newly added source set.
     * @throws InvalidUserDataException when a source set with the given name already exists in this container.
     * @deprecated use {@link #create(String, groovy.lang.Closure)} instead
     */
    @Deprecated
    SourceSet add(String name, Closure configureClosure) throws InvalidUserDataException;
}
