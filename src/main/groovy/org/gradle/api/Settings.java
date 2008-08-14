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

import groovy.lang.Closure;
import org.apache.ivy.plugins.resolver.DualResolver;
import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.gradle.api.dependencies.ResolverContainer;

import java.io.File;
import java.util.List;

/**
 * <p><code>Settings</code> declares the configuration required to instantiate and evaluate the hierarchy of {@link
 * org.gradle.api.Project} instances which are to particpate in a build.</p>
 *
 * <p>There is a one-to-one correspondence between a <code>Settings</code> instance and a <code>settings.gradle</code>
 * settings file. Before Gradle assembles the projects for a build, it creates a <code>Settings</code> instance and
 * executes the settings file against it.</p>
 *
 * <h3>Assembling a Multi-Project Build</h3>
 *
 * <p>One of the purposes of the <code>Settings</code> object is to allow you to declare the projects which are to be
 * included in the build. You add projects to the build using the {@link #include(String[])} method.</p>
 *
 * <h3>Defining the Build Classpath</h3>
 *
 * <p>Using the <code>Settings</code> object, you can define the classpath which will be used to load the build files,
 * and all objects used by them. This includes the non-standard plugins which the build files will use.</p>
 *
 * <h3>Using Settings from the Settings File</h3>
 *
 * @author Hans Dockter
 */
public interface Settings {
    /**
     * <p>The default name for the settings file.</p>
     */
    final static String DEFAULT_SETTINGS_FILE = "settings.gradle";

    /**
     * <p>The paths to the project which should take part in this build additional to the project containing the
     * settings file, which takes always in part in the build.</p>
     *
     * <p>A project path in the settings file is slightly different from a project path you use in your build file. A
     * settings project path is always relative to the directory containing the settings file.</p>
     *
     * @return a list with project paths in the order they have been added.
     */
    List<String> getProjectPaths();

    /**
     * <p>Adds paths of projects which should take part in this build, additional to the project containing the settings
     * file, which takes always in part in the build.</p>
     *
     * <p>A project path in the settings file is slightly different from a project path you use in your build file. A
     * settings project path is always relative to the directory containing the settings file.</p>
     *
     * @param projectPaths the project paths to add
     */
    void include(String[] projectPaths);

    /**
     * <p>Returns the {@link DependencyManager} which manages the classpath to use for the build files.</p>
     *
     * @return the dependency manager instance responsible for managing the dependencies for the users build script
     *         classpath.
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

    FileSystemResolver addFlatDirResolver(String name, Object[] dirs);

    /**
     * Returns the root dir of the build project.
     *
     * @return A file describing the root dir.
     */
    File getRootDir();

    /**
     * @param jarRepoUrls A list of urls of repositories to look for artifacts only. This is needed
     * if only the pom is in the MavenRepo repository (e.g. jta).
     */
    DualResolver addMavenRepo(String[] jarRepoUrls);

    DualResolver addMavenStyleRepo(String name, String root, String[] jarRepoUrls);
}
