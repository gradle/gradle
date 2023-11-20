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

import com.h0tk3y.kotlin.staticObjectNotation.Adding;
import com.h0tk3y.kotlin.staticObjectNotation.Restricted;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.UnknownProjectException;
import org.gradle.api.file.BuildLayout;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.initialization.resolve.DependencyResolutionManagement;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.PluginAware;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.toolchain.management.ToolchainManagement;
import org.gradle.api.cache.CacheConfigurations;
import org.gradle.caching.configuration.BuildCacheConfiguration;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.plugin.management.PluginManagementSpec;
import org.gradle.vcs.SourceControl;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Arrays;

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
 * included in the build. You add projects to the build using the {@link #include(String...)} method.  There is always a
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
@HasInternalProtocol
public interface Settings extends PluginAware, ExtensionAware {
    /**
     * <p>The default name for the settings file.</p>
     */
    String DEFAULT_SETTINGS_FILE = "settings.gradle";

    /**
     * <p>Adds the given projects to the build. Each path in the supplied list is treated as the path of a project to
     * add to the build. Note that these path are not file paths, but instead specify the location of the new project in
     * the project hierarchy. As such, the supplied paths must use the ':' character as separator (and NOT '/').</p>
     *
     * <p>The last element of the supplied path is used as the project name. The supplied path is converted to a project
     * directory relative to the root project directory. The project directory can be altered by changing the 'projectDir'
     * property after the project has been included (see {@link ProjectDescriptor#setProjectDir(File)})</p>
     *
     * <p>As an example, the path {@code a:b} adds a project with path {@code :a:b}, name {@code b} and project
     * directory {@code $rootDir/a/b}. It also adds the a project with path {@code :a}, name {@code a} and project
     * directory {@code $rootDir/a}, if it does not exist already.</p>
     *
     * <p>Some common examples of using the project path are:</p>
     *
     * <pre class='autoTestedSettings'>
     *   // include two projects, 'foo' and 'foo:bar'
     *   // directories are inferred by replacing ':' with '/'
     *   include 'foo:bar'
     *
     *   // include one project whose project dir does not match the logical project path
     *   include 'baz'
     *   project(':baz').projectDir = file('foo/baz')
     *
     *   // include many projects whose project dirs do not match the logical project paths
     *   file('subprojects').eachDir { dir -&gt;
     *     include dir.name
     *     project(":${dir.name}").projectDir = dir
     *   }
     * </pre>
     *
     * @param projectPaths the projects to add.
     */
    default void include(String... projectPaths) {
        include(Arrays.asList(projectPaths));
    }

    @Adding // TODO: support varargs and remove the workaround
    default void include(String projectPath) {
        include(new String[] {projectPath});
    }

    /**
     * <p>Adds the given projects to the build. Each path in the supplied list is treated as the path of a project to
     * add to the build. Note that these path are not file paths, but instead specify the location of the new project in
     * the project hierarchy. As such, the supplied paths must use the ':' character as separator (and NOT '/').</p>
     *
     * <p>The last element of the supplied path is used as the project name. The supplied path is converted to a project
     * directory relative to the root project directory. The project directory can be altered by changing the 'projectDir'
     * property after the project has been included (see {@link ProjectDescriptor#setProjectDir(File)})</p>
     *
     * <p>As an example, the path {@code a:b} adds a project with path {@code :a:b}, name {@code b} and project
     * directory {@code $rootDir/a/b}. It also adds the a project with path {@code :a}, name {@code a} and project
     * directory {@code $rootDir/a}, if it does not exist already.</p>
     *
     * <p>Some common examples of using the project path are:</p>
     *
     * <pre class='autoTestedSettings'>
     *   // include two projects, 'foo' and 'foo:bar'
     *   // directories are inferred by replacing ':' with '/'
     *   include(['foo:bar'])
     *
     *   // include one project whose project dir does not match the logical project path
     *   include(['baz'])
     *   project(':baz').projectDir = file('foo/baz')
     *
     *   // include many projects whose project dirs do not match the logical project paths
     *   file('subprojects').eachDir { dir -&gt;
     *     include([dir.name])
     *     project(":${dir.name}").projectDir = dir
     *   }
     * </pre>
     *
     * @param projectPaths the projects to add.
     *
     * @since 7.4
     */
    void include(Iterable<String> projectPaths);

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
    default void includeFlat(String... projectNames) {
        includeFlat(Arrays.asList(projectNames));
    }

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
     *
     * @since 7.4
     */
    void includeFlat(Iterable<String> projectNames);

    /**
     * <p>Returns this settings object.</p>
     *
     * @return This settings object. Never returns null.
     */
    Settings getSettings();

    /**
     * Provides access to important locations for a Gradle build.
     *
     * @since 8.5
     */
    @Incubating
    BuildLayout getLayout();

    /**
     * Returns the build script handler for settings. You can use this handler to query details about the build
     * script for settings, and manage the classpath used to compile and execute the settings script.
     *
     * @return the classpath handler. Never returns null.
     *
     * @since 4.4
     */
    ScriptHandler getBuildscript();

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
    @Restricted
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
    @Nullable
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
    @Nullable
    ProjectDescriptor findProject(File projectDir);

    /**
     * <p>Returns the set of parameters used to invoke this instance of Gradle.</p>
     *
     * @return The parameters. Never returns null.
     */
    StartParameter getStartParameter();

    /**
     * Provides access to methods to create various kinds of {@link Provider} instances.
     *
     * @since 6.8
     */
    ProviderFactory getProviders();

    /**
     * Returns the {@link Gradle} instance for the current build.
     *
     * @return The Gradle instance. Never returns null.
     */
    Gradle getGradle();

    /**
     * Includes a build at the specified path to the composite build.
     * @param rootProject The path to the root project directory for the build.
     *
     * @since 3.1
     */
    void includeBuild(Object rootProject);

    /**
     * Includes a build at the specified path to the composite build, with the supplied configuration.
     * @param rootProject The path to the root project directory for the build.
     * @param configuration An action to configure the included build.
     *
     * @since 3.1
     */
    void includeBuild(Object rootProject, Action<ConfigurableIncludedBuild> configuration);

    /**
     * Returns the build cache configuration.
     *
     * @since 3.5
     */
    BuildCacheConfiguration getBuildCache();

    /**
     * Configures build cache.
     *
     * @since 3.5
     */
    void buildCache(Action<? super BuildCacheConfiguration> action);

    /**
     * Configures plugin management.
     *
     * @since 3.5
     */
    void pluginManagement(Action<? super PluginManagementSpec> pluginManagementSpec);

    /**
     * Returns the plugin management configuration.
     *
     * @since 3.5
     */
    PluginManagementSpec getPluginManagement();

    /**
     * Configures source control.
     *
     * @since 4.4
     */
    void sourceControl(Action<? super SourceControl> configuration);

    /**
     * Returns the source control configuration.
     *
     * @since 4.4
     */
    SourceControl getSourceControl();

    /**
     * Enables a feature preview by name.
     *
     * @param name the name of the feature to enable
     *
     * @since 4.6
     */
    void enableFeaturePreview(String name);

    /**
     * Configures the cross-project dependency resolution aspects
     * @param dependencyResolutionConfiguration the configuration
     *
     * @since 6.8
     */
    void dependencyResolutionManagement(Action<? super DependencyResolutionManagement> dependencyResolutionConfiguration);

    /**
     * Returns the dependency resolution management handler.
     *
     * @since 6.8
     */
    DependencyResolutionManagement getDependencyResolutionManagement();

    /**
     * Configures toolchain management.
     *
     * @since 7.6
     */
    @Incubating
    void toolchainManagement(Action<? super ToolchainManagement> toolchainManagementConfiguration);

    /**
     * Returns the toolchain management configuration.
     *
     * @since 7.6
     */
    @Incubating
    ToolchainManagement getToolchainManagement();

    /**
     * Returns the configuration for caches stored in the user home directory.
     *
     * @since 8.0
     */
    @Incubating
    CacheConfigurations getCaches();

    /**
     * Configures the settings for caches stored in the user home directory.
     *
     * @param cachesConfiguration the configuration
     *
     * @since 8.0
     */
    @Incubating
    void caches(Action<? super CacheConfigurations> cachesConfiguration);
}
