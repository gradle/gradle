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
package org.gradle.api.artifacts;

import java.util.Set;

/**
 * Information about a resolved dependency.
 *
 * @deprecated This type will be removed in a future version of Gradle.
 * Use {@link org.gradle.api.artifacts.result.ResolvedDependencyResult} instead.
 */
@Deprecated
public interface ResolvedDependency {
    /**
     * Returns the name of the resolved dependency.
     */
    @Deprecated
    String getName();

    /**
     * Returns the module group of the resolved dependency.
     */
    @Deprecated
    String getModuleGroup();

    /**
     * Returns the module name of the resolved dependency.
     */
    @Deprecated
    String getModuleName();

    /**
     * Returns the module version of the resolved dependency.
     */
    @Deprecated
    String getModuleVersion();

    /**
     * Returns the configuration under which this instance was resolved.
     */
    @Deprecated
    String getConfiguration();

    /**
     * Returns the module which this resolved dependency belongs to.
     *
     * @return The module.
     */
    @Deprecated
    ResolvedModuleVersion getModule();

    /**
     * Returns the transitive ResolvedDependency instances of this resolved dependency. Returns never null.
     */
    @Deprecated
    Set<ResolvedDependency> getChildren();

    /**
     * Returns the ResolvedDependency instances that have this instance as a transitive dependency. Returns never null.
     */
    @Deprecated
    Set<ResolvedDependency> getParents();

    /**
     * Returns the module artifacts belonging to this ResolvedDependency. A module artifact is an artifact that belongs
     * to a ResolvedDependency independent of a particular parent. Returns never null.
     */
    @Deprecated
    Set<ResolvedArtifact> getModuleArtifacts();

    /**
     * Returns the module artifacts belonging to this ResolvedDependency and recursively to its children. Returns never null.
     *
     * @see #getModuleArtifacts()
     */
    @Deprecated
    Set<ResolvedArtifact> getAllModuleArtifacts();

    /**
     * Returns the artifacts belonging to this ResolvedDependency which it only has for a particular parent. Returns never null.
     *
     * @param parent A parent of the ResolvedDependency. Must not be null.
     * @throws org.gradle.api.InvalidUserDataException If the parent is unknown or null
     */
    @Deprecated
    Set<ResolvedArtifact> getParentArtifacts(ResolvedDependency parent);

    /**
     * Returns the parent artifacts of this dependency. Never returns null.
     *
     * @param parent A parent of the ResolvedDependency. Must not be null.
     * @throws org.gradle.api.InvalidUserDataException If the parent is unknown or null
     */
    @Deprecated
    Set<ResolvedArtifact> getArtifacts(ResolvedDependency parent);

    /**
     * Returns the parent artifacts of this dependency and its children. Never returns null.
     *
     * @param parent A parent of the ResolvedDependency. Must not be null.
     * @throws org.gradle.api.InvalidUserDataException If the parent is unknown or null
     */
    @Deprecated
    Set<ResolvedArtifact> getAllArtifacts(ResolvedDependency parent);
}
