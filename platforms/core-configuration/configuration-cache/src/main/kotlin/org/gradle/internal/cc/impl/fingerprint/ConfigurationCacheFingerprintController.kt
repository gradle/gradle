/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.cc.impl.fingerprint

import org.gradle.api.internal.artifacts.configurations.ProjectComponentObservationListener
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.cc.base.services.ConfigurationCacheEnvironmentChangeTracker
import org.gradle.internal.cc.impl.CheckedFingerprint
import org.gradle.internal.cc.impl.ConfigurationCacheStateFile
import org.gradle.internal.cc.impl.ConfigurationCacheStateStore.StateFile
import org.gradle.internal.cc.impl.InputTrackingState
import org.gradle.internal.cc.impl.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.cc.impl.problems.ConfigurationCacheProblems
import org.gradle.internal.cc.impl.services.RemoteScriptUpToDateChecker
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.configuration.problems.CommonReport
import org.gradle.internal.configuration.problems.DocumentationSection
import org.gradle.internal.configuration.problems.ProblemFactory
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.configuration.problems.StructuredMessageBuilder
import org.gradle.internal.encryption.EncryptionService
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.execution.FileCollectionFingerprinterRegistry
import org.gradle.internal.execution.WorkExecutionTracker
import org.gradle.internal.execution.WorkInputListeners
import org.gradle.internal.execution.impl.DefaultFileNormalizationSpec
import org.gradle.internal.execution.model.InputNormalizer
import org.gradle.internal.extensions.core.directoryChildrenNamesHash
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.file.FileType
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.internal.hash.HashCode
import org.gradle.internal.instrumentation.agent.AgentStatus
import org.gradle.internal.scripts.ProjectScopedScriptResolution
import org.gradle.internal.scripts.ScriptFileResolverListeners
import org.gradle.internal.serialize.graph.CloseableWriteContext
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.service.scopes.ParallelListener
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.util.Path
import org.gradle.util.internal.BuildCommencedTimeProvider
import org.gradle.util.internal.GFileUtils
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.function.Supplier


/**
 * Coordinates the writing and reading of the configuration cache fingerprint.
 */
