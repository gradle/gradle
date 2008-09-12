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

package org.gradle.api.initialization;

import groovy.lang.Closure;
import org.apache.ivy.plugins.resolver.DualResolver;
import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.gradle.StartParameter;
import org.gradle.api.DependencyManager;
import org.gradle.api.Project;
import org.gradle.api.dependencies.ResolverContainer;

import java.io.File;

/**
 * <p><code>Settings</code> declares the configuration required to instantiate and evaluate the hierarchy of {@link
 * Project} instances which are to participate in a build.</p>
 *
 * <p>There is a one-to-one correspondence between a <code>Settings</code> instance and a <code>{@value
 * #DEFAULT_SETTINGS_FILE}</code> settings file. Before Gradle assembles the projects for a build, it creates a
 * <code>Settings</code> instance and executes the settings file against it.</p>
 *
 * <h3>Assembling a Multi-Project Build</h3>
 *
 * <p>One of the purposes of the <code>Settings</code> object is to allow you to declare the projects which are to be
 * included in the build. You add projects to the build using the {@link #include(String[])} method.  There is always a
 * root project included in a build.  It is added automatically when the <code>Settings</code> object is created.  The
 * root project's name defaults to the name of the directory containing the settings file. The root project's project
 * directory defaults to the directory containing the settings file.</p>
 *
 * <p>When a project is included in the build, a {@link ProjectDescriptor} is created. You can use this descriptor to
 * change the default vaules for several properties of the project.</p>
 *
 * <h3>Defining the Build Class-path</h3>
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
    String DEFAULT_SETTINGS_FILE = "settings.gradle";

    String BUILD_DEPENDENCIES_PROJECT_GROUP = "org.gradle";
    String BUILD_DEPENDENCIES_PROJECT_VERSION = "SNAPSHOT";
    String BUILD_DEPENDENCIES_PROJECT_NAME = "build";

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

    void includeFlat(String[] projectNames);

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
     * <p>Returns the settings directory of the build. The settings directory is the directory containing the settings
     * file.</p>
     *
     * @return The settings directory. Never returns null.
     */
    File getSettingsDir();

    /**
     * <p>Returns the root directory of the build. The root directory is the project directory of the root project.</p>
     *
     * @return The root directory. Never returns null.
     */
    File getRootDir();

    /**
     * @param jarRepoUrls A list of urls of repositories to look for artifacts only. This is needed if only the pom is
     * in the MavenRepo repository (e.g. jta).
     */
    DualResolver addMavenRepo(String[] jarRepoUrls);

    DualResolver addMavenStyleRepo(String name, String root, String[] jarRepoUrls);

    /**
     * <p>Returns the root project of the build.</p>
     *
     * @return The root project. Never returns null.
     */
    ProjectDescriptor getRootProjectDescriptor();

    /**
     * <p>Returns the project with the given path.</p>
     *
     * @param path The path
     * @return The project with the given path. Returns null if no such project exists.
     */
    ProjectDescriptor descriptor(String path);

    /**
     * <p>Returns the project with the given project directory.</p>
     *
     * @param projectDir The project directory.
     * @return The project with the given project directory. Returns null if no such project exists.
     */
    ProjectDescriptor descriptor(File projectDir);

    StartParameter getStartParameter();

    void clientModule(String id, Closure configureClosure);
}
