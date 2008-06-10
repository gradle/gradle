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
import org.apache.ivy.core.module.descriptor.Configuration
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.publish.PublishOptions
import org.apache.ivy.core.report.ResolveReport
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.plugins.resolver.DualResolver
import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.apache.ivy.plugins.resolver.RepositoryResolver
import org.gradle.api.DependencyManager
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.dependencies.GradleArtifact
import org.gradle.api.dependencies.ResolverContainer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.gradle.util.Clock

/**
 * @author Hans Dockter
 */
class DefaultDependencyManager extends DefaultDependencyContainer implements DependencyManager {
    private static Logger logger = LoggerFactory.getLogger(DefaultDependencyManager)

    /**
     * A map where the key is the name of the configuration and the values are Ivy configuration objects.
     */
    Map configurations = [:]

    /**
     * A map where the key is the name of the configuration and the value are Gradles Artifact objects.
     */
    Map artifacts = [:]

    /**
     * A list for passing directly instances of Ivy Artifact objects.
     */
    Map artifactDescriptors = [:]

    /**
     * Ivy patterns to tell Ivy where to look for artifacts when publishing the module.
     */
    List artifactPatterns = []

    ArtifactFactory artifactFactory

    Ivy ivy

    SettingsConverter settingsConverter

    ModuleDescriptorConverter moduleDescriptorConverter

    Report2Classpath report2Classpath

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
    Map conf2Tasks = [:]
    Map task2Conf = [:]

    /**
     * All the classpath resolvers are contained in an Ivy chain resolver. With this closure you can configure the
     * chain resolver if necessary.
     */
    Closure chainConfigurer

    boolean failForMissingDependencies = true

    Map resolveCache = [:]

    DefaultDependencyManager() {

    }

    DefaultDependencyManager(Ivy ivy, DependencyFactory dependencyFactory, ArtifactFactory artifactFactory,
                             SettingsConverter settingsConverter, ModuleDescriptorConverter moduleDescriptorConverter,
                             Report2Classpath report2Classpath, File buildResolverDir) {
        super(dependencyFactory, [])
        assert buildResolverDir
        this.ivy = ivy
        this.artifactFactory = artifactFactory
        this.settingsConverter = settingsConverter
        this.moduleDescriptorConverter = moduleDescriptorConverter
        this.report2Classpath = report2Classpath
        this.localReposCacheHandler.buildResolverDir = buildResolverDir
        this.buildResolverHandler.buildResolverDir = buildResolverDir
    }

    List resolve(String conf) {
        Clock clock = new Clock()
        if (resolveCache.keySet().contains(conf)) {
            return resolveCache[conf]
        }
        ivy = getIvy()
        //        ivy.configure(new File('/Users/hans/IdeaProjects/gradle/gradle-core/ivysettings.xml'))
        ModuleDescriptor moduleDescriptor = moduleDescriptorConverter.convert(this)
        ResolveOptions resolveOptions = new ResolveOptions()
        resolveOptions.setConfs([conf] as String[])
        resolveOptions.outputReport = false
        Clock ivyClock = new Clock()
        ResolveReport resolveReport = ivy.resolve(moduleDescriptor, resolveOptions)
        logger.debug("Timing: Ivy resolve took " + clock.time)
        if (resolveReport.hasError() && failForMissingDependencies) {
            throw new GradleException("Not all dependencies could be resolved!")
        }
        resolveCache[conf] = report2Classpath.getClasspath(conf, resolveReport)
        logger.debug("Timing: Complete resolve took " + clock.time)
        resolveCache[conf]
    }

    List resolveTask(String taskName) {
        String conf = task2Conf[taskName]
        if (!conf) {
            throw new InvalidUserDataException("Task $taskName is not mapped to any conf!")
        }
        resolve(conf)
    }

    String antpath(String conf) {
        resolve(conf).join(':')
    }

    void publish(List configurations, ResolverContainer resolvers, boolean uploadModuleDescriptor) {
        PublishOptions publishOptions = new PublishOptions()
        ModuleDescriptor moduleDescriptor = moduleDescriptorConverter.convert(this)
        if (uploadModuleDescriptor) {
            File ivyFile = new File(project.buildDir, 'ivy.xml')
            moduleDescriptor.toIvyFile(ivyFile)
            publishOptions.srcIvyPattern = ivyFile.absolutePath
        }
        publishToResolvers(configurations, resolvers, moduleDescriptor, publishOptions)
    }

    private void publishToResolvers(List configurations, ResolverContainer resolvers, ModuleDescriptor moduleDescriptor,
                                    PublishOptions publishOptions) {
        publishOptions.setOverwrite(true)
        publishOptions.confs = configurations
        Ivy ivy = ivy(resolvers.resolverList)
        resolvers.resolverList.each {resolver ->
            logger.info("Publishing to Resolver $resolver")
            ivy.publishEngine.publish(moduleDescriptor,
                    artifactPatterns.collect {pattern -> project.file(pattern).absolutePath}, resolver, publishOptions)
        }
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
        ivy = ivy.newInstance(settingsConverter.convert(classpathResolvers.resolverList,
                resolvers,
                new File(project.gradleUserHome), buildResolver, clientModuleRegistry, chainConfigurer))
    }

    void addConf2Tasks(String conf, String[] tasks) {
        if (!conf || !tasks) {throw new InvalidUserDataException('Conf and tasks must be specified!')}
        removeTaskForOldConfMapping(conf)
        conf2Tasks[conf] = tasks as Set
        tasks.each {task2Conf[it] = conf}
    }

    private void removeTaskForOldConfMapping(String conf) {
        if (conf2Tasks[conf]) {
            List task2Remove = []
            task2Conf.each {key, value ->
                if (value == conf) {
                    task2Remove << key
                }
            }
            task2Remove.each {task2Conf.remove(it)}
        }
    }

    void addArtifacts(String configurationName, Object[] artifacts) {
        if (!this.artifacts[configurationName]) {
            this.artifacts[configurationName] = []
        }
        (artifacts as List).flatten().each {
            GradleArtifact gradleArtifact = artifactFactory.createGradleArtifact(it)
            logger.debug("Adding $gradleArtifact to configuration=$configurationName")
            this.artifacts[configurationName] << gradleArtifact
        }
    }

    void addConfiguration(Configuration configuration) {
        configurations[configuration.name] = configuration
    }

    void addConfiguration(String configuration) {
        configurations[configuration] = new Configuration(configuration)
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

