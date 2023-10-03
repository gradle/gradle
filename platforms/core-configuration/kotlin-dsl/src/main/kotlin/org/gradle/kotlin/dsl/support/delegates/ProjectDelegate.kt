/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.support.delegates

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.AntBuilder
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.PathValidation
import org.gradle.api.Project
import org.gradle.api.ProjectState
import org.gradle.api.Task
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.ArtifactHandler
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.DependencyLockingHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.component.SoftwareComponentContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.FileTree
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.SyncSpec
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger
import org.gradle.api.logging.LoggingManager
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.plugins.PluginManager
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.resources.ResourceHandler
import org.gradle.api.shareddata.ProjectSharedData
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.WorkResult
import org.gradle.internal.accesscontrol.AllowUsingApiForExternalUse
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.normalization.InputNormalizationHandler
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec
import java.io.File
import java.net.URI
import java.util.concurrent.Callable


/**
 * Facilitates the implementation of the [Project] interface by delegation via subclassing.
 */
@Deprecated("Will be removed in Gradle 9.0")
abstract class ProjectDelegate : Project {

    init {
        @Suppress("DEPRECATION")
        if (!org.gradle.kotlin.dsl.precompile.PrecompiledProjectScript::class.java.isAssignableFrom(this::class.java)) {
            DeprecationLogger.deprecateType(ProjectDelegate::class.java)
                .willBeRemovedInGradle9()
                .undocumented()
                .nagUser()
        }
    }

    internal
    abstract val delegate: Project

    override fun getGroup(): Any =
        delegate.group

    override fun afterEvaluate(action: Action<in Project>) =
        delegate.afterEvaluate(action)

    override fun afterEvaluate(closure: Closure<*>) =
        delegate.afterEvaluate(closure)

    override fun getDefaultTasks(): MutableList<String> =
        delegate.defaultTasks

    @Deprecated("The concept of conventions is deprecated. Use extensions instead.")
    override fun getConvention(): @Suppress("deprecation") org.gradle.api.plugins.Convention =
        @Suppress("deprecation")
        delegate.convention

    override fun getLogger(): Logger =
        delegate.logger

    @Deprecated("Use layout.buildDirectory instead", ReplaceWith("layout.buildDirectory.get().asFile"))
    override fun getBuildDir(): File =
        @Suppress("DEPRECATION")
        delegate.buildDir

    override fun getAnt(): AntBuilder =
        delegate.ant

    override fun getVersion(): Any =
        delegate.version

    override fun getRootProject(): Project =
        delegate.rootProject

    override fun depthCompare(otherProject: Project): Int =
        delegate.depthCompare(otherProject)

    override fun getGradle(): Gradle =
        delegate.gradle

    override fun getAllTasks(recursive: Boolean): MutableMap<Project, MutableSet<Task>> =
        delegate.getAllTasks(recursive)

    override fun uri(path: Any): URI =
        delegate.uri(path)

    override fun copySpec(closure: Closure<*>): CopySpec =
        delegate.copySpec(closure)

    override fun copySpec(action: Action<in CopySpec>): CopySpec =
        delegate.copySpec(action)

    override fun copySpec(): CopySpec =
        delegate.copySpec()

    override fun relativePath(path: Any): String =
        delegate.relativePath(path)

    override fun setProperty(name: String, value: Any?) =
        delegate.setProperty(name, value)

    override fun beforeEvaluate(action: Action<in Project>) =
        delegate.beforeEvaluate(action)

    override fun beforeEvaluate(closure: Closure<*>) =
        delegate.beforeEvaluate(closure)

    override fun property(propertyName: String): Any? =
        delegate.property(propertyName)

    override fun buildscript(configureClosure: Closure<*>) =
        delegate.buildscript(configureClosure)

    override fun getProject(): Project =
        delegate.project

    override fun dependencies(configureClosure: Closure<*>) =
        delegate.dependencies(configureClosure)

    override fun getPath(): String =
        delegate.path

    override fun getBuildTreePath(): String =
        delegate.buildTreePath

    override fun zipTree(zipPath: Any): FileTree =
        delegate.zipTree(zipPath)

    override fun allprojects(action: Action<in Project>) =
        delegate.allprojects(action)

    override fun allprojects(configureClosure: Closure<*>) =
        delegate.allprojects(configureClosure)

    override fun <T : Any?> container(type: Class<T>): NamedDomainObjectContainer<T> =
        delegate.container(type)

    override fun <T : Any?> container(type: Class<T>, factory: NamedDomainObjectFactory<T>): NamedDomainObjectContainer<T> =
        delegate.container(type, factory)

