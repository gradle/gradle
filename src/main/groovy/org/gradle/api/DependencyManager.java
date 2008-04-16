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
import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.Configuration;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.plugins.resolver.RepositoryResolver;
import org.gradle.api.internal.dependencies.ResolverContainer;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public interface DependencyManager {
    public static final String BUILD_RESOLVER_NAME = "build-resolver";

    public static final String BUILD_RESOLVER_PATTERN = "[organisation]/[module]/[revision]/[type]s/[artifact].[ext]";

    /**
     * The project associated with this DependencyManager
     * @return an instance of a project
     */
    Project getProject();

    Ivy getIvy();

    /**
    * A map where the key is the name of the configuration and the values are Ivy configuration objects.
    */
    Map getConfigurations();

    /**
    * A list of Gradle Dependency objects.
    */
    List getDependencies();

    /**
    * A list for passing directly instances of Ivy DependencyDescriptor objects.
    */
    List getDependencyDescriptors();

    ResolverContainer getClasspathResolvers();
    
    /**
    * A map where the key is the name of the configuration and the value are Gradles Artifact objects.
    */
    Map getArtifacts();

    /**
    * A map for passing directly instances of Ivy Artifact objects.
    */
    Map getArtifactDescriptors();

    /**
    * Ivy patterns to tell Ivy where to look for artifacts when publishing the module.
    */
    List getArtifactPatterns();

    /**
    * The name of the task which produces the artifacts of this project. This is needed by other projects,
    * which have a dependency on a project.
    */
    String getArtifactProductionTaskName();

    /**
    * A map where the key is the name of the configuration and the value is the name of a task. This is needed
    * to deal with project dependencies. In case of a project dependency, we need to establish a dependsOn relationship,
    * between a task of the project and the task of the dependsOn project, which builds the artifacts. The default is,
    * that the project task is used, which has the same name as the configuration. If this is not what is wanted,
    * the mapping can be specified via this map.
    */
    Map getConf2Tasks();

    /**
     * A configuration can be assigned to one or more tasks. One usage of this mapping is that for example the
     * <pre>compile</pre> task can ask for its classpath by simple passing its name as an argument. Of course the JavaPlugin
     * had to create the mapping during the initialization phase.
     *
     * Another important use case are multi-project builds. Let's say you add a project dependency to the testCompile conf.
     * You don't want the other project to be build, if you do just a compile. The testCompile task is mapped to the
     * testCompile conf. With this knowledge we create a dependsOn relation ship between the testCompile task and the
     * task of the other project that produces the jar. This way a compile does not trigger the build of the other project,
     * but a testCompile does.
     * If a mapping between a task and a conf is not specified an implicit mapping is assumed which looks for a task
     * with the same name as the conf. But for example for the test task you have to specify an explicit mapping.
     *
     * @param conf the name of the conf
     * @param tasks the name of the tasks
     */
    void addConf2Tasks(String conf, String[] tasks);

    /**
     * Adds dependency descriptors to confs.
     *
     * @param confs
     * @param dependencies
     */
    void dependencies(List confs, Object[] dependencies);

    /**
     * Add instances of type <code>org.apache.ivy.core.module.descriptor.DependencyDescriptor</code>. Those
     * instances have an attribute to what confs they belong. There the confs don't need to be passed as an
     * argument.
     * 
     * @param dependencyDescriptors
     */
    void dependencyDescriptors(DependencyDescriptor[] dependencyDescriptors);

    /**
     * Adds artifacts for the given confs. An artifact is normally a library produced by the project. Usually this
     * method is not directly used by the build master. The archive tasks of the libs bundle call this method to
     * add the archive to the artifacts. 
     *
     * @param configurationName
     * @param artifacts
     */
    void addArtifacts(String configurationName, Object[] artifacts);

    /**
     * Adds an <code>org.apache.ivy.core.module.descriptor.Configuration</code> You would use this method if
     * you need to add a configuration with special attributes. For example a configuration that extends another
     * configuration.
     *  
     * @param configuration
     */
    void addConfiguration(Configuration configuration);

    /**
     * Adds a configuration with the given name. Under the hood an ivy configuration is created with default
     * attributes. 
     * @param configuration
     */
    void addConfiguration(String configuration);

    /**
     * Returns a list of file objects, denoting the path to the classpath elements belonging to this classname.
     *  
     * @param taskName
     * @return
     */
    List resolveClasspath(String taskName);

    ModuleRevisionId createModuleRevisionId();

    Object configure(Closure configureClosure);

    File getBuildResolverDir();
    
    RepositoryResolver getBuildResolver();
}
