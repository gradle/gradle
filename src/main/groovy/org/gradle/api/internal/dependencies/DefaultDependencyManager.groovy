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
import org.apache.ivy.core.cache.DefaultRepositoryCacheManager
import org.apache.ivy.core.module.descriptor.Configuration
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.publish.PublishOptions
import org.apache.ivy.core.report.ResolveReport
import org.apache.ivy.core.resolve.ResolveOptions
import org.apache.ivy.plugins.lock.NoLockStrategy
import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.apache.ivy.plugins.resolver.RepositoryResolver
import org.gradle.api.DependencyManager
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.dependencies.ModuleDependency
import org.gradle.util.GradleUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 * todo: map tasks to configurations not vice versa
 */
class DefaultDependencyManager implements DependencyManager {
    Logger logger = LoggerFactory.getLogger(DefaultDependencyManager)

    Project project

    Ivy ivy

    DependencyFactory dependencyFactory

    ArtifactFactory artifactFactory

    SettingsConverter settingsConverter

    ModuleDescriptorConverter moduleDescriptorConverter

    Report2Classpath report2Classpath

    /**
     * A map where the key is the name of the configuration and the values are Ivy configuration objects.
     */
    Map configurations = [:]

    /**
     * A list of Gradle Dependency objects.
     */
    List dependencies = []

    /**
     * A list for passing directly instances of Ivy DependencyDescriptor objects.
     */
    List dependencyDescriptors = []

    ResolverContainer classpathResolvers = new ResolverContainer()

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

    File buildResolverDir

    RepositoryResolver buildResolver = null

    DefaultDependencyManager() {

    }

    DefaultDependencyManager(Ivy ivy, DependencyFactory dependencyFactory, ArtifactFactory artifactFactory,
                             SettingsConverter settingsConverter, ModuleDescriptorConverter moduleDescriptorConverter,
                             Report2Classpath report2Classpath, File buildResolverDir) {
        assert buildResolverDir
        this.ivy = ivy
        this.dependencyFactory = dependencyFactory
        this.artifactFactory = artifactFactory
        this.settingsConverter = settingsConverter
        this.moduleDescriptorConverter = moduleDescriptorConverter
        this.report2Classpath = report2Classpath
        this.buildResolverDir = buildResolverDir
    }

    DependencyManager configure(Closure closure) {
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.delegate = this
        closure()
        this
    }

    void addDependencies(List confs, Object[] dependencies) {
        (dependencies as List).flatten().each {
            this.dependencies << dependencyFactory.createDependency(confs as Set, it, project)
        }
    }

    ModuleDependency addDependency(Map args, Closure configureClosure = null) {
        def dependency = dependencyFactory.createDependency(args.confs as Set, args.id as String, project)
        dependencies << dependency
        GradleUtil.configure(configureClosure, dependency)
        dependency
    }

    void addArtifacts(String configurationName, Object[] artifacts) {
        if (!this.artifacts[configurationName]) {
            this.artifacts[configurationName] = []
        }
        (artifacts as List).flatten().each {
            this.artifacts[configurationName] << artifactFactory.createGradleArtifact(it)
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
        addDependencies([name], args as Object[])
    }

    void addDependencyDescriptors(DependencyDescriptor[] dependencyDescriptors) {
        this.dependencyDescriptors.addAll(dependencyDescriptors as List)
    }

    List resolveClasspath(String taskName) {
        String conf = task2Conf[taskName] ?: taskName
        ivy = getIvy()
        //        ivy.configure(new File('/Users/hans/IdeaProjects/gradle/gradle-core/ivysettings.xml'))
        ModuleDescriptor moduleDescriptor = moduleDescriptorConverter.convert(this)
        ResolveOptions resolveOptions = new ResolveOptions()
        resolveOptions.setConfs([conf] as String[])
        ResolveReport resolveReport = ivy.resolve(moduleDescriptor, resolveOptions)
        report2Classpath.getClasspath(conf, resolveReport)
    }

    void publish(List configurations, ResolverContainer resolvers, boolean uploadModuleDescriptor) {
        PublishOptions publishOptions = new PublishOptions()
        ModuleDescriptor moduleDescriptor = moduleDescriptorConverter.convert(this)
        if (uploadModuleDescriptor) {
            File ivyFile = new File(project.buildDir, 'ivy.xml')
            moduleDescriptor.toIvyFile(ivyFile)
            publishOptions.srcIvyPattern = ivyFile.absolutePath
        }
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
        new ModuleRevisionId(new ModuleId(project.group as String, project.name as String), project.version as String)
    }

    Ivy getIvy() {
        ivy([])
    }

    Ivy ivy(List resolvers) {
        ivy = ivy.newInstance(settingsConverter.convert(classpathResolvers.resolverList,
                resolvers,
                new File(project.gradleUserHome), getBuildResolver()))
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

    RepositoryResolver getBuildResolver() {
        if (!buildResolver) {
            assert buildResolverDir
            DefaultRepositoryCacheManager cacheManager = new DefaultRepositoryCacheManager()
            cacheManager.basedir = new File(buildResolverDir, 'cache')
            cacheManager.name = 'build-resolver-cache'
            cacheManager.useOrigin = true
            cacheManager.lockStrategy = new NoLockStrategy()
            buildResolver = new FileSystemResolver()
            buildResolver.setRepositoryCacheManager(cacheManager)
            buildResolver.name = DependencyManager.BUILD_RESOLVER_NAME
            String pattern = "$buildResolverDir.absolutePath/$DependencyManager.BUILD_RESOLVER_PATTERN"
            buildResolver.addIvyPattern(pattern)
            buildResolver.addArtifactPattern(pattern)
        }
        buildResolver
    }
}
