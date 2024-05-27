/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache

import groovy.lang.Closure
import groovy.lang.GroovyObjectSupport
import groovy.lang.GroovyRuntimeException
import groovy.lang.Script
import org.gradle.api.Action
import org.gradle.api.AntBuilder
import org.gradle.api.project.IsolatedProject
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.PathValidation
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.Task
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
import org.gradle.api.internal.DynamicObjectAware
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.ProcessOperations
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.api.internal.project.CrossProjectModelAccess
import org.gradle.api.internal.project.ProjectIdentifier
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.project.ProjectStateInternal
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyUsageTracker
import org.gradle.api.logging.Logger
import org.gradle.api.logging.LoggingManager
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.resources.ResourceHandler
import org.gradle.api.tasks.WorkResult
import org.gradle.configuration.ConfigurationTargetIdentifier
import org.gradle.configuration.project.ProjectConfigurationActionContainer
import org.gradle.configurationcache.CrossProjectModelAccessPattern.ALLPROJECTS
import org.gradle.configurationcache.CrossProjectModelAccessPattern.CHILD
import org.gradle.configurationcache.CrossProjectModelAccessPattern.DIRECT
import org.gradle.configurationcache.CrossProjectModelAccessPattern.SUBPROJECT
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.configuration.problems.ProblemFactory
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.accesscontrol.AllowUsingApiForExternalUse
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.logging.StandardOutputCapture
import org.gradle.internal.metaobject.BeanDynamicObject
import org.gradle.internal.metaobject.DynamicInvokeResult
import org.gradle.internal.metaobject.DynamicObject
import org.gradle.internal.model.ModelContainer
import org.gradle.internal.model.RuleBasedPluginListener
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.normalization.InputNormalizationHandler
import org.gradle.normalization.internal.InputNormalizationHandlerInternal
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec
import org.gradle.util.Path
import org.gradle.util.internal.ConfigureUtil
import java.io.File
import java.net.URI
import java.util.concurrent.Callable


