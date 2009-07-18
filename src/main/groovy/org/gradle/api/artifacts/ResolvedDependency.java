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

import java.io.File;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public interface ResolvedDependency {
    /**
     * Returns the name of the dependency. The name is differently constructed for different types of dependencies.
     * For external dependencies the name consists of the group, name and version. For self resolving dependencies the
     * name consists of the file paths belonging to this dependency.
     */
    String getName();

    /**
     * Returns the configuration under which this instance was resolved.
     */
    String getConfiguration();

    /**
     * Returns the transitive ResolvedDependency instances of this resolved dependency. Returns never null.
     */
    Set<ResolvedDependency> getChildren();

    /**
     * Returns the ResolvedDependency instances that have this instance as a transitive dependency. Returns never null.
     */
    Set<ResolvedDependency> getParents();

    /**
     * Returns the artifact files belonging to this ResolvedDependencie. Returns never null. But there might be
     * ResolvedDependencies which don't have artifact files and where an empty set is returned. 
     */
    Set<File> getModuleFiles();

    /**
     * Returns the artifact files belonging to this ResolvedDependencie and recursively to its children. Returns never null.
     */
    Set<File> getAllModuleFiles();

    Set<File> getParentFiles(ResolvedDependency parent);

    Set<File> getFiles(ResolvedDependency parent);

    Set<File> getAllFiles(ResolvedDependency parent);
}
