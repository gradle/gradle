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

import org.gradle.api.dependencies.maven.MavenPom;

import groovy.lang.Closure;

/**
 * Adds Groovy configuration convenience methods on top of the {@link MavenUploader}.
 *
 * This class provides also a builder for repository and snapshot-repository:
 *
 * <pre>
 * mavenUploader.repository(url: 'file://repoDir') {
 *    authentication(userName: 'myName')
 *    releases(updatePolicy: 'never')
 *    snapshots(updatePolicy: 'always')
 * }
 * </pre>
 *
 * This call set the repository object and also returns an instance of this object. If you use 'snapshotRepository'
 * instead of repository, the snapshot repository is build.
 *
 * @author Hans Dockter
 * @see org.gradle.api.dependencies.maven.MavenUploader
 */
public interface GroovyMavenUploader extends MavenUploader {
    /**
     * Adds a publish filter.
     *
     * @param name   The name of the filter
     * @param filter The filter
     * @return The Maven pom associated with the closure
     * @see org.gradle.api.dependencies.maven.PublishFilter
     * @see org.gradle.api.dependencies.maven.MavenUploader#addFilter(String, PublishFilter)
     */
    MavenPom addFilter(String name, Closure filter);

    /**
     * Sets the default publish filter.
     *
     * @param filter The filter to be set
     * @see org.gradle.api.dependencies.maven.PublishFilter
     * @see org.gradle.api.dependencies.maven.MavenUploader#setFilter(PublishFilter)
     */
    void filter(Closure filter);

    /**
     * Configures a pom by a closure. The closure statements are delegated to the pom object associated with the given name.
     *
     * @param name
     * @param configureClosure
     * @return The pom object associated with the given name.
     * @see org.gradle.api.dependencies.maven.MavenUploader#pom(String)
     */
    MavenPom pom(String name, Closure configureClosure);

    /**
     * Configures the default pom by a closure. The closure statements are delegated to the default pom.
     *
     * @param configureClosure
     * @return The default pom.
     * @see MavenUploader#getPom() 
     */
    MavenPom pom(Closure configureClosure);
}