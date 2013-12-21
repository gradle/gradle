/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.api.artifacts;

/**
 * A {@code Dependency} represents a dependency on the artifacts from a particular source. A source can be an Ivy
 * module, a Maven POM, another Gradle project, a collection of Files, etc... A source can have zero or more artifacts.
 */
public interface Dependency {
    String DEFAULT_CONFIGURATION = "default";
    String ARCHIVES_CONFIGURATION = "archives";
    String CLASSIFIER = "m:classifier";

    /**
     * Returns the group of this dependency. The group is often required to find the artifacts of a dependency in a
     * repository. For example, the group name corresponds to a directory name in a Maven like repository. Might return
     * null.
     */
    String getGroup();

    /**
     * Returns the name of this dependency. The name is almost always required to find the artifacts of a dependency in
     * a repository. Never returns null.
     */
    String getName();

    /**
     * Returns the version of this dependency. The version is often required to find the artifacts of a dependency in a
     * repository. For example the version name corresponds to a directory name in a Maven like repository. Might return
     * null.
     */
    String getVersion();

    /**
     * Returns whether two dependencies have identical values for their properties. A dependency is an entity with a
     * key. Therefore dependencies might be equal and yet have different properties.
     *
     * @param dependency The dependency to compare this dependency with
     */
    boolean contentEquals(Dependency dependency);

    /**
     * Creates and returns a new dependency with the property values of this one.
     *
     * @return The copy. Never returns null.
     */
    Dependency copy();
}
