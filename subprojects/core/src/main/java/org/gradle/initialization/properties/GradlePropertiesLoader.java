/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.initialization.properties;

import org.gradle.StartParameter;
import org.gradle.api.internal.StartParameterInternal;

import java.io.File;
import java.util.Map;

/**
 * Loads Gradle properties from various sources including files, system properties, and environment variables.
 * <p>
 * This interface provides methods to load properties from different locations in the Gradle build environment:
 * <ul>
 * <li>Gradle installation directory ({@code <Gradle Home>/gradle.properties})</li>
 * <li>Gradle user home directory ({@code <Gradle User Home>/gradle.properties})</li>
 * <li>Arbitrary directories containing {@code gradle.properties} files</li>
 * <li>System properties with the {@value #SYSTEM_PROJECT_PROPERTIES_PREFIX} prefix</li>
 * <li>Environment variables with the {@value #ENV_PROJECT_PROPERTIES_PREFIX} prefix</li>
 * <li>Start parameter project properties (e.g., {@code -P} command line arguments)</li>
 * </ul>
 * <p>
 * The loader is responsible for extracting properties from these various sources and returning them
 * as key-value maps. Property name prefixes are automatically stripped when loading from system
 * properties and environment variables to provide clean property names.
 * <p>
 * This interface is typically used by {@link org.gradle.api.internal.properties.GradlePropertiesController}
 * to compose the final property resolution hierarchy for builds and projects.
 *
 * @see org.gradle.api.internal.properties.GradlePropertiesController
 * @see org.gradle.api.internal.properties.GradleProperties
 */
public interface GradlePropertiesLoader {

    /**
     * The prefix used to identify project properties in system properties.
     * <p>
     * System properties with this prefix are considered project properties and the prefix
     * is stripped from the property name when loaded.
     */
    String SYSTEM_PROJECT_PROPERTIES_PREFIX = "org.gradle.project.";

    /**
     * The prefix used to identify project properties in environment variables.
     * <p>
     * Environment variables with this prefix are considered project properties and the prefix
     * is stripped from the property name when loaded.
     */
    String ENV_PROJECT_PROPERTIES_PREFIX = "ORG_GRADLE_PROJECT_";

    /**
     * Loads properties from the {@code gradle.properties} file in the Gradle installation directory.
     *
     * @return a map of property names to values, or an empty map if no properties file exists
     * @see StartParameterInternal#getGradleHomeDir()
     */
    Map<String, String> loadFromGradleHome();

    /**
     * Loads properties from the {@code gradle.properties} file in the Gradle user home directory.
     *
     * @return a map of property names to values, or an empty map if no properties file exists
     * @see StartParameter#getGradleUserHomeDir()
     */
    Map<String, String> loadFromGradleUserHome();

    /**
     * Loads properties from the {@code gradle.properties} file in the specified directory.
     * <p>
     * This method is used to load properties from build root directories, project directories,
     * or any other directory that may contain a {@code gradle.properties} file.
     *
     * @param dir the directory to look for the {@code gradle.properties} file in
     * @return a map of property names to values, or an empty map if no properties file exists
     */
    Map<String, String> loadFrom(File dir);

    /**
     * Loads project properties from system properties.
     * <p>
     * Only system properties that start with the {@value #SYSTEM_PROJECT_PROPERTIES_PREFIX}
     * prefix are considered. The prefix is stripped from the property names in the returned map.
     * <p>
     * For example, a system property {@code org.gradle.project.myProp=value} would be
     * returned as {@code myProp=value}.
     *
     * @return a map of project property names to values, with prefixes removed
     */
    Map<String, String> loadFromSystemProperties();

    /**
     * Loads project properties from environment variables.
     * <p>
     * Only environment variables that start with the {@value #ENV_PROJECT_PROPERTIES_PREFIX}
     * prefix are considered. The prefix is stripped from the property names in the returned map.
     * <p>
     * For example, an environment variable {@code ORG_GRADLE_PROJECT_myProp=value} would be
     * returned as {@code myProp=value}.
     *
     * @return a map of project property names to values, with prefixes removed
     */
    Map<String, String> loadFromEnvironmentVariables();

    /**
     * Loads project properties from the start parameter.
     * <p>
     * These are properties that were explicitly passed via command line arguments such as
     * {@code -P} options or {@code --project-prop} flags.
     * <p>
     * For example, {@code gradle build -PmyProp=value} would result in {@code myProp=value}
     * being included in the returned map.
     *
     * @return a map of project property names to values from command line arguments
     * @see StartParameter#getProjectProperties()
     */
    Map<String, String> loadFromStartParameterProjectProperties();

}
