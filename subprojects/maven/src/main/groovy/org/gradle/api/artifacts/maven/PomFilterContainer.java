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
package org.gradle.api.artifacts.maven;

import groovy.lang.Closure;
import org.gradle.api.publication.maven.internal.PomFilter;

/**
 * Manages a set of {@link MavenPom} instances and their associated {@link PublishFilter} instances.
 */
public interface PomFilterContainer {
    String DEFAULT_ARTIFACT_POM_NAME = "default";

    /**
     * Returns the default filter being used. .
     *
     * @see #setFilter(org.gradle.api.artifacts.maven.PublishFilter)
     */
    PublishFilter getFilter();

    /**
     * <p>Sets the default filter to be used. This filter is active if no custom filters have been added (see {@link #addFilter(String, org.gradle.api.artifacts.maven.PublishFilter)}).
     * If at least one custom filter has been added the default filter is not used any longer.</p>
     * <p>The default for this property is {@link PublishFilter#ALWAYS_ACCEPT}.
     * If there is only one artifact you are fine with this filter. If there is more than one artifact, deployment will lead to
     * an exception, if you don't specify a filter that selects the artifact to deploy. If you want to deploy more than one artifact you have
     * to use the (see {@link #addFilter(String, org.gradle.api.artifacts.maven.PublishFilter)} method.</p>
     *
     * @param defaultFilter
     * @see #getFilter()
     */
    void setFilter(PublishFilter defaultFilter);

    /**
     * Returns the POM property of the custom filter.
     * The POM property can be used to customize the POM generation. By default the properties of such a POM object are all null.
     * Null means that Gradle will use default values for generating the Maven POM. Those default values are derived from the deployable artifact
     * and from the project type (e.g. java, war, ...). If you explicitly set a POM property, Gradle will use those instead.
     *
     * @return The Maven Pom
     */
    MavenPom getPom();

    /**
     * <p>Sets the default POM to be used. This POM is active if no custom filters have been added (see {@link #addFilter(String, org.gradle.api.artifacts.maven.PublishFilter)}).
     * If at least one custom filter has been added the default POM is not used any longer.</p>
     * <p>Usually you don't need to set this property as the default value provides you a POM object you might use for configuration.
     * By default the properties of such a POM object are all null.
     * If they are null, Gradle will use default values for generating the Maven POM. Those default values are derived from the deployable artifact
     * and from the project type (e.g. java, war, ...). If you explicitly set a POM property, Gradle will use this instead.</p>
     *
     * @param defaultPom
     */
    void setPom(MavenPom defaultPom);

    /**
     * If you want to deploy more than one artifact you need to define filters to select each of those artifacts. The method
     * returns a POM object associated with this filter, that allows you to customize the POM generation for the artifact selected
     * by the filter.
     *
     * @param name The name of the filter
     * @param publishFilter The filter to use
     * @return The POM associated with the filter
     */
    MavenPom addFilter(String name, PublishFilter publishFilter);

    /**
     * Adds a publish filter.
     *
     * @param name   The name of the filter
     * @param filter The filter
     * @return The Maven POM associated with the closure
     * @see PublishFilter
     * @see PomFilterContainer#addFilter(String, org.gradle.api.artifacts.maven.PublishFilter)
     */
    MavenPom addFilter(String name, Closure filter);

    /**
     * Returns a filter added with {@link #addFilter(String, org.gradle.api.artifacts.maven.PublishFilter)}.
     *
     * @param name The name of the filter
     */
    PublishFilter filter(String name);

    /**
     * Sets the default publish filter.
     *
     * @param filter The filter to be set
     * @see PublishFilter
     * @see PomFilterContainer#setFilter(org.gradle.api.artifacts.maven.PublishFilter)
     */
    void filter(Closure filter);

    /**
     * Returns the POM associated with a filter added with {@link #addFilter(String, org.gradle.api.artifacts.maven.PublishFilter)}.
     *
     * @param name The name of the filter.
     */
    MavenPom pom(String name);

    /**
     * Configures a POM by a closure. The closure statements are delegated to the POM object associated with the given name.
     *
     * @param name
     * @param configureClosure
     * @return The POM object associated with the given name.
     * @see PomFilterContainer#pom(String)
     */
    MavenPom pom(String name, Closure configureClosure);

    /**
     * Configures the default POM by a closure. The closure statements are delegated to the default POM.
     *
     * @param configureClosure
     * @return The default POM.
     * @see PomFilterContainer#getPom()
     */
    MavenPom pom(Closure configureClosure);

    Iterable<PomFilter> getActivePomFilters();
}