    override fun <T : Any?> container(type: Class<T>, factoryClosure: Closure<*>): NamedDomainObjectContainer<T> =
        delegate.container(type, factoryClosure)

    override fun repositories(configureClosure: Closure<*>) =
        delegate.repositories(configureClosure)

    override fun evaluationDependsOnChildren() =
        delegate.evaluationDependsOnChildren()

    override fun configure(`object`: Any, configureClosure: Closure<*>): Any =
        delegate.configure(`object`, configureClosure)

    override fun configure(objects: Iterable<*>, configureClosure: Closure<*>): Iterable<*> =
        delegate.configure(objects, configureClosure)

    override fun <T : Any?> configure(objects: Iterable<T>, configureAction: Action<in T>): Iterable<T> =
        delegate.configure(objects, configureAction)

    override fun exec(closure: Closure<*>): ExecResult =
        delegate.exec(closure)

    override fun exec(action: Action<in ExecSpec>): ExecResult =
        delegate.exec(action)

    override fun sync(action: Action<in SyncSpec>): WorkResult =
        delegate.sync(action)

    override fun configurations(configureClosure: Closure<*>) =
        delegate.configurations(configureClosure)

    override fun getExtensions(): ExtensionContainer =
        delegate.extensions

    override fun getProperties(): MutableMap<String, *> =
        delegate.properties

    override fun absoluteProjectPath(path: String): String =
        delegate.absoluteProjectPath(path)

    override fun getProjectDir(): File =
        delegate.projectDir

    override fun files(vararg paths: Any?): ConfigurableFileCollection =
        delegate.files(*paths)

    override fun files(paths: Any, configureClosure: Closure<*>): ConfigurableFileCollection =
        delegate.files(paths, configureClosure)

    override fun files(paths: Any, configureAction: Action<in ConfigurableFileCollection>): ConfigurableFileCollection =
        delegate.files(paths, configureAction)

    override fun hasProperty(propertyName: String): Boolean =
        delegate.hasProperty(propertyName)

    override fun getState(): ProjectState =
        delegate.state

    override fun getComponents(): SoftwareComponentContainer =
        delegate.components

    override fun components(configuration: Action<in SoftwareComponentContainer>) =
        delegate.components(configuration)

    @Deprecated("Use layout.buildDirectory instead", ReplaceWith("layout.buildDirectory.set(path)"))
    override fun setBuildDir(path: File) {
        @Suppress("DEPRECATION")
        delegate.buildDir = path
    }

    @Deprecated("Use layout.buildDirectory instead", ReplaceWith("layout.buildDirectory.set(file(path))"))
    override fun setBuildDir(path: Any) =
        @Suppress("DEPRECATION")
        delegate.setBuildDir(path)

    override fun defaultTasks(vararg defaultTasks: String?) =
        delegate.defaultTasks(*defaultTasks)

    override fun compareTo(other: Project?): Int =
        delegate.compareTo(other)

    override fun artifacts(configureClosure: Closure<*>) =
        delegate.artifacts(configureClosure)

    override fun artifacts(configureAction: Action<in ArtifactHandler>) =
        delegate.artifacts(configureAction)

    override fun getRootDir(): File =
        delegate.rootDir

    override fun getDependencyLocking(): DependencyLockingHandler =
        delegate.dependencyLocking

    override fun <T : Any> provider(value: Callable<out T?>): Provider<T> =
        delegate.provider(value)

    override fun findProperty(propertyName: String): Any? =
        delegate.findProperty(propertyName)

    override fun getDependencies(): DependencyHandler =
        delegate.dependencies

    override fun getDependencyFactory(): DependencyFactory =
        delegate.dependencyFactory

    override fun getSharedData(): ProjectSharedData =
        delegate.sharedData

    override fun getResources(): ResourceHandler =
        delegate.resources

    override fun setDefaultTasks(defaultTasks: MutableList<String>) {
        delegate.defaultTasks = defaultTasks
    }

    override fun normalization(configuration: Action<in InputNormalizationHandler>) =
        delegate.normalization(configuration)

    override fun project(path: String): Project =
        delegate.project(path)

    override fun project(path: String, configureClosure: Closure<*>): Project =
        delegate.project(path, configureClosure)

    override fun project(path: String, configureAction: Action<in Project>): Project =
        delegate.project(path, configureAction)

    override fun task(name: String): Task =
        delegate.task(name)

    override fun task(args: Map<String, *>, name: String): Task =
        delegate.task(args, name)

    override fun task(args: Map<String, *>, name: String, configureClosure: Closure<*>): Task =
        delegate.task(args, name, configureClosure)

    override fun task(name: String, configureClosure: Closure<*>): Task =
        delegate.task(name, configureClosure)

