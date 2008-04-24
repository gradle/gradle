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
import org.apache.ivy.plugins.resolver.RepositoryResolver
import org.gradle.api.DependencyManager
import org.gradle.api.InvalidUserDataException
import org.gradle.api.dependencies.GradleArtifact
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.apache.ivy.plugins.resolver.FileSystemResolver
import org.apache.ivy.plugins.resolver.IBiblioResolver
import org.gradle.api.dependencies.ResolverContainer
import org.gradle.api.GradleException

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

    ResolverContainer classpathResolvers = new ResolverContainer()

    SpecialResolverHandler specialResolverHandler = new SpecialResolverHandler()

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

    boolean failForMissingDependencies = true

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
        this.specialResolverHandler.buildResolverDir = buildResolverDir
    }

    // todo: Build should fail, if resolve fails (at least optional) 
    List resolveClasspath(String taskName) {
        String conf = task2Conf[taskName] ?: taskName
        ivy = getIvy()
        //        ivy.configure(new File('/Users/hans/IdeaProjects/gradle/gradle-core/ivysettings.xml'))
        ModuleDescriptor moduleDescriptor = moduleDescriptorConverter.convert(this)
        ResolveOptions resolveOptions = new ResolveOptions()
        resolveOptions.setConfs([conf] as String[])
        resolveOptions.outputReport = false
        ResolveReport resolveReport = ivy.resolve(moduleDescriptor, resolveOptions)
        if (resolveReport.hasError() && failForMissingDependencies) {
            throw new GradleException("Not all dependencies could be resolved!")
        }
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
        new ModuleRevisionId(new ModuleId(project.group as String, project.name as String), project.version as String)
    }

    Ivy getIvy() {
        ivy([])
    }

    Ivy ivy(List resolvers) {
        ivy = ivy.newInstance(settingsConverter.convert(classpathResolvers.resolverList,
                resolvers,
                new File(project.gradleUserHome), buildResolver, clientModuleRegistry))
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
        specialResolverHandler.buildResolver
    }

    File getBuildResolverDir() {
        specialResolverHandler.buildResolverDir
    }

    FileSystemResolver createFlatDirResolver(String name, File[] dirs) {
        specialResolverHandler.createFlatDirResolver(name, dirs)
    }

    FileSystemResolver addFlatDirResolver(String name, File[] dirs) {
        FileSystemResolver resolver = createFlatDirResolver(name, dirs)
        classpathResolvers.add(resolver)
        resolver
    }

    IBiblioResolver addMavenRepo() {
        classpathResolvers.add([name: DependencyManager.DEFAULT_MAVEN_REPO_NAME, url: DependencyManager.MAVEN_REPO_URL])
    }


}

