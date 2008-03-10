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
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.report.ResolveReport
import org.apache.ivy.core.resolve.ResolveOptions
import org.gradle.api.DependencyManager
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.dependencies.ModuleDependency
import org.gradle.api.tasks.util.GradleUtil

/**
* @author Hans Dockter
* todo: map tasks to configurations not vice versa
*/
class DefaultDependencyManager implements DependencyManager {
    Project project

    Ivy ivy

    DependencyFactory dependencyFactory

    ArtifactFactory artifactFactory

    SettingsConverter settingsConverter

    ModuleDescriptorConverter moduleDescriptorConverter

    Report2Classpath report2Classpath

    ResolverContainer uploadResolvers = new ResolverContainer()

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

    ResolverContainer resolvers = new ResolverContainer()

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

    DefaultDependencyManager() {

    }

    DefaultDependencyManager(Ivy ivy, DependencyFactory dependencyFactory, ArtifactFactory artifactFactory,
                             SettingsConverter settingsConverter, ModuleDescriptorConverter moduleDescriptorConverter, Report2Classpath report2Classpath) {
        this.ivy = ivy
        this.dependencyFactory = dependencyFactory
        this.artifactFactory = artifactFactory
        this.settingsConverter = settingsConverter
        this.moduleDescriptorConverter = moduleDescriptorConverter
        this.report2Classpath = report2Classpath
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

    ModuleRevisionId createModuleRevisionId() {
        new ModuleRevisionId(new ModuleId(project.group, project.name), project.version)
    }

    Ivy getIvy() {
        ivy = ivy.newInstance(settingsConverter.convert(resolvers.resolverList, uploadResolvers.resolverList, new File(project.gradleUserHome)))
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
}
