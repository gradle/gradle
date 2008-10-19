/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.api.dependencies;

import java.util.Set;
import java.util.List;
import java.io.File;

/**
 * <p>A {@code Configuration} represents a group of artifacts and their dependencies.</p>
 */
public interface Configuration {
    /**
     * Returns the name of this configuration.
     *
     * @return The configuration name, never null.
     */
    String getName();

    /**
     * Returns true if this is a private configuration. A private configuration is not visible outside the project it
     * belongs to.
     *
     * @return true if this is a private configuration.
     */
    boolean isPrivate();

    /**
     * Sets the visibility of this configuration. When private is set to true, this configuration is not visibile
     * outside the project it belongs to.
     *
     * @param p true if this is a private configuration
     */
    void setPrivate(boolean p);

    /**
     * Returns the names of the configurations which this configuration extends. The artifacts of the super
     * configurations are also available in this configuration.
     *
     * @return The super configurations. Returns an empty set when this configuration does not extend any others.
     */
    Set<String> getExtendsConfiguration();

    /**
     * Sets the configurations which this configuration extends.
     *
     * @param superConfigs The super configuration. Should not be null.
     */
    void setExtendsConfiguration(Set<String> superConfigs);

    void extendsConfiguration(String[] superConfigs);

    boolean isTransitive();

    void setTransitive(boolean t);

    /**
     * Resolves this configuration. This locates and downloads the files which make up this configuration, and returns
     * the resulting set of files.
     *
     * @return The files of this configuration.
     */
    Set<File> resolve();

    /**
     * Resolves this configuration as a path.
     *
     * @return The files of this configuration as a path.
     */
    String asPath();
}