@ServiceScope(Scope.BuildTree::class)
@Suppress("LongParameterList")
internal
class ConfigurationCacheFingerprintController internal constructor(
    private val startParameter: ConfigurationCacheStartParameter,
    private val modelParameters: BuildModelParameters,
    private val workInputListeners: WorkInputListeners,
    private val fileSystemAccess: FileSystemAccess,
    fingerprinterRegistry: FileCollectionFingerprinterRegistry,
    private val buildCommencedTimeProvider: BuildCommencedTimeProvider,
    private val listenerManager: ListenerManager,
    private val fileCollectionFactory: FileCollectionFactory,
    private val directoryFileTreeFactory: DirectoryFileTreeFactory,
    private val report: CommonReport,
    private val problemFactory: ProblemFactory,
    private val workExecutionTracker: WorkExecutionTracker,
    private val environmentChangeTracker: ConfigurationCacheEnvironmentChangeTracker,
    private val inputTrackingState: InputTrackingState,
    private val scriptFileResolverListeners: ScriptFileResolverListeners,
    private val remoteScriptUpToDateChecker: RemoteScriptUpToDateChecker,
    private val agentStatus: AgentStatus,
    private val problems: ConfigurationCacheProblems,
    private val encryptionService: EncryptionService,
    private val configurationTimeBarrier: ConfigurationTimeBarrier
) : Stoppable, ProjectScopedScriptResolution {

    interface Host {
        val buildPath: Path
        val valueSourceProviderFactory: ValueSourceProviderFactory
        val gradleProperties: GradleProperties
    }

    private
    val fileCollectionFingerprinter =
        fingerprinterRegistry.getFingerprinter(
            DefaultFileNormalizationSpec.from(
                InputNormalizer.ABSOLUTE_PATH,
                DirectorySensitivity.DEFAULT,
                LineEndingSensitivity.DEFAULT
            )
        )

    private
    abstract class WritingState {

        open fun maybeStart(
            buildScopedSpoolFile: StateFile,
            projectScopedSpoolFile: StateFile,
            writeContextForOutputStream: (StateFile) -> CloseableWriteContext
        ): WritingState =
            illegalStateFor("start")

        open fun pause(): WritingState =
            illegalStateFor("pause")

        open fun commit(
            buildScopedFingerprint: ConfigurationCacheStateFile,
            projectScopedFingerprint: ConfigurationCacheStateFile
        ): WritingState =
            illegalStateFor("commit")

        open fun append(fingerprint: ProjectSpecificFingerprint): Unit =
            illegalStateFor("append")

        open fun <T> resolveScriptsForProject(project: ProjectIdentity, action: () -> T): T =
            illegalStateFor("resolveScriptsForProject")

        open fun <T> runCollectingFingerprintForProject(project: ProjectIdentity, action: () -> T): T =
            illegalStateFor("collectFingerprintForProject")

        open fun projectObserved(consumingProjectPath: Path?, targetProjectPath: Path): Unit =
            illegalStateFor("projectObserved")

        abstract fun dispose(): WritingState

        private
        fun illegalStateFor(operation: String): Nothing = error(
            "'$operation' is illegal while in '${javaClass.simpleName}' state."
        )
    }

    private
    inner class Idle : WritingState() {
        override fun maybeStart(
            buildScopedSpoolFile: StateFile,
            projectScopedSpoolFile: StateFile,
            writeContextForOutputStream: (StateFile) -> CloseableWriteContext
        ): WritingState {

            val fingerprintWriter = ConfigurationCacheFingerprintWriter(
                CacheFingerprintWriterHost(),
                writeContextForOutputStream(buildScopedSpoolFile),
                writeContextForOutputStream(projectScopedSpoolFile),
                fileCollectionFactory,
                directoryFileTreeFactory,
                workExecutionTracker,
                environmentChangeTracker,
                inputTrackingState
            )
            addListener(fingerprintWriter)
            return Writing(fingerprintWriter, buildScopedSpoolFile, projectScopedSpoolFile)
        }

        override fun <T> resolveScriptsForProject(project: ProjectIdentity, action: () -> T): T {
            // Ignore scripts resolved while loading from cache
            return action()
        }

        override fun dispose(): WritingState =
            this
    }

    private
    inner class Writing(
        private val fingerprintWriter: ConfigurationCacheFingerprintWriter,
        private val buildScopedSpoolFile: StateFile,
        private val projectScopedSpoolFile: StateFile
    ) : WritingState() {
        override fun maybeStart(
            buildScopedSpoolFile: StateFile,
            projectScopedSpoolFile: StateFile,
            writeContextForOutputStream: (StateFile) -> CloseableWriteContext
        ): WritingState {
            return this
        }

        override fun <T> resolveScriptsForProject(project: ProjectIdentity, action: () -> T): T {
            return fingerprintWriter.runCollectingFingerprintForProject(project, action)
        }

        override fun <T> runCollectingFingerprintForProject(project: ProjectIdentity, action: () -> T): T {
            return fingerprintWriter.runCollectingFingerprintForProject(project, action)
        }

        override fun pause(): WritingState {
            removeListener(fingerprintWriter)
            return Paused(fingerprintWriter, buildScopedSpoolFile, projectScopedSpoolFile)
        }

        override fun projectObserved(consumingProjectPath: Path?, targetProjectPath: Path) {
            fingerprintWriter.projectObserved(consumingProjectPath, targetProjectPath)
        }

        override fun dispose() =
            pause().dispose()
    }

    private
    inner class Paused(
        private val fingerprintWriter: ConfigurationCacheFingerprintWriter,
        private val buildScopedSpoolFile: StateFile,
        private val projectScopedSpoolFile: StateFile
    ) : WritingState() {
        override fun maybeStart(
            buildScopedSpoolFile: StateFile,
            projectScopedSpoolFile: StateFile,
            writeContextForOutputStream: (StateFile) -> CloseableWriteContext
        ): WritingState {
            addListener(fingerprintWriter)
            // Continue with the current spool file, rather than starting a new one
            return Writing(fingerprintWriter, this.buildScopedSpoolFile, this.projectScopedSpoolFile)
        }

        override fun pause(): WritingState {
            return this
        }

        override fun append(fingerprint: ProjectSpecificFingerprint) {
            fingerprintWriter.append(fingerprint)
        }

        override fun commit(
            buildScopedFingerprint: ConfigurationCacheStateFile,
            projectScopedFingerprint: ConfigurationCacheStateFile
        ): WritingState {
            closeStreams()
            buildScopedFingerprint.moveFrom(buildScopedSpoolFile.file)
            projectScopedFingerprint.moveFrom(projectScopedSpoolFile.file)
            return Committed()
        }

        override fun dispose(): WritingState {
            closeStreams()
            if (buildScopedSpoolFile.file.exists()) {
                Files.delete(buildScopedSpoolFile.file.toPath())
            }
            if (projectScopedSpoolFile.file.exists()) {
                Files.delete(projectScopedSpoolFile.file.toPath())
            }
            return Idle()
        }

        override fun projectObserved(consumingProjectPath: Path?, targetProjectPath: Path) {
            if (!atConfigurationTime()) {
                return
            }

            error("Unexpected project dependency observed outside of fingerprinting: consumer=$consumingProjectPath, target=$targetProjectPath")
        }

        private
        fun closeStreams() {
            fingerprintWriter.close()
        }

        private
        fun atConfigurationTime() = configurationTimeBarrier.isAtConfigurationTime
    }

    private
    class Committed : WritingState() {
        override fun <T> resolveScriptsForProject(project: ProjectIdentity, action: () -> T): T {
            // Ignore scripts resolved while loading from cache
            return action()
        }

        override fun dispose(): WritingState {
            return this
        }
    }

    private
    var writingState: WritingState = Idle()

    private
    val projectComponentObservationListener = ProjectObservationListener(this)

    @ParallelListener
    private class ProjectObservationListener(
        private val controller: ConfigurationCacheFingerprintController
    ) : ProjectComponentObservationListener {
        override fun projectObserved(consumingProjectPath: Path?, targetProjectPath: Path) =
            controller.writingState.projectObserved(consumingProjectPath, targetProjectPath)
    }

    // Start fingerprinting if not already started and not already committed
    // This should be strict but currently this method may be called multiple times when a
    // build invocation both runs tasks and queries models
    fun maybeStartCollectingFingerprint(
        buildScopedSpoolFile: StateFile,
        projectScopedSpoolFile: StateFile,
        writeContextForOutputStream: (StateFile) -> CloseableWriteContext
    ) {
        writingState = writingState.maybeStart(buildScopedSpoolFile, projectScopedSpoolFile, writeContextForOutputStream)
    }

    fun stopCollectingFingerprint() {
        writingState = writingState.pause()
    }

    fun commitFingerprintTo(
        buildScopedFingerprint: ConfigurationCacheStateFile,
        projectScopedFingerprint: ConfigurationCacheStateFile
    ) {
        writingState = writingState.commit(buildScopedFingerprint, projectScopedFingerprint)
    }

    override fun <T : Any> resolveScriptsForProject(project: ProjectIdentity, action: Supplier<T>): T {
        return writingState.resolveScriptsForProject(project) { action.get() }
    }

    /**
     * Runs the given action that is specific to the given project, and associates any build inputs read by the current thread
     * with the project.
     */
    fun <T> runCollectingFingerprintForProject(project: ProjectIdentity, action: () -> T): T {
        return writingState.runCollectingFingerprintForProject(project, action)
    }

    override fun stop() {
        writingState = writingState.dispose()
    }

    suspend fun ReadContext.checkBuildScopedFingerprint(host: Host): CheckedFingerprint =
        ConfigurationCacheFingerprintChecker(CacheFingerprintCheckerHost(host)).run {
            checkBuildScopedFingerprint()
        }

    suspend fun ReadContext.checkProjectScopedFingerprint(host: Host): CheckedFingerprint =
        ConfigurationCacheFingerprintChecker(CacheFingerprintCheckerHost(host)).run {
            checkProjectScopedFingerprint()
        }

    suspend fun ReadContext.collectFingerprintForReusedProjects(host: Host, reusedProjects: Set<Path>): Unit =
        ConfigurationCacheFingerprintChecker(CacheFingerprintCheckerHost(host)).run {
            visitEntriesForProjects(reusedProjects) { fingerprint ->
                writingState.append(fingerprint)
            }
        }

    private
    fun addListener(listener: ConfigurationCacheFingerprintWriter) {
        // Never removed, as stateful listeners cannot be removed after events have been emitted
        listenerManager.addListener(projectComponentObservationListener)

        listenerManager.addListener(listener)
        workInputListeners.addListener(listener)
        scriptFileResolverListeners.addListener(listener)
    }

    private
    fun removeListener(listener: ConfigurationCacheFingerprintWriter) {
        scriptFileResolverListeners.removeListener(listener)
        workInputListeners.removeListener(listener)
        listenerManager.removeListener(listener)
    }

    private
    inner class CacheFingerprintWriterHost :
        ConfigurationCacheFingerprintWriter.Host {

        override val isEncrypted: Boolean
            get() = encryptionService.isEncrypting

        override val encryptionKeyHashCode: HashCode
            get() = encryptionService.encryptionKeyHashCode

        override val gradleUserHomeDir: File
            get() = startParameter.gradleUserHomeDir

        override val startParameterProperties: Map<String, Any?>
            get() = startParameter.gradleProperties

        override val allInitScripts: List<File>
            get() = startParameter.allInitScripts

        override val buildStartTime: Long
            get() = buildCommencedTimeProvider.currentTime

        override val cacheIntermediateModels: Boolean
            get() = modelParameters.isIntermediateModelCache

        override val modelAsProjectDependency: Boolean
            get() = modelParameters.isModelAsProjectDependency

        override val ignoreInputsDuringConfigurationCacheStore: Boolean
            get() = startParameter.isIgnoreInputsDuringStore

        override val instrumentationAgentUsed: Boolean
            get() = agentStatus.isAgentInstrumentationEnabled

        override val ignoredFileSystemCheckInputs: String?
            get() = startParameter.ignoredFileSystemCheckInputs

        override fun hashCodeOf(file: File): HashCode =
            fileSystemAccess.read(file.absolutePath).hash

        override fun hashCodeOfDirectoryChildrenNames(file: File): HashCode =
            directoryChildrenNamesHash(file)

        override fun displayNameOf(file: File): String =
            GFileUtils.relativePathOf(file, rootDirectory)

        override fun fingerprintOf(fileCollection: FileCollectionInternal): HashCode =
            fileCollectionFingerprinter.fingerprint(fileCollection).hash

        override fun reportInput(input: PropertyProblem) =
            report.onInput(input)

        override fun reportProblem(
            exception: Throwable?,
            documentationSection: DocumentationSection?,
            message: StructuredMessageBuilder
        ) =
            problems.onProblem(
                problemFactory.problem(
                    StructuredMessage.build(message),
                    exception,
                    documentationSection
                )
            )

        override fun location(consumer: String?) =
            problemFactory.locationForCaller(consumer)
    }

    private
    inner class CacheFingerprintCheckerHost(
        private val host: Host
    ) : ConfigurationCacheFingerprintChecker.Host {

        private
        val gradleProperties by lazy(host::gradleProperties)

        override val buildPath: Path
            get() = host.buildPath

        override val isEncrypted: Boolean
            get() = encryptionService.isEncrypting

        override val encryptionKeyHashCode: HashCode
            get() = encryptionService.encryptionKeyHashCode

        override val gradleUserHomeDir: File
            get() = startParameter.gradleUserHomeDir

        override val allInitScripts: List<File>
            get() = startParameter.allInitScripts

        override val startParameterProperties: Map<String, Any?>
            get() = startParameter.gradleProperties

        override val buildStartTime: Long
            get() = buildCommencedTimeProvider.currentTime

        override val invalidateCoupledProjects: Boolean
            get() = modelParameters.isInvalidateCoupledProjects

        override val ignoreInputsDuringConfigurationCacheStore: Boolean
            get() = startParameter.isIgnoreInputsDuringStore

        override val instrumentationAgentUsed: Boolean
            get() = agentStatus.isAgentInstrumentationEnabled

        override val ignoredFileSystemCheckInputs: String?
            get() = startParameter.ignoredFileSystemCheckInputs

        override fun gradleProperty(propertyName: String): String? =
            gradleProperties.find(propertyName)?.uncheckedCast()

        override fun hashCodeOf(file: File) =
            hashCodeAndTypeOf(file).first

        override fun hashCodeAndTypeOf(file: File): Pair<HashCode, FileType> =
            fileSystemAccess.read(file.absolutePath).let { it.hash to it.type }

        override fun hashCodeOfDirectoryContent(file: File): HashCode =
            directoryChildrenNamesHash(file)

        override fun fingerprintOf(fileCollection: FileCollectionInternal): HashCode =
            fileCollectionFingerprinter.fingerprint(fileCollection).hash

        override fun displayNameOf(fileOrDirectory: File): String =
            GFileUtils.relativePathOf(fileOrDirectory, rootDirectory)

        override fun instantiateValueSourceOf(obtainedValue: ObtainedValue) =
            (host.valueSourceProviderFactory as DefaultValueSourceProviderFactory).instantiateValueSource(
                obtainedValue.valueSourceType,
                obtainedValue.valueSourceParametersType,
                obtainedValue.valueSourceParameters
            )

        override fun isRemoteScriptUpToDate(uri: URI): Boolean =
            remoteScriptUpToDateChecker.isUpToDate(uri)
    }

    private
    val rootDirectory
        get() = startParameter.rootDirectory
}
