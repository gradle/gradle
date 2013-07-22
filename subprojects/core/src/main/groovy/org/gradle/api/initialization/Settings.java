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

package org.gradle.api.initialization;

import org.gradle.StartParameter;
import org.gradle.api.UnknownProjectException;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.PluginAware;

import java.io.File;

/**
 * <p>Declares the configuration required to instantiate and configure the hierarchy of {@link
 * org.gradle.api.Project} instances which are to participate in a build.</p>
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
 * change the default values for several properties of the project.</p>
 *
 * <h3>Using Settings in a Settings File</h3>
 *
 * <h4>Dynamic Properties</h4>
 *
 * <p>In addition to the properties of this interface, the {@code Settings} object makes some additional read-only
 * properties available to the settings script. This includes properties from the following sources:</p>
 *
 * <ul>
 *
 * <li>Defined in the {@value org.gradle.api.Project#GRADLE_PROPERTIES} file located in the settings directory of the
 * build.</li>
 *
 * <li>Defined the {@value org.gradle.api.Project#GRADLE_PROPERTIES} file located in the user's {@code .gradle}
 * directory.</li>
 *
 * <li>Provided on the command-line using the -P option.</li>
 *
 * </ul>
 */
public interface Settings extends PluginAware {
    /**
     * <p>The default name for the settings file.</p>
     */
    String DEFAULT_SETTINGS_FILE = "settings.gradle";

    /**
     * <p>Adds the given projects to the build. Each path in the supplied list is treated as the path of a project to
     * add to the build. Note that these path are not file paths, but instead specify the location of the new project in
     * the project heirarchy. As such, the supplied paths must use the ':' character as separator.</p>
     *
     * <p>The last element of the supplied path is used as the project name. The supplied path is converted to a project
     * directory relative to the root project directory.</p>
     *
     * <p>As an example, the path {@code a:b} adds a project with path {@code :a:b}, name {@code b} and project
     * directory {@code $rootDir/a/b}.</p>
     *
     * @param projectPaths the projects to add.
     */
    void include(String[] projectPaths);

    /**
     * <p>Adds the given projects to the build. Each name in the supplied list is treated as the name of a project to
     * add to the build.</p>
     *
     * <p>The supplied name is converted to a project directory relative to the <em>parent</em> directory of the root
     * project directory.</p>
     *
     * <p>As an example, the name {@code a} add a project with path {@code :a}, name {@code a} and project directory
     * {@code $rootDir/../a}.</p>
     *
     * @param projectNames the projects to add.
     */
    void includeFlat(String[] projectNames);

    /**
     * <p>Returns this settings object.</p>
     *
     * @return This settings object. Never returns null.
     */
    Settings getSettings();

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
     * <p>Returns the root project of the build.</p>
     *
     * @return The root project. Never returns null.
     */
    ProjectDescriptor getRootProject();

    /**
     * <p>Returns the project with the given path.</p>
     *
     * @param path The path.
     * @return The project with the given path. Never returns null.
     * @throws UnknownProjectException If no project with the given path exists.
     */
    ProjectDescriptor project(String path) throws UnknownProjectException;

    /**
     * <p>Returns the project with the given path.</p>
     *
     * @param path The path
     * @return The project with the given path. Returns null if no such project exists.
     */
    ProjectDescriptor findProject(String path);

    /**
     * <p>Returns the project with the given project directory.</p>
     *
     * @param projectDir The project directory.
     * @return The project with the given project directory. Never returns null.
     * @throws UnknownProjectException If no project with the given path exists.
     */
    ProjectDescriptor project(File projectDir) throws UnknownProjectException;

    /**
     * <p>Returns the project with the given project directory.</p>
     *
     * @param projectDir The project directory.
     * @return The project with the given project directory. Returns null if no such project exists.
     */
    ProjectDescriptor findProject(File projectDir);

    /**
     * <p>Returns the set of parameters used to invoke this instance of Gradle.</p>
     *
     * @return The parameters. Never returns null.
     */
    StartParameter getStartParameter();

    /**
     * Returns the {@link Gradle} instance for the current build.
     * 
     * @return The Gradle instance. Never returns null.
     */
    Gradle getGradle();
}
