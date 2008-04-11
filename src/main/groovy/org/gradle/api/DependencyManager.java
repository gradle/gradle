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

    void dependencies(List confs, Object[] dependencies);

    void addArtifacts(String configurationName, Object[] artifacts);

    void addConfiguration(Configuration configuration);

    void addConfiguration(String configuration);

    void dependencyDescriptors(DependencyDescriptor[] dependencyDescriptors);

    List resolveClasspath(String configurationName);

    ModuleRevisionId createModuleRevisionId();

    Object configure(Closure configureClosure);

    File getBuildResolverDir();
    
    RepositoryResolver getBuildResolver();
}
