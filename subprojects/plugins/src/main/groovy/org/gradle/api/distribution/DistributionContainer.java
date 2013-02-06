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
package org.gradle.api.distribution;

import groovy.lang.Closure;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectContainer;

/**
 * A {@code DistributionContainer} manages a set of {@link Distribution} objects.
 */
@Incubating
public interface DistributionContainer extends NamedDomainObjectContainer<Distribution> {

    /**
     * Adds a distribution with the given name.
     *
     * @param name The name of the new distribution.
     * @return The newly added distribution.
     * @throws org.gradle.api.InvalidUserDataException when a distribution with the given name already exists in this container.
     */
    public Distribution add(String name) throws InvalidUserDataException;

    /**
     * Adds a distribution with the given name. The given configuration closure is executed against the distribution
     * before it is returned from this method.
     *
     * @param name The name of the new distribution.
     * @param configureClosure The closure to use to configure the distribution.
     * @return The newly added distribution.
     * @throws InvalidUserDataException when a distribution with the given name already exists in this container.
     */
    public Distribution add(String name, Closure configureClosure) throws InvalidUserDataException;
}
