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

package org.gradle.api.internal.dependencies

import org.apache.ivy.Ivy
import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.descriptor.Configuration
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.plugins.resolver.DualResolver
import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.apache.ivy.plugins.resolver.RepositoryResolver
import org.gradle.api.DependencyManager
import org.gradle.api.InvalidUserDataException
import org.gradle.api.dependencies.GradleArtifact
import org.gradle.api.dependencies.ResolverContainer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 */
class DefaultDependencyManager extends DefaultDependencyContainer implements DependencyManager {
    private static Logger logger = LoggerFactory.getLogger(DefaultDependencyManager)

    /**
     * A map where the key is the name of the configuration and the values are Ivy configuration objects.
     */
    Map<String, Configuration> configurations = [:]

    /**
     * A map where the key is the name of the configuration and the value are Gradles Artifact objects.
     */
    Map<String, List<GradleArtifact>> artifacts = [:]

    /**
     * A list for passing directly instances of Ivy Artifact objects.
     */
    Map<String, List<Artifact>> artifactDescriptors = [:]

    /**
     * Ivy patterns to tell Ivy where to look for artifacts when publishing the module.
     */
    List<String> absoluteArtifactPatterns = []

    List<File> artifactParentDirs = []

    String defaultArtifactPattern = DependencyManager.DEFAULT_ARTIFACT_PATTERN

    ArtifactFactory artifactFactory

    IIvyFactory ivyFactory

    SettingsConverter settingsConverter

    ModuleDescriptorConverter moduleDescriptorConverter

    IDependencyResolver dependencyResolver

    IDependencyPublisher dependencyPublisher

    LocalReposCacheHandler localReposCacheHandler = new LocalReposCacheHandler()

    BuildResolverHandler buildResolverHandler = new BuildResolverHandler(localReposCacheHandler)

    ResolverContainer classpathResolvers = new ResolverContainer(localReposCacheHandler)

    /**
     * The name of the task which produces the artifacts of this project. This is needed by other projects,
     * which have a dependency on a project.
     */
    String artifactProductionTaskName

    /**
     * A map where the key is the name of the configuration and the value is the name of a task. This is needed
     * to deal with project dependencies. In case of a project dependency, we need to establish a dependsOn relationship,
     * between a task of the project and the task of the dependsOn project, which builds the artifacts. The default is,
     * that the project task is used, which has the same name as the configuration. If this is not what is wanted,
     * the mapping can be specified via this map.
     */
    Map confs4Task = [:]
    Map tasks4Conf = [:]

    /**
     * All the classpath resolvers are contained in an Ivy chain resolver. With this closure you can configure the
     * chain resolver if necessary.
     */
    Closure chainConfigurer

    boolean failForMissingDependencies = true

    Map resolveCache = [:]

    DefaultDependencyManager() {

    }

    DefaultDependencyManager(IIvyFactory ivyFactory, DependencyFactory dependencyFactory, ArtifactFactory artifactFactory,
                             SettingsConverter settingsConverter, ModuleDescriptorConverter moduleDescriptorConverter,
                             IDependencyResolver dependencyResolver, IDependencyPublisher dependencyPublisher,
                             File buildResolverDir) {
        super(dependencyFactory, [])
        assert buildResolverDir
        this.ivyFactory = ivyFactory
        this.artifactFactory = artifactFactory
        this.settingsConverter = settingsConverter
        this.moduleDescriptorConverter = moduleDescriptorConverter
        this.dependencyResolver = dependencyResolver
        this.dependencyPublisher = dependencyPublisher
        this.localReposCacheHandler.buildResolverDir = buildResolverDir
        this.buildResolverHandler.buildResolverDir = buildResolverDir
    }

    List resolve(String conf) {
        return dependencyResolver.resolve(conf, getIvy(), moduleDescriptorConverter.convert(this),
                failForMissingDependencies);
    }

    List resolveTask(String taskName) {
        Set confs = confs4Task[taskName]
        if (!confs) {
            throw new InvalidUserDataException("Task $taskName is not mapped to any conf!")
        }
        confs.inject([]) {List allPaths, String conf ->
            allPaths.addAll(resolve(conf))
            allPaths
        }
    }

