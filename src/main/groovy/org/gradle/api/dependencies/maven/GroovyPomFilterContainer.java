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
package org.gradle.api.dependencies.maven;

import groovy.lang.Closure;

/**
 * @author Hans Dockter
 */
public interface GroovyPomFilterContainer extends PomFilterContainer {
    /**
     * Adds a publish filter.
     *
     * @param name   The name of the filter
     * @param filter The filter
     * @return The Maven pom associated with the closure
     * @see PublishFilter
     * @see PomFilterContainer#addFilter(String, org.gradle.api.dependencies.maven.PublishFilter)
     */
    MavenPom addFilter(String name, Closure filter);

    /**
     * Sets the default publish filter.
     *
     * @param filter The filter to be set
     * @see PublishFilter
     * @see PomFilterContainer#setFilter(org.gradle.api.dependencies.maven.PublishFilter)
     */
    void filter(Closure filter);

    /**
     * Configures a pom by a closure. The closure statements are delegated to the pom object associated with the given name.
     *
     * @param name
     * @param configureClosure
     * @return The pom object associated with the given name.
     * @see PomFilterContainer#pom(String)
     */
    MavenPom pom(String name, Closure configureClosure);

    /**
     * Configures the default pom by a closure. The closure statements are delegated to the default pom.
     *
     * @param configureClosure
     * @return The default pom.
     * @see PomFilterContainer#getPom()
     */
    MavenPom pom(Closure configureClosure);
}
