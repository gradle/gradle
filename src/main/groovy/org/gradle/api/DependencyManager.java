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

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.plugins.resolver.*;
import org.gradle.api.dependencies.*;
import org.gradle.api.dependencies.maven.Conf2ScopeMappingContainer;
import org.gradle.api.dependencies.maven.GroovyPomFilterContainer;
import org.gradle.api.internal.dependencies.ResolverFactory;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import groovy.lang.Closure;

/**
 * <p>A <code>DependencyManager</code> represents the set of dependencies and artifacts for a {@link
 * org.gradle.api.Project}.</p>
 *
 * <h3>Using the DependencyManager in a Build File</h3>
 *
 * <h4>Dynamic Properties</h4>
 *
 * <p>Each configuration added to this {@code DependencyManager} is made available as a property which can be used from
 * your build script. You use the name of the configuration as the property name to refer to the {@link Configuration}
 * object.</p>
 *
 * <h4>Dynamic Methods</h4>
 *
 * <p>The following methods are added for each configuration added to this {@code DependencyManager}. For a
 * configuration with name {@code config}:</p>
 *
 * <ul>
 *
 * <li>Method {@code config(Closure configureClosure)}. This is equivalent to calling {@link #configuration(String,
 * groovy.lang.Closure)}.</li>
 *
 * <li>Method {@code config(Object dependency,Closure configureClosure)}. This is equivalent to calling {@link
 * #dependency(java.util.List, Object, groovy.lang.Closure)}.</li>
 *
 * <li>Method {@code config(Object... dependencies)}. This is equivalent to calling
 * {@link #dependencies(java.util.List, Object[])}.</li>
 *
 * </ul>
 *
 * @author Hans Dockter
 */
public interface DependencyManager extends DependencyContainer {
    public static final String GROUP = "group";
    public static final String VERSION = "version";
    public static final String DEFAULT_MAVEN_REPO_NAME = "MavenRepo";

    public static final String DEFAULT_ARTIFACT_PATTERN = "/[artifact]-[revision](-[classifier]).[ext]";

    public static final String MAVEN_REPO_URL = "http://repo1.maven.org/maven2/";

    public static final String BUILD_RESOLVER_NAME = "build-resolver";

    public static final String DEFAULT_CACHE_DIR_NAME = "cache";

    public static final String TMP_CACHE_DIR_NAME = Project.TMP_DIR_NAME + "/tmpIvyCache";

    public static final String DEFAULT_CACHE_NAME = "default-gradle-cache";

    public static final String DEFAULT_CACHE_ARTIFACT_PATTERN
            = "[organisation]/[module](/[branch])/[type]s/[artifact]-[revision](-[classifier])(.[ext])";

    public static final String DEFAULT_CACHE_IVY_PATTERN = "[organisation]/[module](/[branch])/ivy-[revision].xml";

    public static final String BUILD_RESOLVER_PATTERN = "[organisation]/[module]/[revision]/[type]s/[artifact].[ext]";

    public static final String MAVEN_REPO_PATTERN
            = "[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]";

    public static final String FLAT_DIR_RESOLVER_PATTERN = "[artifact]-[revision](-[classifier]).[ext]";
    public static final String DEFAULT_STATUS = "integration";
    public static final String DEFAULT_GROUP = "unspecified";
    public static final String DEFAULT_VERSION = "unspecified";

    public static final String CLASSIFIER = "classifier";

    /**
     * Returns the configurations that are managed by this dependency manager.
     *
     * @return The configurations, mapped from configuration name to the configuration. Returns an empty map when this
     *         dependency manager has no configurations.
     */
    Map<String, Configuration> getConfigurations();

    /**
     * A map where the key is the name of the configuration and the value are Gradles Artifact objects.
     */
    Map<String, List<PublishArtifact>> getArtifacts();

    /**
     * A map for passing directly instances of Ivy Artifact objects.
     */
    Map<String, List<Artifact>> getArtifactDescriptors();

    /**
     * Ivy patterns to tell Ivy where to look for artifacts when publishing the module.
     */
    List<String> getAbsoluteArtifactPatterns();

    /**
     * Directories where Ivy should look for artifacts according to the {@link #getDefaultArtifactPattern()}.
     */
    Set<File> getArtifactParentDirs();

    /**
     * Returns an Ivy pattern describing not a path but only a pattern for the artifact file itself. The default for
     * this property is {@link #DEFAULT_ARTIFACT_PATTERN}. The path information is taken from {@link
     * #getArtifactParentDirs()}.
     *
     * @see #setDefaultArtifactPattern(String)
     */
    String getDefaultArtifactPattern();

    /**
     * Sets the default artifact pattern.
     *
     * @param defaultArtifactPattern The default artifact pattern.
     * @see #getDefaultArtifactPattern()
     */
    void setDefaultArtifactPattern(String defaultArtifactPattern);

    /**
     * Returns the name of the task which produces the artifacts of this project. This is needed by other projects,
     * which have a dependency on a project.
     */
    String getArtifactProductionTaskName();

