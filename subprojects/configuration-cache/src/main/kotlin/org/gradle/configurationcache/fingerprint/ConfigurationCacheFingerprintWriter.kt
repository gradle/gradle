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

package org.gradle.configurationcache.fingerprint

import com.google.common.collect.Sets.newConcurrentHashSet
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.execution.internal.TaskInputsListener
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.artifacts.configurations.dynamicversion.Expiry
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ChangingValueDependencyResolutionListener
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.internal.provider.sources.EnvironmentVariableValueSource
import org.gradle.api.internal.provider.sources.FileContentValueSource
import org.gradle.api.internal.provider.sources.GradlePropertyValueSource
import org.gradle.api.internal.provider.sources.SystemPropertyValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.util.PatternSet
import org.gradle.configurationcache.UndeclaredBuildInputListener
import org.gradle.configurationcache.extensions.uncheckedCast
import org.gradle.configurationcache.fingerprint.ConfigurationCacheFingerprint.InputFile
import org.gradle.configurationcache.fingerprint.ConfigurationCacheFingerprint.ValueSource
import org.gradle.configurationcache.problems.DocumentationSection
import org.gradle.configurationcache.problems.PropertyProblem
import org.gradle.configurationcache.problems.PropertyTrace
import org.gradle.configurationcache.problems.StructuredMessage
import org.gradle.configurationcache.serialization.DefaultWriteContext
import org.gradle.configurationcache.serialization.runWriteOperation
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.hash.HashCode
import org.gradle.internal.resource.local.FileResourceListener
import org.gradle.internal.scripts.ScriptExecutionListener
import org.gradle.util.Path
import java.io.File