    override fun task(name: String, configureAction: Action<in Task>): Task =
        delegate.task(name, configureAction)

    override fun copy(closure: Closure<*>): WorkResult =
        delegate.copy(closure)

    override fun copy(action: Action<in CopySpec>): WorkResult =
        delegate.copy(action)

    override fun getDescription(): String? =
        delegate.description

    override fun subprojects(action: Action<in Project>) =
        delegate.subprojects(action)

    override fun subprojects(configureClosure: Closure<*>) =
        delegate.subprojects(configureClosure)

    override fun getBuildscript(): ScriptHandler =
        delegate.buildscript

    override fun getStatus(): Any =
        delegate.status

    override fun mkdir(path: Any): File =
        delegate.mkdir(path)

    override fun setStatus(status: Any) {
        delegate.status = status
    }

    override fun getConfigurations(): ConfigurationContainer =
        delegate.configurations

    override fun getArtifacts(): ArtifactHandler =
        delegate.artifacts

    override fun setDescription(description: String?) {
        delegate.description = description
    }

    override fun getLayout(): ProjectLayout =
        delegate.layout

    override fun apply(closure: Closure<*>) =
        delegate.apply(closure)

    override fun apply(action: Action<in ObjectConfigurationAction>) =
        delegate.apply(action)

    override fun apply(options: Map<String, *>) =
        delegate.apply(options)

    override fun evaluationDependsOn(path: String): Project =
        delegate.evaluationDependsOn(path)

    override fun javaexec(closure: Closure<*>): ExecResult =
        delegate.javaexec(closure)

    override fun javaexec(action: Action<in JavaExecSpec>): ExecResult =
        delegate.javaexec(action)

    @AllowUsingApiForExternalUse
    override fun getChildProjects(): MutableMap<String, Project> =
        delegate.childProjects

    override fun getLogging(): LoggingManager =
        delegate.logging

    override fun getTasks(): TaskContainer =
        delegate.tasks

    override fun getName(): String =
        delegate.name

    override fun file(path: Any): File =
        delegate.file(path)

    override fun file(path: Any, validation: PathValidation): File =
        delegate.file(path, validation)

    override fun findProject(path: String): Project? =
        delegate.findProject(path)

    override fun getPlugins(): PluginContainer =
        delegate.plugins

    override fun ant(configureClosure: Closure<*>): AntBuilder =
        delegate.ant(configureClosure)

    override fun ant(configureAction: Action<in AntBuilder>): AntBuilder =
        delegate.ant(configureAction)

    override fun getAllprojects(): MutableSet<Project> =
        delegate.allprojects

    override fun createAntBuilder(): AntBuilder =
        delegate.createAntBuilder()

    override fun getObjects(): ObjectFactory =
        delegate.objects

    override fun dependencyLocking(configuration: Action<in DependencyLockingHandler>) =
        delegate.dependencyLocking(configuration)

    override fun tarTree(tarPath: Any): FileTree =
        delegate.tarTree(tarPath)

    override fun delete(vararg paths: Any?): Boolean =
        delegate.delete(*paths)

    override fun delete(action: Action<in DeleteSpec>): WorkResult =
        delegate.delete(action)

    override fun getRepositories(): RepositoryHandler =
        delegate.repositories

    override fun getTasksByName(name: String, recursive: Boolean): MutableSet<Task> =
        delegate.getTasksByName(name, recursive)

    override fun getParent(): Project? =
        delegate.parent

    override fun getDisplayName(): String =
        delegate.displayName

    override fun relativeProjectPath(path: String): String =
        delegate.relativeProjectPath(path)

    override fun getPluginManager(): PluginManager =
        delegate.pluginManager

    override fun setGroup(group: Any) {
        delegate.group = group
    }

    override fun fileTree(baseDir: Any): ConfigurableFileTree =
        delegate.fileTree(baseDir)

    override fun fileTree(baseDir: Any, configureClosure: Closure<*>): ConfigurableFileTree =
        delegate.fileTree(baseDir, configureClosure)

    override fun fileTree(baseDir: Any, configureAction: Action<in ConfigurableFileTree>): ConfigurableFileTree =
        delegate.fileTree(baseDir, configureAction)

    override fun fileTree(args: Map<String, *>): ConfigurableFileTree =
        delegate.fileTree(args)

    override fun getNormalization(): InputNormalizationHandler =
        delegate.normalization

    override fun setVersion(version: Any) {
        delegate.version = version
    }

    override fun getDepth(): Int =
        delegate.depth

    override fun getProviders(): ProviderFactory =
        delegate.providers

    override fun getSubprojects(): MutableSet<Project> =
        delegate.subprojects

    override fun getBuildFile(): File =
        delegate.buildFile
}