    /**
     * Set the artifact production task name for this project.
     *
     * @param name The name of the artifact production task name.
     * @see #getArtifactProductionTaskName()
     */
    void setArtifactProductionTaskName(String name);

    Map<String, Set<String>> getConfs4Task();

    /**
     * A map where the key is the name of the configuration and the value is the name of a task. This is needed to deal
     * with project dependencies. In case of a project dependency, we need to establish a dependsOn relationship,
     * between a task of the project and the task of the dependsOn project, which builds the artifacts. The default is,
     * that the project task is used, which has the same name as the configuration. If this is not what is wanted, the
     * mapping can be specified via this map.
     */
    Map<String, Set<String>> getTasks4Conf();

    /**
     * A configuration can be assigned to one or more tasks. One usage of this mapping is that for example the
     * <pre>compile</pre> task can ask for its classpath by simple passing its name as an argument. Of course the
     * JavaPlugin had to create the mapping during the initialization phase. <p/> Another important use case are
     * multi-project builds. Let's say you add a project dependency to the testCompile conf. You don't want the other
     * project to be build, if you do just a compile. The testCompile task is mapped to the testCompile conf. With this
     * knowledge we create a dependsOn relation ship between the testCompile task and the task of the other project that
     * produces the jar. This way a compile does not trigger the build of the other project, but a testCompile does.
     *
     * @param conf The name of the configuration
     * @param task The name of the task
     * @return this
     */
    DependencyManager linkConfWithTask(String conf, String task);

    /**
     * Disassociates a conf from a task.
     *
     * @param conf The name of the configuration
     * @param task The name of the task
     * @return this
     * @see #linkConfWithTask(String, String)
     */
    DependencyManager unlinkConfWithTask(String conf, String task);

    /**
     * Adds artifacts for the given confs. An artifact is normally a library produced by the project. Usually this
     * method is not directly used by the build master. The archive tasks of the libs bundle call this method to add the
     * archive to the artifacts.
     */
    void addArtifacts(String configurationName, PublishArtifact... artifacts);

    /**
     * Adds a configuration to this dependency manager.
     *
     * @param configuration The name of the configuration.
     * @return The added configuration
     * @throws InvalidUserDataException If a configuration with the given name already exists in this dependency
     * manager.
     */
    Configuration addConfiguration(String configuration) throws InvalidUserDataException;

    /**
     * Adds a configuration to this dependency manager, and configures it using the given closure.
     *
     * @param configuration The name of the configuration.
     * @param configureClosure The closure to use to configure the new configuration.
     * @return The added configuration
     * @throws InvalidUserDataException If a configuration with the given name already exists in this dependency
     * manager.
     */
    Configuration addConfiguration(String configuration, Closure configureClosure) throws InvalidUserDataException;

    /**
     * <p>Locates a {@link Configuration} by name.</p>
     *
     * @param name The name of the configuration.
     * @return The configuration. Returns null if the configuration cannot be found.
     */
    Configuration findConfiguration(String name);

    /**
     * <p>Locates a {@link Configuration} by name.</p>
     *
     * <p>You can also call this method from your build script by using the name of the configuration.</p>
     *
     * @param name The name of the configuration.
     * @return The configuration. Never returns null.
     * @throws UnknownConfigurationException when a configuration with the given name cannot be found.
     */
    Configuration configuration(String name) throws UnknownConfigurationException;

    /**
     * <p>Locates a {@link Configuration} by name and configures it.</p>
     *
     * <p>You can also call this method from your build script by using the name of the configuration followed by a
     * closure.</p>
     *
     * @param name The name of the configuration.
     * @param configureClosure The closure to use to configure the configuration.
     * @return The configuration. Never returns null.
     * @throws UnknownConfigurationException when a configuration with the given name cannot be found.
     */
    Configuration configuration(String name, Closure configureClosure) throws UnknownConfigurationException;

    /**
     * Returns a list of file objects, denoting the path to the classpath elements belonging to this configuration. If a
     * dependency can't be resolved an exception is thrown. Resolves also project dependencies.
     *
     * @return A list of file objects
     * @throws GradleException If not all dependencies can be resolved
     * @see #resolve(String, boolean, boolean)
     */
    List<File> resolve(String conf);

    /**
     * Returns a list of file objects, denoting the path to the classpath elements belonging to this configuration.
     *
     * @param failForMissingDependencies If this method should throw an exception in case a dependency of the
     * configuration can't be resolved
     * @param includeProjectDependencies Whether project dependencies should be resolved as well.
     * @return A list of file objects
     * @throws GradleException If not all dependencies can be resolved
     */
    List<File> resolve(String conf, boolean failForMissingDependencies, boolean includeProjectDependencies);

    /**
     * Returns a list of file objects, denoting the path to the classpath elements belonging to this task. Not all tasks
     * have an classpagh assigned to them.
     *
     * @throws InvalidUserDataException If no classpath is assigned to this tak
     */
    List<File> resolveTask(String taskName);

