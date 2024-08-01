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

package org.gradle.internal.cc.impl

import groovy.lang.Closure
import groovy.lang.GroovyRuntimeException
import groovy.lang.Script
import org.gradle.api.Action
import org.gradle.api.AntBuilder
import org.gradle.api.PathValidation
import org.gradle.api.Project
import org.gradle.api.ProjectEvaluationListener
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.FileTree
import org.gradle.api.file.SyncSpec
import org.gradle.api.internal.DynamicObjectAware
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.ProcessOperations
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.CrossProjectModelAccess
import org.gradle.api.internal.project.MutableStateAccessAwareProject
import org.gradle.api.internal.project.ProjectIdentifier
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyUsageTracker
import org.gradle.api.logging.Logger
import org.gradle.api.logging.LoggingManager
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.resources.ResourceHandler
import org.gradle.api.tasks.WorkResult
import org.gradle.configuration.ConfigurationTargetIdentifier
import org.gradle.configuration.project.ProjectConfigurationActionContainer
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.cc.impl.CrossProjectModelAccessPattern.ALLPROJECTS
import org.gradle.internal.cc.impl.CrossProjectModelAccessPattern.CHILD
import org.gradle.internal.cc.impl.CrossProjectModelAccessPattern.DIRECT
import org.gradle.internal.cc.impl.CrossProjectModelAccessPattern.SUBPROJECT
import org.gradle.internal.configuration.problems.ProblemFactory
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.logging.StandardOutputCapture
import org.gradle.internal.metaobject.DynamicInvokeResult
import org.gradle.internal.metaobject.DynamicObject
import org.gradle.internal.model.ModelContainer
import org.gradle.internal.model.RuleBasedPluginListener
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec
import org.gradle.util.Path
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
    private val buildModelParameters: BuildModelParameters,
    private val instantiator: Instantiator
) : CrossProjectModelAccess {
    override fun findProject(referrer: ProjectInternal, relativeTo: ProjectInternal, path: String): ProjectInternal? {
        return delegate.findProject(referrer, relativeTo, path)?.let {
            it.wrap(referrer, CrossProjectModelAccessInstance(DIRECT, it), instantiator)
        }
    }

    override fun access(referrer: ProjectInternal, project: ProjectInternal): ProjectInternal {
        return project.wrap(referrer, CrossProjectModelAccessInstance(DIRECT, project), instantiator)
    }

    override fun getChildProjects(referrer: ProjectInternal, relativeTo: ProjectInternal): MutableMap<String, Project> {
        return delegate.getChildProjects(referrer, relativeTo).mapValuesTo(LinkedHashMap()) {
            (it.value as ProjectInternal).wrap(referrer, CrossProjectModelAccessInstance(CHILD, relativeTo), instantiator)
        }
    }

    override fun getSubprojects(referrer: ProjectInternal, relativeTo: ProjectInternal): MutableSet<out ProjectInternal> {
        return delegate.getSubprojects(referrer, relativeTo).mapTo(LinkedHashSet()) {
            it.wrap(referrer, CrossProjectModelAccessInstance(SUBPROJECT, relativeTo), instantiator)
        }
    }

    override fun getAllprojects(referrer: ProjectInternal, relativeTo: ProjectInternal): MutableSet<out ProjectInternal> {
        return delegate.getAllprojects(referrer, relativeTo).mapTo(LinkedHashSet()) {
            it.wrap(referrer, CrossProjectModelAccessInstance(ALLPROJECTS, relativeTo), instantiator)
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
        instantiator: Instantiator
    ): ProjectInternal = MutableStateAccessAwareProject.wrap(this, referrer) {
        instantiator.newInstance(ProblemReportingProject::class.java, this, referrer, access, problems, coupledProjectsListener, problemFactory, buildModelParameters, dynamicCallProblemReporting)
    }

    @Suppress("LargeClass")
    open class ProblemReportingProject(
        delegate: ProjectInternal,
        referrer: ProjectInternal,
        private val access: CrossProjectModelAccessInstance,
        private val problems: ProblemsListener,
        private val coupledProjectsListener: CoupledProjectsListener,
        private val problemFactory: ProblemFactory,
        private val buildModelParameters: BuildModelParameters,
        private val dynamicCallProblemReporting: DynamicCallProblemReporting,
    ) : MutableStateAccessAwareProject(delegate, referrer) {

        override fun onMutableStateAccess(what: String) {
            onIsolationViolation(what)
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

        override fun hashCode(): Int = delegate.hashCode()

        override fun toString(): String = delegate.toString()

        override fun propertyMissing(name: String): Any? {
            onProjectsCoupled()
            return withDelegateDynamicCallReportingConfigurationOrder(
                name,
                action = { tryGetProperty(name) },
                resultNotFoundExceptionProvider = { getMissingProperty(name) }
            )
        }

        override fun methodMissing(name: String, args: Any): Any? {
            onProjectsCoupled()
            val varargs: Array<Any?> = args.uncheckedCast()
            @Suppress("SpreadOperator")
            return withDelegateDynamicCallReportingConfigurationOrder(
                name,
                action = { tryInvokeMethod(name, *varargs) },
                resultNotFoundExceptionProvider = { methodMissingException(name, *varargs) }
            )
        }

        override fun file(path: Any): File {
            onIsolationViolation("file")
            return super.file(path)
        }

        override fun file(path: Any, validation: PathValidation): File {
            onIsolationViolation("file")
            return super.file(path, validation)
        }

        override fun uri(path: Any): URI {
            onIsolationViolation("uri")
            return super.uri(path)
        }

        override fun relativePath(path: Any): String {
            onIsolationViolation("relativePath")
            return super.relativePath(path)
        }

        override fun files(vararg paths: Any?): ConfigurableFileCollection {
            onIsolationViolation("files")
            return super.files(*paths)
        }

        override fun files(paths: Any, configureClosure: Closure<*>): ConfigurableFileCollection {
            onIsolationViolation("files")
            return super.files(paths, configureClosure)
        }

        override fun files(paths: Any, configureAction: Action<in ConfigurableFileCollection>): ConfigurableFileCollection {
            onIsolationViolation("files")
            return super.files(paths, configureAction)
        }

        override fun fileTree(baseDir: Any): ConfigurableFileTree {
            onIsolationViolation("fileTree")
            return super.fileTree(baseDir)
        }

        override fun fileTree(baseDir: Any, configureClosure: Closure<*>): ConfigurableFileTree {
            onIsolationViolation("fileTree")
            return super.fileTree(baseDir, configureClosure)
        }

        override fun fileTree(baseDir: Any, configureAction: Action<in ConfigurableFileTree>): ConfigurableFileTree {
            onIsolationViolation("fileTree")
            return super.fileTree(baseDir, configureAction)
        }

        override fun fileTree(args: MutableMap<String, *>): ConfigurableFileTree {
            onIsolationViolation("fileTree")
            return super.fileTree(args)
        }

        override fun zipTree(zipPath: Any): FileTree {
            onIsolationViolation("zipTree")
            return super.zipTree(zipPath)
        }

        override fun tarTree(tarPath: Any): FileTree {
            onIsolationViolation("tarTree")
            return super.tarTree(tarPath)
        }

        override fun <T : Any> provider(value: Callable<out T?>): Provider<T> {
            onIsolationViolation("provider")
            return super.provider(value)
        }

        override fun getProviders(): ProviderFactory {
            onIsolationViolation("providers")
            return super.getProviders()
        }

        override fun getObjects(): ObjectFactory {
            onIsolationViolation("objects")
            return super.getObjects()
        }

        override fun mkdir(path: Any): File {
            onIsolationViolation("mkdir")
            return super.mkdir(path)
        }

        override fun delete(vararg paths: Any?): Boolean {
            onIsolationViolation("delete")
            return super.delete(*paths)
        }

        override fun delete(action: Action<in DeleteSpec>): WorkResult {
            onIsolationViolation("delete")
            return super.delete(action)
        }

        override fun javaexec(closure: Closure<*>): ExecResult {
            onIsolationViolation("javaexec")
            return super.javaexec(closure)
        }

        override fun javaexec(action: Action<in JavaExecSpec>): ExecResult {
            onIsolationViolation("javaexec")
            return super.javaexec(action)
        }

        override fun exec(closure: Closure<*>): ExecResult {
            onIsolationViolation("exec")
            return super.exec(closure)
        }

        override fun exec(action: Action<in ExecSpec>): ExecResult {
            onIsolationViolation("exec")
            return super.exec(action)
        }

        override fun getResources(): ResourceHandler {
            onIsolationViolation("resources")
            return super.getResources()
        }

        override fun sync(action: Action<in SyncSpec>): WorkResult {
            onIsolationViolation("sync")
            return super.sync(action)
        }

        override fun copySpec(closure: Closure<*>): CopySpec {
            onIsolationViolation("copySpec")
            return super.copySpec(closure)
        }

        override fun copySpec(action: Action<in CopySpec>): CopySpec {
            onIsolationViolation("copySpec")
            return super.copySpec(action)
        }

        override fun copySpec(): CopySpec {
            onIsolationViolation("copySpec")
            return super.copySpec()
        }

        override fun copy(closure: Closure<*>): WorkResult {
            onIsolationViolation("copy")
            return super.copy(closure)
        }

        override fun copy(action: Action<in CopySpec>): WorkResult {
            onIsolationViolation("copy")
            return super.copy(action)
        }

        override fun getDependencyFactory(): DependencyFactory {
            onIsolationViolation("dependencyFactory")
            return super.getDependencyFactory()
        }

        override fun configure(`object`: Any, configureClosure: Closure<*>): Any {
            onIsolationViolation("configure")
            return super.configure(`object`, configureClosure)
        }

        override fun configure(objects: MutableIterable<*>, configureClosure: Closure<*>): MutableIterable<*> {
            onIsolationViolation("configure")
            return super.configure(objects, configureClosure)
        }

        override fun <T : Any?> configure(objects: MutableIterable<T>, configureAction: Action<in T>): MutableIterable<T> {
            onIsolationViolation("configure")
            return super.configure(objects, configureAction)
        }

        override fun getLogging(): LoggingManager {
            onIsolationViolation("logging")
            return super.getLogging()
        }

        override fun getLogger(): Logger {
            onIsolationViolation("logger")
            return super.getLogger()
        }

        override fun getAnt(): AntBuilder {
            onIsolationViolation("ant")
            return super.getAnt()
        }

        override fun createAntBuilder(): AntBuilder {
            onIsolationViolation("antBuilder")
            return super.createAntBuilder()
        }

        override fun ant(configureClosure: Closure<*>): AntBuilder {
            onIsolationViolation("ant")
            return super.ant(configureClosure)
        }

        override fun ant(configureAction: Action<in AntBuilder>): AntBuilder {
            onIsolationViolation("ant")
            return super.ant(configureAction)
        }

        override fun getGradle(): GradleInternal {
            onIsolationViolation("gradle")
            return super.getGradle()
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

        override fun evaluate(): Project {
            shouldNotBeUsed()
        }

        override fun bindAllModelRules(): ProjectInternal {
            shouldNotBeUsed()
        }

        override fun getBuildScriptSource(): ScriptSource {
            shouldNotBeUsed()
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

        override fun stepEvaluationListener(listener: ProjectEvaluationListener, action: Action<ProjectEvaluationListener>): ProjectEvaluationListener? {
            shouldNotBeUsed()
        }

        override fun newDetachedResolver(): ProjectInternal.DetachedResolver {
            shouldNotBeUsed()
        }

        private fun shouldNotBeUsed(): Nothing {
            throw UnsupportedOperationException("This internal method should not be used.")
        }

        private fun onIsolationViolation(what: String) {
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

        @Suppress("ThrowingExceptionsWithoutMessageOrCause")
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
