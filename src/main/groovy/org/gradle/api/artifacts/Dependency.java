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

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * A {@code Dependency} represents a dependency on the artifacts from a particular source. A source can be an
 * Ivy module, a Maven pom, another Gradle project, etc.. A source can have one or more artifacts.
 *
 * A dependency is an entity. Its key consists of the fields {@code group, name, version, configuration}.
 *
 * @author Hans Dockter
 */
public interface Dependency {
    String DEFAULT_CONFIGURATION = "default";
    String ARCHIVES_CONFIGURATION = "archives";
    String CLASSIFIER = "classifier";

    /**
     * Adds an exclude rule to exclude transitive dependencies of this dependency. You can also
     * add exclude rules per-configuration. See {@link Configuration#getExcludeRules()}.
     *
     * @param excludeProperties the properties to define the exclude rule.
     * @return this
     */
    Dependency exclude(Map<String, String> excludeProperties);

    /**
     * Returns the exclude rules for this dependency.
     *
     * @see #exclude(java.util.Map) 
     */
    Set<ExcludeRule> getExcludeRules();

    /**
     * Returns the group of this dependency. The group is often required to find the artifacts of a dependency
     * in a repository. For example the group name corresponds to a directory name in a Maven like repository.
     * Might return null.
     */
    String getGroup();

    /**
     * Returns the name of this dependency. The name is almost always required to find the artifacts of a dependency
     * in a repository. Never returns null.
     */
    String getName();

    /**
     * Returns the version of this dependency. The version if often required to find the artifacts of a dependency
     * in a repository. For example the version name corresponds to a directory name in a Maven like repository.
     * Might return null.
     */
    String getVersion();

    /**
     * Returns whether this dependency should be resolved including or excluding its transitive dependencies.
     *
     * @see #setTransitive(boolean) 
     */
    boolean isTransitive();

    /**
     * Sets whether this dependency should be resolved including or excluding its transitive dependencies.
     * The artifacts belonging to this dependency might themselve have dependencies on other artifacts. The latter
     * are called transitive dependencies.
     * 
     * @param transitive Whether transitive dependencies should be resolved.
     * @return this
     */
    Dependency setTransitive(boolean transitive);

    /**
     * Returns the artifacts belonging to this dependency.
     *
     * @see #addArtifact(DependencyArtifact) 
     */
    Set<DependencyArtifact> getArtifacts();

    /**
     * Adds an artifact to this dependency. If no artifact is added to a dependency, an implicit default
     * artifact is used. This default artifact has the same name as the module and its type and extension is
     * <em>jar</em>. If at least one artifact is explicitly added, the implicit default artifact won't be used
     * any longer.
     *
     * @param artifact
     * @return this
     */
    Dependency addArtifact(DependencyArtifact artifact);

    /**
     * Returns the configuration of this dependency module (not the configurations this dependency belongs too).
     * Never returns null. The default value for the configuration is {@link #DEFAULT_CONFIGURATION}.
     * A dependency source might have multiple configuration. Every configuration
     * represents a different set of artifacts and dependencies for this dependency module.
     */
    String getConfiguration();

    /**
     * Returns whether two dependencies have identical values for there properties.
     * A dependency is an entity with a key. Therefore dependencies might be equal and yet have
     * different properties.
     *  
     * @param dependency The dependency to compare this dependency with
     */
    boolean contentEquals(Dependency dependency);

    /**
     * Creates and returns a new dependency with the property values of this one.
     */
    Dependency copy();
}