    /**
     * Returns a classpath String in ant notation for the configuration.
     */
    String antpath(String conf);

    /**
     * Returns a ResolverContainer with the resolvers responsible for resolving the classpath dependencies. There are
     * different resolver containers for uploading the libraries and the distributions of a project. The same resolvers
     * can be part of multiple resolver container.
     *
     * @return a ResolverContainer containing the classpathResolvers
     */
    ResolverContainer getClasspathResolvers();

    /**
     * @return The root directory used by the build resolver.
     */
    File getBuildResolverDir();

    /**
     * The build resolver is the resolver responsible for uploading and resolving the build source libraries as well as
     * project libraries between multi-project builds.
     *
     * @return the build resolver
     */
    RepositoryResolver getBuildResolver();

    /**
     * Sets the fail behavior for resolving dependencies.
     *
     * @see #isFailForMissingDependencies()
     */
    void setFailForMissingDependencies(boolean failForMissingDependencies);

    /**
     * If true an exception is thrown if a dependency can't be resolved. This usually leads to a build failure (unless
     * there is custom logic which catches the exception). If false, missing dependencies are ignored. The default value
     * is true.
     */
    boolean isFailForMissingDependencies();

    /**
     * Adds a resolver that look in a list of directories for artifacts. The artifacts are expected to be located in the
     * root of the specified directories. The resolver ignores any group/organization information specified in the
     * dependency section of your build script. If you only use this kind of resolver you might specify your
     * dependencies like <code>":junit:4.4"</code> instead of <code>"junit:junit:4.4"</code>
     *
     * @param name The name of the resolver
     * @param dirs The directories to look for artifacts.
     * @return the added resolver
     */
    FileSystemResolver addFlatDirResolver(String name, Object... dirs);

    /**
     * Adds a resolver which look in the official Maven Repo for dependencies. The URL of the official Repo is {@link
     * #MAVEN_REPO_URL}. The name is {@link #DEFAULT_MAVEN_REPO_NAME}. The behavior of this resolver is otherwise the
     * same as the ones added by {@link #addMavenStyleRepo(String, String, String[])}.
     *
     * @param jarRepoUrls A list of urls of repositories to look for artifacts only.
     * @return the added resolver
     * @see #addMavenStyleRepo(String, String, String[])
     */
    DependencyResolver addMavenRepo(String... jarRepoUrls);

    /**
     * Adds a resolver that uses Maven pom.xml descriptor files for resolving dependencies. By default the resolver
     * accepts to resolve artifacts without a pom. The resolver always looks first in the root location for the pom and
     * the artifact. Sometimes the artifact is supposed to live in a different repository as the pom. In such a case you
     * can specify further locations to look for an artifact. But be aware that the pom is only looked for in the root
     * location.
     *
     * For Ivy related reasons, Maven Snapshot dependencies are only properly resolved if no additional jar locations
     * are specified. This is unfortunate and we hope to improve this in our next release.
     *
     * @param name The name of the resolver
     * @param root A URL to look for artifacts and pom's
     * @param jarRepoUrls A list of urls of repositories to look for artifacts only.
     * @return the added resolver
     */
    DependencyResolver addMavenStyleRepo(String name, String root, String... jarRepoUrls);

    /**
     * Publishes dependencies with a set of resolvers.
     *
     * @param configurations The configurations which dependencies you want to publish.
     * @param resolvers The resolvers you want to publish the dependencies with.
     * @param uploadModuleDescriptor Whether the module descriptor should be published (ivy.xml or pom.xml)
     */
    void publish(List<String> configurations, ResolverContainer resolvers, boolean uploadModuleDescriptor);

    // todo Should we move this to DependencyManagerInternal?
    ModuleRevisionId createModuleRevisionId();


    /**
     * Returns a container for adding exclude rules that apply to the transitive dependencies of all dependencies.
     */
    ExcludeRuleContainer getExcludeRules();

    /**
     * Returns an ivy module descriptor containing all dependencies and configuration that have been added to this
     * dependency manager.
     *
     * @param includeProjectDependencies Whether project dependencies should be included in the module descriptor
     */
    ModuleDescriptor createModuleDescriptor(boolean includeProjectDependencies);

    /**
     * Returns the default mapping between configurations and Maven scopes. This default mapping is used by default a
     * {@link org.gradle.api.internal.dependencies.maven.deploy.BaseMavenDeployer} to create and deploy pom. If wished,
     * a MavenUploadResolver sepcific setting can be defined.
     */
    Conf2ScopeMappingContainer getDefaultMavenScopeMapping();

    /**
     *
     */
    GroovyPomFilterContainer getPoms();

    /**
     * Returns a factory for creating special resolvers like flat dir or maven resolvers.
     */
    ResolverFactory getResolverFactory();

    /**
     * Returns the converter used for creating the IvySettings object.
     */
    SettingsConverter getSettingsConverter();

    /**
     * Returnes the converter used for creating an Ivy ModuleDescriptor.
     */
    ModuleDescriptorConverter getModuleDescriptorConverter();
}