internal
class ConfigurationCacheFingerprintWriter(
    private val host: Host,
    private val writeContext: DefaultWriteContext,
    private val fileCollectionFactory: FileCollectionFactory,
    private val directoryFileTreeFactory: DirectoryFileTreeFactory
) : ValueSourceProviderFactory.Listener,
    TaskInputsListener,
    ScriptExecutionListener,
    UndeclaredBuildInputListener,
    ChangingValueDependencyResolutionListener,
    FileResourceListener {

    interface Host {
        val gradleUserHomeDir: File
        val allInitScripts: List<File>
        val buildStartTime: Long
        fun fingerprintOf(fileCollection: FileCollectionInternal): HashCode
        fun hashCodeOf(file: File): HashCode?
        fun reportInput(input: PropertyProblem)
        fun location(consumer: String?): PropertyTrace
    }

    private
    val projectForThread = ThreadLocal<Path>()

    private
    val capturedFiles = newConcurrentHashSet<File>()

    private
    val undeclaredGradleProperties = newConcurrentHashSet<String>()

    private
    val undeclaredSystemProperties = newConcurrentHashSet<String>()

    private
    val undeclaredEnvironmentVariables = newConcurrentHashSet<String>()

    private
    var closestChangingValue: ConfigurationCacheFingerprint.ChangingDependencyResolutionValue? = null

    init {
        val initScripts = host.allInitScripts
        capturedFiles.addAll(initScripts)
        write(
            ConfigurationCacheFingerprint.InitScripts(
                initScripts.map(::inputFile)
            )
        )
        write(
            ConfigurationCacheFingerprint.GradleEnvironment(
                host.gradleUserHomeDir,
                jvmFingerprint()
            )
        )
    }

    /**
     * Finishes writing to the given [writeContext] and closes it.
     *
     * **MUST ALWAYS BE CALLED**
     */
    fun close() {
        // we synchronize access to all resources used by callbacks
        // in case there was still an event being dispatched at closing time.
        synchronized(writeContext) {
            synchronized(this) {
                if (closestChangingValue != null) {
                    unsafeWrite(closestChangingValue)
                }
            }
            unsafeWrite(null)
            writeContext.close()
        }
    }

    override fun onDynamicVersionSelection(requested: ModuleComponentSelector, expiry: Expiry, versions: Set<ModuleVersionIdentifier>) {
        // Only consider repositories serving at least one version of the requested module.
        // This is meant to avoid repetitively expiring cache entries due to a 404 response for the requested module metadata
        // from one of the configured repositories.
        if (versions.isEmpty()) return
        val expireAt = host.buildStartTime + expiry.keepFor.toMillis()
        onChangingValue(ConfigurationCacheFingerprint.DynamicDependencyVersion(requested.displayName, expireAt))
    }

    override fun onChangingModuleResolve(moduleId: ModuleComponentIdentifier, expiry: Expiry) {
        val expireAt = host.buildStartTime + expiry.keepFor.toMillis()
        onChangingValue(ConfigurationCacheFingerprint.ChangingModule(moduleId.displayName, expireAt))
    }

    private
    fun onChangingValue(changingValue: ConfigurationCacheFingerprint.ChangingDependencyResolutionValue) {
        synchronized(this) {
            if (closestChangingValue == null || closestChangingValue!!.expireAt > changingValue.expireAt) {
                closestChangingValue = changingValue
            }
        }
    }

    override fun fileObserved(file: File) {
        captureFile(file)
    }

    private
    fun gradlePropertyRead(key: String, value: String?, consumer: String?) {
        if (undeclaredGradleProperties.add(key)) {
            write(ConfigurationCacheFingerprint.UndeclaredGradleProperty(key, value))
            reportGradlePropertyInput(key, consumer)
        }
    }

    override fun systemPropertyRead(key: String, value: Any?, consumer: String?) {
        if (undeclaredSystemProperties.add(key)) {
            write(ConfigurationCacheFingerprint.UndeclaredSystemProperty(key, value))
            reportSystemPropertyInput(key, consumer)
        }
    }

    override fun envVariableRead(key: String, value: String?, consumer: String?) {
        if (undeclaredEnvironmentVariables.add(key)) {
            write(ConfigurationCacheFingerprint.UndeclaredEnvironmentVariable(key, value))
            reportEnvironmentVariableInput(key, consumer)
        }
    }

    override fun <T : Any, P : ValueSourceParameters> valueObtained(
        obtainedValue: ValueSourceProviderFactory.Listener.ObtainedValue<T, P>
    ) {
        when (val parameters = obtainedValue.valueSourceParameters) {
            is FileContentValueSource.Parameters -> {
                parameters.file.orNull?.asFile?.let { file ->
                    // TODO - consider the potential race condition in computing the hash code here
                    captureFile(file)
                }
            }
            is GradlePropertyValueSource.Parameters -> {
                gradlePropertyRead(parameters.propertyName.get(), obtainedValue.value.get() as? String, null)
            }
            is SystemPropertyValueSource.Parameters -> {
                systemPropertyRead(parameters.propertyName.get(), obtainedValue.value.get(), null)
            }
            is EnvironmentVariableValueSource.Parameters -> {
                envVariableRead(parameters.variableName.get(), obtainedValue.value.get() as? String, null)
            }
            else -> {
                captureValueSource(obtainedValue)
            }
        }
    }

    private
    fun <P : ValueSourceParameters, T : Any> captureValueSource(obtainedValue: ValueSourceProviderFactory.Listener.ObtainedValue<T, P>) {
        write(ValueSource(obtainedValue.uncheckedCast()))
        reportValueSourceInput(obtainedValue.valueSourceType)
    }

    override fun onScriptClassLoaded(source: ScriptSource, scriptClass: Class<*>) {
        source.resource.file?.let {
            captureFile(it)
        }
    }

    override fun onExecute(task: TaskInternal, fileSystemInputs: FileCollectionInternal) {
        captureTaskInputs(task, fileSystemInputs)
    }

    private
    fun captureFile(file: File) {
        if (!capturedFiles.add(file)) {
            return
        }
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
            ConfigurationCacheFingerprint.TaskInputs(
                task.identityPath.path,
                simplify(fileSystemInputs),
                host.fingerprintOf(fileSystemInputs)
            )
        )
    }

    fun <T> collectFingerprintForProject(identityPath: Path, action: () -> T): T {
        val previous = projectForThread.get()
        projectForThread.set(identityPath)
        try {
            return action()
        } finally {
            projectForThread.set(previous)
        }
    }

    private
    fun write(value: ConfigurationCacheFingerprint) {
        val project = projectForThread.get()
        val contextualized = if (project != null) {
            ConfigurationCacheFingerprint.ProjectSpecificInput(project.path, value)
        } else {
            value
        }
        synchronized(writeContext) {
            unsafeWrite(contextualized)
        }
    }

    private
    fun unsafeWrite(value: ConfigurationCacheFingerprint?) {
        writeContext.runWriteOperation {
            write(value)
        }
    }

    private
    fun simplify(source: FileCollectionInternal): FileCollectionInternal {
        // Transform the collection into a sequence of files or directory trees and remove dynamic behaviour
        val elements = mutableListOf<Any>()
        source.visitStructure(object : FileCollectionStructureVisitor {
            override fun visitCollection(source: FileCollectionInternal.Source, contents: Iterable<File>) {
                elements.addAll(contents)
            }

            override fun visitGenericFileTree(fileTree: FileTreeInternal, sourceTree: FileSystemMirroringFileTree) {
                elements.addAll(fileTree)
            }

            override fun visitFileTree(root: File, patterns: PatternSet, fileTree: FileTreeInternal) {
                elements.add(directoryFileTreeFactory.create(root, patterns))
            }

            override fun visitFileTreeBackedByFile(file: File, fileTree: FileTreeInternal, sourceTree: FileSystemMirroringFileTree) {
                elements.add(file)
            }
        })
        return fileCollectionFactory.resolving(elements)
    }

    private
    fun reportValueSourceInput(valueSourceType: Class<out Any>) {
        reportInput(consumer = null, documentationSection = null) {
            text("build logic input of type ")
            reference(valueSourceType.simpleName)
        }
    }

    private
    fun reportGradlePropertyInput(key: String, consumer: String?) {
        reportInput(consumer, DocumentationSection.RequirementsUndeclaredGradlePropRead) {
            text("Gradle property ")
            reference(key)
        }
    }

    private
    fun reportSystemPropertyInput(key: String, consumer: String?) {
        reportInput(consumer, DocumentationSection.RequirementsUndeclaredSysPropRead) {
            text("system property ")
            reference(key)
        }
    }

    private
    fun reportEnvironmentVariableInput(key: String, consumer: String?) {
        reportInput(consumer, DocumentationSection.RequirementsUndeclaredEnvVarRead) {
            text("environment variable ")
            reference(key)
        }
    }

    private
    fun reportInput(consumer: String?, documentationSection: DocumentationSection?, messageBuilder: StructuredMessage.Builder.() -> Unit) {
        reportInput(locationFor(consumer), documentationSection, messageBuilder)
    }

    private
    fun reportInput(location: PropertyTrace, documentationSection: DocumentationSection?, messageBuilder: StructuredMessage.Builder.() -> Unit) {
        host.reportInput(
            PropertyProblem(
                location,
                StructuredMessage.build(messageBuilder),
                null,
                documentationSection = documentationSection
            )
        )
    }

    private
    fun locationFor(consumer: String?) = host.location(consumer)
}


internal
fun jvmFingerprint() = String.format(
    "%s|%s|%s",
    System.getProperty("java.vm.name"),
    System.getProperty("java.vm.vendor"),
    System.getProperty("java.vm.version")
)
