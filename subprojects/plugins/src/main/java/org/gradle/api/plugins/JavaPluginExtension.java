/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.JavaVersion;

/**
 * Common configuration for Java based projects. This is added by the {@link JavaBasePlugin}.
 *
 * @since 4.10
 */
@Incubating
public interface JavaPluginExtension {
    /**
     * Returns the source compatibility used for compiling Java sources.
     */
    JavaVersion getSourceCompatibility();

    /**
     * Sets the source compatibility used for compiling Java sources.
     *
     * @param value The value for the source compatibility
     */
    void setSourceCompatibility(JavaVersion value);

    /**
     * Returns the target compatibility used for compiling Java sources.
     */
    JavaVersion getTargetCompatibility();

    /**
     * Sets the target compatibility used for compiling Java sources.
     *
     * @param value The value for the target compatibility
     */
    void setTargetCompatibility(JavaVersion value);

    /**
     * Registers a feature.
     * @param name the name of the feature
     * @param configureAction the configuration for the feature
     *
     * @since 5.3
     */
    void registerFeature(String name, Action<? super FeatureSpec> configureAction);

    /**
     * If this method is called, Gradle will not automatically try to fetch
     * dependencies which have a JVM version compatible with the target compatibility
     * of this module. This should be used whenever the default behavior is not
     * applicable, in particular when for some reason it's not possible to split
     * a module and that this module only has some classes which require dependencies
     * on higher versions.
     *
     * @since 5.3
     */
    @Incubating
    void disableAutoTargetJvm();

    /**
     * If this method is called, a new source set will be created for test fixtures.
     * This source set will automatically depend on the main component, and the tests
     * will also automatically depend on test fixtures.
     *
     * Consumers of this project may use this test fixtures by calling the {@link #usesTestFixturesOf(Object)} method
     *
     * @since 5.6
     */
    @Incubating
    void enableTestFixtures();

    /**
     * Indicates that this project uses the test fixtures of another project or external module.
     * It is assumed that those test fixtures were enabled using the {@link #enableTestFixtures()} method
     * of the producer.
     *
     * @since 5.6
     */
    @Incubating
    void usesTestFixturesOf(Object notation);
}
