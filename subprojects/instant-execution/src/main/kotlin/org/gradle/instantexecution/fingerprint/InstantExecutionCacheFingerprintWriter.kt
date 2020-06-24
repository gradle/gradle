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

package org.gradle.instantexecution.fingerprint

import com.google.common.collect.Sets.newConcurrentHashSet
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.execution.internal.TaskInputsListener
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.artifacts.configurations.dynamicversion.Expiry
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DynamicVersionResolutionListener
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.internal.provider.sources.FileContentValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.initialization.DefaultSettingsLoader.BUILD_SRC_PROJECT_PATH
import org.gradle.instantexecution.UndeclaredBuildInputListener
import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.fingerprint.InstantExecutionCacheFingerprint.InputFile
import org.gradle.instantexecution.fingerprint.InstantExecutionCacheFingerprint.ValueSource
import org.gradle.instantexecution.serialization.DefaultWriteContext
import org.gradle.instantexecution.serialization.runWriteOperation
import org.gradle.internal.hash.HashCode
import org.gradle.internal.scripts.ScriptExecutionListener
import java.io.File


internal
class InstantExecutionCacheFingerprintWriter(
    private val host: Host,
    private val writeContext: DefaultWriteContext
) : ValueSourceProviderFactory.Listener, TaskInputsListener, ScriptExecutionListener, UndeclaredBuildInputListener, DynamicVersionResolutionListener {

    interface Host {

        val allInitScripts: List<File>

        val buildStartTime: Long

        fun hashCodeOf(file: File): HashCode?

        fun fingerprintOf(
            fileCollection: FileCollectionInternal,
            owner: TaskInternal
        ): HashCode
    }

    private
    var ignoreValueSources = false

    private
    val capturedFiles: MutableSet<File>

    private
    val undeclaredSystemProperties = mutableSetOf<String>()

    private
    var soonestDynamicVersionExpiry: InstantExecutionCacheFingerprint.DynamicDependencyVersion? = null

    init {
        val initScripts = host.allInitScripts
        capturedFiles = newConcurrentHashSet(initScripts)
        write(
            InstantExecutionCacheFingerprint.InitScripts(
                initScripts.map(::inputFile)
            )
        )
    }

    /**
     * Finishes writing to the given [writeContext] and closes it.
     *
     * **MUST ALWAYS BE CALLED**
     */
    fun close() {
        if (soonestDynamicVersionExpiry != null) {
            write(soonestDynamicVersionExpiry)
        }
        write(null)
        writeContext.close()
    }

    fun stopCollectingValueSources() {
        // TODO - this is a temporary step, see the comment in DefaultInstantExecution
        ignoreValueSources = true
    }

    override fun onDynamicVersionResolve(requested: ModuleComponentSelector, expiry: Expiry) {
        val expireAt = host.buildStartTime + expiry.keepFor.toMillis()
        if (soonestDynamicVersionExpiry == null || soonestDynamicVersionExpiry!!.expireAt > expireAt) {
            soonestDynamicVersionExpiry = InstantExecutionCacheFingerprint.DynamicDependencyVersion(requested.displayName, expireAt)
        }
    }

    override fun systemPropertyRead(key: String) {
        if (!undeclaredSystemProperties.add(key)) {
            return
        }
        write(InstantExecutionCacheFingerprint.UndeclaredSystemProperty(key))
    }

    override fun <T : Any, P : ValueSourceParameters> valueObtained(
        obtainedValue: ValueSourceProviderFactory.Listener.ObtainedValue<T, P>
    ) {
        if (ignoreValueSources) {
            return
        }
        when (val parameters = obtainedValue.valueSourceParameters) {
            is FileContentValueSource.Parameters -> {
                parameters.file.orNull?.asFile?.let { file ->
                    // TODO - consider the potential race condition in computing the hash code here
                    captureFile(file)
                }
            }
            else -> {
                write(
                    ValueSource(
                        obtainedValue.uncheckedCast()
                    )
                )
            }
        }
    }

    override fun onScriptClassLoaded(source: ScriptSource, scriptClass: Class<*>) {
        source.resource.file?.let {
            captureFile(it)
        }
    }

    override fun onExecute(task: TaskInternal, fileSystemInputs: FileCollectionInternal) {
        if (isBuildSrcTask(task)) {
            captureTaskInputs(task, fileSystemInputs)
        }
    }

    private
    fun captureFile(file: File) {
        if (!capturedFiles.add(file))
            return
        write(inputFile(file))
    }

    private
    fun inputFile(file: File) =
        InputFile(
            file,
            host.hashCodeOf(file)
        )

    private
    fun captureTaskInputs(task: TaskInternal, fileSystemInputs: FileCollectionInternal) {
        write(
            InstantExecutionCacheFingerprint.TaskInputs(
                task.identityPath.path,
                fileSystemInputs,
                host.fingerprintOf(fileSystemInputs, task)
            )
        )
    }

    private
    fun write(value: InstantExecutionCacheFingerprint?) {
        synchronized(writeContext) {
            writeContext.runWriteOperation {
                write(value)
            }
        }
    }

    private
    fun isBuildSrcTask(task: TaskInternal) =
        task.taskIdentity.buildPath.path == BUILD_SRC_PROJECT_PATH
}