    String antpath(String conf) {
        resolve(conf).join(':')
    }

    void publish(List configurations, ResolverContainer resolvers, boolean uploadModuleDescriptor) {
        dependencyPublisher.publish(
                configurations,
                resolvers,
                moduleDescriptorConverter.convert(this),
                uploadModuleDescriptor,
                new File(getProject().getBuildDir(), "ivy.xml"),
                this);
    }

    ModuleRevisionId createModuleRevisionId() {
        def group = DependencyManager.DEFAULT_GROUP
        def version = DependencyManager.DEFAULT_VERSION
        if (project.hasProperty('group') && project.group) {
            group = project.group
        }
        if (project.hasProperty('version') && project.version) {
            version = project.version
        }
        new ModuleRevisionId(new ModuleId(group as String, project.name as String), version as String)
    }

    Ivy getIvy() {
        ivy([])
    }

    Ivy ivy(List resolvers) {
        return ivyFactory.createIvy(settingsConverter.convert(classpathResolvers.resolverList,
                resolvers,
                new File(project.gradleUserHome), buildResolver, clientModuleRegistry, chainConfigurer))
    }

    DependencyManager linkConfWithTask(String conf, String task) {
        if (!conf || !task) {throw new InvalidUserDataException('Conf and tasks must be specified!')}
        if (!tasks4Conf[conf]) { tasks4Conf[conf] = [] as Set }
        if (!confs4Task[task]) { confs4Task[task] = [] as Set }
        tasks4Conf[conf] << task
        confs4Task[task] << conf
        this
    }

    DependencyManager unlinkConfWithTask(String conf, String task) {
        if (!conf || !task) {throw new InvalidUserDataException('Conf and tasks must be specified!')}
        if (!tasks4Conf[conf] || !tasks4Conf[conf].contains(task)) {
            throw new InvalidUserDataException("Can not unlink Conf= $conf and Task=$task because they are not linked!");
        }
        tasks4Conf[conf].remove(task)
        assert confs4Task[task]
        confs4Task[task].remove(conf)
        this
    }

    void addArtifacts(String configurationName, Object[] artifacts) {
        if (!this.artifacts[configurationName]) {
            this.artifacts[configurationName] = []
        }
        (artifacts as List).flatten().each {
            GradleArtifact gradleArtifact = artifactFactory.createGradleArtifact(it)
            logger.debug("Adding {} to configuration={}", gradleArtifact, configurationName)
            this.artifacts[configurationName] << gradleArtifact
        }
    }

    DependencyManager addConfiguration(Configuration configuration) {
        configurations[configuration.name] = configuration
        this
    }

    DependencyManager addConfiguration(String configuration) {
        configurations[configuration] = new Configuration(configuration)
        this
    }

    def methodMissing(String name, args) {
        if (!configurations[name]) {
            if (!getMetaClass().respondsTo(this, name, args)) {
                throw new MissingMethodException(name, this.getClass(), args)
            }
            return getMetaClass().invokeMethod(this, name, args)
        }
        dependencies([name], args as Object[])
    }

    RepositoryResolver getBuildResolver() {
        buildResolverHandler.buildResolver
    }

    File getBuildResolverDir() {
        buildResolverHandler.buildResolverDir
    }

    FileSystemResolver addFlatDirResolver(String name, Object[] dirs) {
        classpathResolvers.add(classpathResolvers.createFlatDirResolver(name,
                dirs.collect {new File(it.toString())} as File[]))
    }

    DualResolver addMavenRepo(String[] jarRepoUrls) {
        classpathResolvers.add(classpathResolvers.createMavenRepoResolver(DependencyManager.DEFAULT_MAVEN_REPO_NAME,
                DependencyManager.MAVEN_REPO_URL, jarRepoUrls))
    }

    DualResolver addMavenStyleRepo(String name, String root, String[] jarRepoUrls) {
        classpathResolvers.add(classpathResolvers.createMavenRepoResolver(name, root, jarRepoUrls))
    }


}