internal
class ProblemReportingCrossProjectModelAccess(
    private val delegate: CrossProjectModelAccess,
    private val problems: ProblemsListener,
    private val coupledProjectsListener: CoupledProjectsListener,
    private val problemFactory: ProblemFactory,
    private val dynamicCallProblemReporting: DynamicCallProblemReporting,
    private val buildModelParameters: BuildModelParameters
) : CrossProjectModelAccess {
    override fun findProject(referrer: ProjectInternal, relativeTo: ProjectInternal, path: String): ProjectInternal? {
        return delegate.findProject(referrer, relativeTo, path)?.let {
            it.wrap(referrer, CrossProjectModelAccessInstance(DIRECT, it))
        }
    }

    override fun access(referrer: ProjectInternal, project: ProjectInternal): ProjectInternal {
        return project.wrap(referrer, CrossProjectModelAccessInstance(DIRECT, project))
    }

    override fun getChildProjects(referrer: ProjectInternal, relativeTo: ProjectInternal): MutableMap<String, Project> {
        return delegate.getChildProjects(referrer, relativeTo).mapValuesTo(LinkedHashMap()) {
            (it.value as ProjectInternal).wrap(referrer, CrossProjectModelAccessInstance(CHILD, relativeTo))
        }
    }

    override fun getSubprojects(referrer: ProjectInternal, relativeTo: ProjectInternal): MutableSet<out ProjectInternal> {
        return delegate.getSubprojects(referrer, relativeTo).mapTo(LinkedHashSet()) {
            it.wrap(referrer, CrossProjectModelAccessInstance(SUBPROJECT, relativeTo))
        }
    }

    override fun getAllprojects(referrer: ProjectInternal, relativeTo: ProjectInternal): MutableSet<out ProjectInternal> {
        return delegate.getAllprojects(referrer, relativeTo).mapTo(LinkedHashSet()) {
            it.wrap(referrer, CrossProjectModelAccessInstance(ALLPROJECTS, relativeTo))
        }
    }

    override fun gradleInstanceForProject(referrerProject: ProjectInternal, gradle: GradleInternal): GradleInternal {
        return CrossProjectConfigurationReportingGradle.from(gradle, referrerProject)
    }

    override fun taskDependencyUsageTracker(referrerProject: ProjectInternal): TaskDependencyUsageTracker {
        return ReportingTaskDependencyUsageTracker(referrerProject, coupledProjectsListener, problems, problemFactory)
    }

    override fun taskGraphForProject(referrerProject: ProjectInternal, taskGraph: TaskExecutionGraphInternal): TaskExecutionGraphInternal {
        return CrossProjectConfigurationReportingTaskExecutionGraph(taskGraph, referrerProject, problems, this, coupledProjectsListener, problemFactory)
    }

    override fun parentProjectDynamicInheritedScope(referrerProject: ProjectInternal): DynamicObject? {
        val parent = referrerProject.parent ?: return null
        return CrossProjectModelAccessTrackingParentDynamicObject(
            parent, parent.inheritedScope, referrerProject, problems, coupledProjectsListener, problemFactory, dynamicCallProblemReporting
        )
    }

    private
    fun ProjectInternal.wrap(
        referrer: ProjectInternal,
        access: CrossProjectModelAccessInstance,
    ): ProjectInternal {
        return if (this == referrer) {
            this
        } else {
            ProblemReportingProject(this, referrer, access, problems, coupledProjectsListener, problemFactory, buildModelParameters, dynamicCallProblemReporting)
        }
    }

    private
    class ProblemReportingProject(
        val delegate: ProjectInternal,
        val referrer: ProjectInternal,
        val access: CrossProjectModelAccessInstance,
        val problems: ProblemsListener,
        val coupledProjectsListener: CoupledProjectsListener,
        val problemFactory: ProblemFactory,
        val buildModelParameters: BuildModelParameters,
        val dynamicCallProblemReporting: DynamicCallProblemReporting,
    ) : ProjectInternal, GroovyObjectSupport() {

        override fun toString(): String {
            return delegate.toString()
        }

        override fun equals(other: Any?): Boolean {
            if (other === this) {
                return true
            }
            if (other == null || other.javaClass != javaClass) {
                return false
            }
            val project = other as ProblemReportingProject
            return delegate == project.delegate && referrer == project.referrer // do not include `access`
        }

        override fun hashCode(): Int {
            return delegate.hashCode()
        }

        override fun getProperty(propertyName: String): Any? {
            // Attempt to get the property value via this instance. If not present, then attempt to lookup via the delegate
            val thisBean = BeanDynamicObject(this).withNotImplementsMissing()
            val result = thisBean.tryGetProperty(propertyName)
            if (result.isFound) {
                return result.value
            }

            onProjectsCoupled()

            return withDelegateDynamicCallReportingConfigurationOrder(
                propertyName,
                action = { tryGetProperty(propertyName) },
                resultNotFoundExceptionProvider = { getMissingProperty(propertyName) }
            )
        }

        override fun invokeMethod(name: String, args: Any): Any? {
            // Attempt to get the property value via this instance. If not present, then attempt to lookup via the delegate
            val varargs: Array<Any?> = args.uncheckedCast()
            val thisBean = BeanDynamicObject(this).withNotImplementsMissing()
            val result = thisBean.tryInvokeMethod(name, *varargs)
            if (result.isFound) {
                return result.value
            }

            onProjectsCoupled()

            return withDelegateDynamicCallReportingConfigurationOrder(
                name,
                action = { tryInvokeMethod(name, *varargs) },
                resultNotFoundExceptionProvider = { methodMissingException(name, *varargs) }
            )
        }

        override fun compareTo(other: Project?): Int {
            return delegate.compareTo(other)
        }

        override fun getRootDir(): File {
            return delegate.rootDir
        }

        @Deprecated("Use layout.buildDirectory instead")
        override fun getBuildDir(): File {
            onAccess("buildDir")
            @Suppress("DEPRECATION")
            return delegate.buildDir
        }

        @Deprecated("Use layout.buildDirectory instead")
        override fun setBuildDir(path: File) {
            onAccess("buildDir")
            @Suppress("DEPRECATION")
            delegate.buildDir = path
        }

        @Deprecated("Use layout.buildDirectory instead")
        override fun setBuildDir(path: Any) {
            onAccess("buildDir")
            @Suppress("DEPRECATION")
            delegate.setBuildDir(path)
        }

        override fun getName(): String {
            return delegate.name
        }

        override fun getBuildFile(): File {
            return delegate.buildFile
        }

        override fun getDisplayName(): String {
            return delegate.displayName
        }

        override fun getDescription(): String? {
            onAccess("description")
            return delegate.description
        }

        override fun setDescription(description: String?) {
            onAccess("description")
            delegate.description = description
        }

        override fun getGroup(): Any {
            onAccess("group")
            return delegate.group
        }

        override fun setGroup(group: Any) {
            onAccess("group")
            delegate.group = group
        }

        override fun getVersion(): Any {
            onAccess("version")
            return delegate.version
        }

        override fun setVersion(version: Any) {
            onAccess("version")
            delegate.version = version
        }

        override fun getStatus(): Any {
            onAccess("status")
            return delegate.status
        }

        override fun getInternalStatus(): Property<Any> {
            onAccess("internalStatus")
            return delegate.internalStatus
        }

        override fun setStatus(status: Any) {
            onAccess("status")
            delegate.status = status
        }

        @AllowUsingApiForExternalUse
        override fun getChildProjects(): MutableMap<String, Project> {
            return delegate.childProjects
        }

        override fun getChildProjectsUnchecked(): MutableMap<String, Project> {
            return delegate.childProjectsUnchecked
        }

        override fun setProperty(name: String, value: Any?) {
            onAccess("property")
            delegate.setProperty(name, value)
        }

        override fun getProject(): ProjectInternal {
            return this
        }

        override fun getIsolated(): IsolatedProject {
            return delegate.isolated
        }

        override fun task(name: String): Task {
            onAccess("task")
            return delegate.task(name)
        }

        override fun task(args: MutableMap<String, *>, name: String): Task {
            onAccess("task")
            return delegate.task(args, name)
        }

        override fun task(args: MutableMap<String, *>, name: String, configureClosure: Closure<*>): Task {
            onAccess("task")
            return delegate.task(args, name, configureClosure)
        }

        override fun task(name: String, configureClosure: Closure<*>): Task {
            onAccess("task")
            return delegate.task(name, configureClosure)
        }

        override fun task(name: String, configureAction: Action<in Task>): Task {
            onAccess("task")
            return delegate.task(name, configureAction)
        }

        override fun getPath(): String {
            return delegate.path
        }

        override fun getBuildTreePath(): String {
            return delegate.buildTreePath
        }

        override fun getDefaultTasks(): MutableList<String> {
            onAccess("defaultTasks")
            return delegate.defaultTasks
        }

        override fun setDefaultTasks(defaultTasks: MutableList<String>) {
            onAccess("defaultTasks")
            delegate.defaultTasks = defaultTasks
        }

        override fun defaultTasks(vararg defaultTasks: String?) {
            onAccess("defaultTasks")
            delegate.defaultTasks(*defaultTasks)
        }

        override fun evaluationDependsOn(path: String): Project {
            onAccess("evaluationDependsOn")
            return delegate.evaluationDependsOn(path)
        }

        override fun evaluationDependsOnChildren() {
            onAccess("evaluationDependsOnChildren")
            delegate.evaluationDependsOnChildren()
        }

        override fun getAllTasks(recursive: Boolean): MutableMap<Project, MutableSet<Task>> {
            onAccess("allTasks")
            return delegate.getAllTasks(recursive)
        }

        override fun getTasksByName(name: String, recursive: Boolean): MutableSet<Task> {
            onAccess("tasksByName")
            return delegate.getTasksByName(name, recursive)
        }

        override fun getProjectDir(): File {
            return delegate.projectDir
        }

        override fun file(path: Any): File {
            onAccess("file")
            return delegate.file(path)
        }

        override fun file(path: Any, validation: PathValidation): File {
            onAccess("file")
            return delegate.file(path, validation)
        }

        override fun uri(path: Any): URI {
            onAccess("uri")
            return delegate.uri(path)
        }

        override fun relativePath(path: Any): String {
            onAccess("relativePath")
            return delegate.relativePath(path)
        }

        override fun files(vararg paths: Any?): ConfigurableFileCollection {
            onAccess("files")
            return delegate.files(*paths)
        }

        override fun files(paths: Any, configureClosure: Closure<*>): ConfigurableFileCollection {
            onAccess("files")
            return delegate.files(paths, configureClosure)
        }

        override fun files(paths: Any, configureAction: Action<in ConfigurableFileCollection>): ConfigurableFileCollection {
            onAccess("files")
            return delegate.files(paths, configureAction)
        }

        override fun fileTree(baseDir: Any): ConfigurableFileTree {
            onAccess("fileTree")
            return delegate.fileTree(baseDir)
        }

        override fun fileTree(baseDir: Any, configureClosure: Closure<*>): ConfigurableFileTree {
            onAccess("fileTree")
            return delegate.fileTree(baseDir, configureClosure)
        }

        override fun fileTree(baseDir: Any, configureAction: Action<in ConfigurableFileTree>): ConfigurableFileTree {
            onAccess("fileTree")
            return delegate.fileTree(baseDir, configureAction)
        }

        override fun fileTree(args: MutableMap<String, *>): ConfigurableFileTree {
            onAccess("fileTree")
            return delegate.fileTree(args)
        }

        override fun zipTree(zipPath: Any): FileTree {
            onAccess("zipTree")
            return delegate.zipTree(zipPath)
        }

        override fun tarTree(tarPath: Any): FileTree {
            onAccess("tarTree")
            return delegate.tarTree(tarPath)
        }

        override fun <T : Any> provider(value: Callable<out T?>): Provider<T> {
            onAccess("provider")
            return delegate.provider(value)
        }

        override fun getProviders(): ProviderFactory {
            onAccess("providers")
            return delegate.providers
        }

        override fun getObjects(): ObjectFactory {
            onAccess("objects")
            return delegate.objects
        }

        override fun getLayout(): ProjectLayout {
            onAccess("layout")
            return delegate.layout
        }

        override fun mkdir(path: Any): File {
            onAccess("mkdir")
            return delegate.mkdir(path)
        }

        override fun delete(vararg paths: Any?): Boolean {
            onAccess("delete")
            return delegate.delete(*paths)
        }

        override fun delete(action: Action<in DeleteSpec>): WorkResult {
            onAccess("delete")
            return delegate.delete(action)
        }

        override fun javaexec(closure: Closure<*>): ExecResult {
            onAccess("javaexec")
            return delegate.javaexec(closure)
        }

        override fun javaexec(action: Action<in JavaExecSpec>): ExecResult {
            onAccess("javaexec")
            return delegate.javaexec(action)
        }

        override fun exec(closure: Closure<*>): ExecResult {
            onAccess("exec")
            return delegate.exec(closure)
        }

        override fun exec(action: Action<in ExecSpec>): ExecResult {
            onAccess("exec")
            return delegate.exec(action)
        }

        override fun absoluteProjectPath(path: String): String {
            return delegate.absoluteProjectPath(path)
        }

        override fun relativeProjectPath(path: String): String {
            return delegate.relativeProjectPath(path)
        }

        override fun getAnt(): AntBuilder {
            onAccess("ant")
            return delegate.ant
        }

        override fun createAntBuilder(): AntBuilder {
            onAccess("antBuilder")
            return delegate.createAntBuilder()
        }

        override fun ant(configureClosure: Closure<*>): AntBuilder {
            onAccess("ant")
            return delegate.ant(configureClosure)
        }

        override fun ant(configureAction: Action<in AntBuilder>): AntBuilder {
            onAccess("ant")
            return delegate.ant(configureAction)
        }

        override fun getConfigurations(): RoleBasedConfigurationContainerInternal {
            onAccess("configurations")
            return delegate.configurations
        }

        override fun configurations(configureClosure: Closure<*>) {
            onAccess("configurations")
            delegate.configurations(configureClosure)
        }

        override fun getArtifacts(): ArtifactHandler {
            onAccess("artifacts")
            return delegate.artifacts
        }

        override fun artifacts(configureClosure: Closure<*>) {
            onAccess("artifacts")
            delegate.artifacts(configureClosure)
        }

        override fun artifacts(configureAction: Action<in ArtifactHandler>) {
            onAccess("artifacts")
            delegate.artifacts(configureAction)
        }

        @Deprecated("The concept of conventions is deprecated. Use extensions instead.")
        override fun getConvention(): @Suppress("deprecation") org.gradle.api.plugins.Convention {
            onAccess("convention")
            @Suppress("deprecation")
            return delegate.convention
        }

        override fun depthCompare(otherProject: Project): Int {
            return delegate.depthCompare(otherProject)
        }

        override fun getDepth(): Int {
            return delegate.depth
        }

        override fun project(path: String, configureClosure: Closure<*>): Project {
            return project(path, ConfigureUtil.configureUsing(configureClosure))
        }

        override fun project(path: String, configureAction: Action<in Project>): Project {
            return delegate.project(referrer, path, configureAction)
        }

        override fun project(referrer: ProjectInternal, path: String, configureAction: Action<in Project>): ProjectInternal {
            return delegate.project(referrer, path, configureAction)
        }

        override fun getSubprojects(): Set<Project> {
            return delegate.getSubprojects(referrer)
        }

        override fun getSubprojects(referrer: ProjectInternal): Set<ProjectInternal> {
            return delegate.getSubprojects(referrer)
        }

        override fun subprojects(action: Action<in Project>) {
            delegate.subprojects(referrer, action)
        }

        override fun subprojects(configureClosure: Closure<*>) {
            delegate.subprojects(referrer, ConfigureUtil.configureUsing(configureClosure))
        }

        override fun subprojects(referrer: ProjectInternal, configureAction: Action<in Project>) {
            delegate.subprojects(referrer, configureAction)
        }

        override fun getAllprojects(): Set<Project> {
            return delegate.getAllprojects(referrer)
        }

        override fun getAllprojects(referrer: ProjectInternal): Set<ProjectInternal> {
            return delegate.getAllprojects(referrer)
        }

        override fun allprojects(action: Action<in Project>) {
            delegate.allprojects(referrer, action)
        }

        override fun allprojects(configureClosure: Closure<*>) {
            delegate.allprojects(referrer, ConfigureUtil.configureUsing(configureClosure))
        }

        override fun allprojects(referrer: ProjectInternal, configureAction: Action<in Project>) {
            delegate.allprojects(referrer, configureAction)
        }

        override fun beforeEvaluate(action: Action<in Project>) {
            onAccess("beforeEvaluate")
            delegate.beforeEvaluate(action)
        }

        override fun afterEvaluate(action: Action<in Project>) {
            onAccess("afterEvaluate")
            delegate.afterEvaluate(action)
        }

        override fun beforeEvaluate(closure: Closure<*>) {
            onAccess("beforeEvaluate")
            delegate.beforeEvaluate(closure)
        }

        override fun afterEvaluate(closure: Closure<*>) {
            onAccess("afterEvaluate")
            delegate.afterEvaluate(closure)
        }

        override fun hasProperty(propertyName: String): Boolean {
            onAccess("hasProperty")
            return delegate.hasProperty(propertyName)
        }

        override fun getProperties(): MutableMap<String, *> {
            onAccess("properties")
            return delegate.properties
        }

        override fun property(propertyName: String): Any? {
            onAccess("property")
            return delegate.property(propertyName)
        }

        override fun findProperty(propertyName: String): Any? {
            onAccess("findProperty")
            return delegate.findProperty(propertyName)
        }

        override fun getLogger(): Logger {
            onAccess("logger")
            return delegate.logger
        }

        override fun getLogging(): LoggingManager {
            onAccess("logging")
            return delegate.logging
        }

        override fun configure(target: Any, configureClosure: Closure<*>): Any {
            onAccess("configure")
            return delegate.configure(target, configureClosure)
        }

        override fun configure(targets: MutableIterable<*>, configureClosure: Closure<*>): MutableIterable<*> {
            onAccess("configure")
            return delegate.configure(targets, configureClosure)
        }

        override fun <T : Any?> configure(targets: MutableIterable<T>, configureAction: Action<in T>): MutableIterable<T> {
            onAccess("configure")
            return delegate.configure(targets, configureAction)
        }

        override fun getRepositories(): RepositoryHandler {
            onAccess("repositories")
            return delegate.repositories
        }

        override fun repositories(configureClosure: Closure<*>) {
            onAccess("repositories")
            delegate.repositories(configureClosure)
        }

        override fun getDependencies(): DependencyHandler {
            onAccess("dependencies")
            return delegate.dependencies
        }

        override fun dependencies(configureClosure: Closure<*>) {
            onAccess("dependencies")
            delegate.dependencies(configureClosure)
        }

        override fun getDependencyFactory(): DependencyFactory {
            onAccess("dependencyFactory")
            return delegate.dependencyFactory
        }

        override fun buildscript(configureClosure: Closure<*>) {
            onAccess("buildscript")
            delegate.buildscript(configureClosure)
        }

        override fun copy(closure: Closure<*>): WorkResult {
            onAccess("copy")
            return delegate.copy(closure)
        }

        override fun copy(action: Action<in CopySpec>): WorkResult {
            onAccess("copy")
            return delegate.copy(action)
        }

        override fun copySpec(closure: Closure<*>): CopySpec {
            onAccess("copySpec")
            return delegate.copySpec(closure)
        }

        override fun copySpec(action: Action<in CopySpec>): CopySpec {
            onAccess("copySpec")
            return delegate.copySpec(action)
        }

        override fun copySpec(): CopySpec {
            onAccess("copySpec")
            return delegate.copySpec()
        }

        override fun sync(action: Action<in SyncSpec>): WorkResult {
            onAccess("sync")
            return delegate.sync(action)
        }

        override fun <T : Any?> container(type: Class<T>): NamedDomainObjectContainer<T> {
            onAccess("container")
            return delegate.container(type)
        }

        override fun <T : Any?> container(type: Class<T>, factory: NamedDomainObjectFactory<T>): NamedDomainObjectContainer<T> {
            onAccess("container")
            return delegate.container(type, factory)
        }

        override fun <T : Any?> container(type: Class<T>, factoryClosure: Closure<*>): NamedDomainObjectContainer<T> {
            onAccess("container")
            return delegate.container(type, factoryClosure)
        }

        override fun getResources(): ResourceHandler {
            onAccess("resources")
            return delegate.resources
        }

        override fun getComponents(): SoftwareComponentContainer {
            onAccess("components")
            return delegate.components
        }

        override fun components(configuration: Action<in SoftwareComponentContainer>) {
            onAccess("components")
            delegate.components(configuration)
        }

        override fun getNormalization(): InputNormalizationHandlerInternal {
            onAccess("normalization")
            return delegate.normalization
        }

        override fun normalization(configuration: Action<in InputNormalizationHandler>) {
            onAccess("normalization")
            delegate.normalization(configuration)
        }

        override fun dependencyLocking(configuration: Action<in DependencyLockingHandler>) {
            onAccess("dependencyLocking")
            delegate.dependencyLocking(configuration)
        }

        override fun getDependencyLocking(): DependencyLockingHandler {
            onAccess("dependencyLocking")
            return delegate.dependencyLocking
        }

        override fun getPlugins(): PluginContainer {
            onAccess("plugins")
            return delegate.plugins
        }

        override fun apply(closure: Closure<*>) {
            onAccess("apply")
            delegate.apply(closure)
        }

        override fun apply(action: Action<in ObjectConfigurationAction>) {
            onAccess("apply")
            delegate.apply(action)
        }

        override fun apply(options: MutableMap<String, *>) {
            onAccess("apply")
            delegate.apply(options)
        }

        override fun getPluginManager(): PluginManagerInternal {
            onAccess("pluginManager")
            return delegate.pluginManager
        }

        override fun identityPath(name: String): Path {
            shouldNotBeUsed()
        }

        override fun projectPath(name: String): Path {
            shouldNotBeUsed()
        }

        override fun getModel(): ModelContainer<*> {
            shouldNotBeUsed()
        }

        override fun getBuildPath(): Path {
            shouldNotBeUsed()
        }

        override fun isScript(): Boolean {
            shouldNotBeUsed()
        }

        override fun isRootScript(): Boolean {
            shouldNotBeUsed()
        }

        override fun isPluginContext(): Boolean {
            shouldNotBeUsed()
        }

        override fun getDependencyMetaDataProvider(): DependencyMetaDataProvider {
            shouldNotBeUsed()
        }

        override fun getFileOperations(): FileOperations {
            shouldNotBeUsed()
        }

        override fun getProcessOperations(): ProcessOperations {
            shouldNotBeUsed()
        }

        override fun getConfigurationTargetIdentifier(): ConfigurationTargetIdentifier {
            shouldNotBeUsed()
        }

        override fun getParentIdentifier(): ProjectIdentifier {
            shouldNotBeUsed()
        }

        override fun getParent(): ProjectInternal? {
            return delegate.getParent(referrer)
        }

        override fun getParent(referrer: ProjectInternal): ProjectInternal? {
            return delegate.getParent(referrer)
        }

        override fun getRootProject(): ProjectInternal {
            return delegate.getRootProject(referrer)
        }

        override fun getRootProject(referrer: ProjectInternal): ProjectInternal {
            return delegate.getRootProject(referrer)
        }

        override fun evaluate(): Project {
            shouldNotBeUsed()
        }

        override fun bindAllModelRules(): ProjectInternal {
            shouldNotBeUsed()
        }

        override fun getTasks(): TaskContainerInternal {
            onAccess("tasks")
            return delegate.tasks
        }

        override fun getBuildScriptSource(): ScriptSource {
            shouldNotBeUsed()
        }

        override fun project(path: String): ProjectInternal {
            return delegate.project(referrer, path)
        }

        override fun project(referrer: ProjectInternal, path: String): ProjectInternal {
            return delegate.project(referrer, path)
        }

        override fun findProject(path: String): ProjectInternal? {
            return delegate.findProject(referrer, path)
        }

        override fun findProject(referrer: ProjectInternal, path: String): ProjectInternal? {
            return delegate.findProject(referrer, path)
        }

        override fun getInheritedScope(): DynamicObject {
            return delegate.inheritedScope
        }

        override fun getGradle(): GradleInternal {
            onAccess("gradle")
            return delegate.gradle
        }

        override fun getProjectEvaluationBroadcaster(): ProjectEvaluationListener {
            shouldNotBeUsed()
        }

        override fun addRuleBasedPluginListener(listener: RuleBasedPluginListener) {
            shouldNotBeUsed()
        }

        override fun prepareForRuleBasedPlugins() {
            shouldNotBeUsed()
        }

        override fun getFileResolver(): FileResolver {
            shouldNotBeUsed()
        }

        override fun getTaskDependencyFactory(): TaskDependencyFactory {
            shouldNotBeUsed()
        }

        override fun getServices(): ServiceRegistry {
            shouldNotBeUsed()
        }

        override fun getServiceRegistryFactory(): ServiceRegistryFactory {
            shouldNotBeUsed()
        }

        override fun getStandardOutputCapture(): StandardOutputCapture {
            shouldNotBeUsed()
        }

        override fun getState(): ProjectStateInternal {
            onAccess("state")
            return delegate.state
        }

        override fun getExtensions(): ExtensionContainerInternal {
            onAccess("extensions")
            return delegate.extensions
        }

        override fun getConfigurationActions(): ProjectConfigurationActionContainer {
            shouldNotBeUsed()
        }

        override fun getModelRegistry(): ModelRegistry {
            shouldNotBeUsed()
        }

        override fun getClassLoaderScope(): ClassLoaderScope {
            shouldNotBeUsed()
        }

        override fun getBaseClassLoaderScope(): ClassLoaderScope {
            shouldNotBeUsed()
        }

        override fun setScript(script: Script) {
            shouldNotBeUsed()
        }

        override fun addDeferredConfiguration(configuration: Runnable) {
            shouldNotBeUsed()
        }

        override fun fireDeferredConfiguration() {
            shouldNotBeUsed()
        }

        override fun getProjectPath(): Path {
            return delegate.projectPath
        }

        override fun getIdentityPath(): Path {
            return delegate.identityPath
        }

        override fun stepEvaluationListener(listener: ProjectEvaluationListener, action: Action<ProjectEvaluationListener>): ProjectEvaluationListener? {
            shouldNotBeUsed()
        }

        override fun getOwner(): ProjectState {
            return delegate.owner
        }

        override fun getBuildscript(): ScriptHandlerInternal {
            onAccess("buildscript")
            return delegate.buildscript
        }

        override fun newDetachedResolver(): ProjectInternal.DetachedResolver {
            shouldNotBeUsed()
        }

        fun shouldNotBeUsed(): Nothing {
            throw UnsupportedOperationException("This internal method should not be used.")
        }

        private
        fun onAccess(what: String) {
            reportCrossProjectAccessProblem("Project.$what", "functionality")
            onProjectsCoupled()
        }

        private
        fun withDelegateDynamicCallIgnoringProblem(
            action: DynamicObject.() -> DynamicInvokeResult,
            resultNotFoundExceptionProvider: DynamicObject.() -> GroovyRuntimeException
        ): Any? {
            val delegateBean = (delegate as DynamicObjectAware).asDynamicObject

            dynamicCallProblemReporting.enterDynamicCall(delegateBean)

            try {
                dynamicCallProblemReporting.unreportedProblemInCurrentCall(CrossProjectModelAccessTrackingParentDynamicObject.PROBLEM_KEY)

                val delegateResult = delegateBean.action()

                if (delegateResult.isFound) {
                    return delegateResult.value
                }
                throw delegateBean.resultNotFoundExceptionProvider()
            } finally {
                dynamicCallProblemReporting.leaveDynamicCall(delegateBean)
            }
        }

        private
        fun withDelegateDynamicCallReportingConfigurationOrder(
            accessRef: String,
            action: DynamicObject.() -> DynamicInvokeResult,
            resultNotFoundExceptionProvider: DynamicObject.() -> GroovyRuntimeException
        ): Any? {
            val result = runCatching {
                withDelegateDynamicCallIgnoringProblem(action, resultNotFoundExceptionProvider)
            }

            val memberKind = "extension"
            return when {
                result.isSuccess -> {
                    reportCrossProjectAccessProblem(accessRef, memberKind)
                    result.getOrNull()
                }

                // Referent is not configured and wasn't invalidated
                // This can be a case in an incremental sync scenario, when referrer script was changed and since re-configured,
                // but referent wasn't changed and since wasn't configured.
                delegate < referrer && delegate.state.isUnconfigured && !buildModelParameters.isInvalidateCoupledProjects -> {
                    reportCrossProjectAccessProblem(accessRef, memberKind) { missedReferentConfigurationMessage() }
                    null
                }

                else -> {
                    reportCrossProjectAccessProblem(accessRef, memberKind)
                    throw result.exceptionOrNull()!!
                }
            }
        }

        private
        fun onProjectsCoupled() {
            coupledProjectsListener.onProjectReference(referrer.owner, delegate.owner)
            // Configure the target project, if it would normally be configured before the referring project
            if (delegate < referrer && delegate.parent != null && buildModelParameters.isInvalidateCoupledProjects) {
                delegate.owner.ensureConfigured()
            }
        }

        private
        fun reportCrossProjectAccessProblem(
            accessRef: String,
            accessRefKind: String,
            buildAdditionalMessage: StructuredMessage.Builder.() -> Unit = {}
        ) {
            val problem = problemFactory.problem {
                text("Project ")
                reference(referrer)
                text(" cannot access ")
                reference(accessRef)
                text(" $accessRefKind on ")
                describeCrossProjectAccess()
                buildAdditionalMessage()
            }
                .exception()
                .build()

            problems.onProblem(problem)
        }

        private
        fun StructuredMessage.Builder.missedReferentConfigurationMessage() = apply {
            text(". Setting ")
            reference("org.gradle.internal.invalidate-coupled-projects=false")
            text(" is preventing configuration of ")
            describeCrossProjectAccess()
        }

        private
        fun StructuredMessage.Builder.describeCrossProjectAccess() {
            val relativeToAnother = access.relativeTo != referrer

            when (access.pattern) {
                DIRECT -> {
                    text("another project ")
                    reference(delegate)
                }

                CHILD -> {
                    text("child projects")
                    if (relativeToAnother) {
                        text(" of project ")
                        reference(access.relativeTo)
                    }
                }

                SUBPROJECT -> {
                    text("subprojects")
                    if (relativeToAnother) {
                        text(" of project ")
                        reference(access.relativeTo)
                    }
                }

                ALLPROJECTS -> {
                    text("subprojects")
                    if (relativeToAnother) {
                        text(" of project ")
                        reference(access.relativeTo)
                    } else {
                        text(" via ")
                        reference("allprojects")
                    }
                }
            }
        }

        private
        fun StructuredMessage.Builder.reference(project: ProjectInternal) = reference(project.identityPath.toString())
    }
}
