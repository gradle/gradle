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

package org.gradle.api;

import org.gradle.api.dependencies.ResolverContainer;
import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.apache.ivy.plugins.resolver.DualResolver;

import java.util.List;
import java.io.File;

import groovy.lang.Closure;

/**
 * @author Hans Dockter
 */
public interface Settings {
    /**
     * The paths to the project which should take part in this build additional to the project containing the
     * settings file, which takes always in part in the build.
     *
     * A project path in the settings file is slightly different from a project path you use in your build scripts.
     * A settings project path is always relative to the directory containing the settings file.
     *
     * @return a list with project paths in the order they have been added.
     */
    List getProjectPaths();

    /**
     * Adds project paths of projects which should take part in this build, additional to the project containing the
     * settings file, which takes always in part in the build.
     *
     * A project path in the settings file is slightly different from a project path you use in your build scripts.
     * A settings project path is always relative to the directory containing the settings file.
     *
     * @param projectPaths the project paths to add
     */
    void include(String[] projectPaths);

    /**
     * @return the dependency manager instance responsible for managing the dependencies for the users build script classpath.
     */
    DependencyManager getDependencyManager();

    /**
     * Delegates to the dependencies manager addDependencies method.
     *
     * @param dependencies dependencies passed to the dependencies manager addDependencies method.
     */
    void dependencies(Object[] dependencies);

    void dependency(String id, Closure configureClosure);

    /**
     * Delegates to the dependencies manager getResolvers method.
     *
     * @return the result of the dependencies manager getResolvers method.
     */
    ResolverContainer getResolvers();

    FileSystemResolver addFlatDirResolver(String name, File[] dirs);

    /**
     * @param jarRepoUrls A list of urls of repositories to look for artifacts only. This is needed
     * if only the pom is in the MavenRepo repository (e.g. jta).
     */
    DualResolver addMavenRepo(String[] jarRepoUrls);

    DualResolver addMavenStyleRepo(String name, String root, String[] jarRepoUrls);
}
