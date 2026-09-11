/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.kotlin.dsl.support

import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.invocation.Gradle
import org.gradle.internal.InternalBuildAdapter
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.service.scopes.ListenerService
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.kotlin.dsl.cache.KotlinDslClasspathEntrySnapshotCache
import org.gradle.kotlin.dsl.cache.KotlinDslIncrementalCompilationCache
import org.jetbrains.kotlin.name.NameUtils
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.reflect.KClass
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.util.PropertiesCollection


@ServiceScope(Scope.BuildTree::class)
internal interface KotlinCompiler {
    fun compileKotlinScriptToDirectory(
        outputDirectory: File,
        compilerOptions: KotlinCompilerOptions,
        scriptFile: File,
        implicitImports: List<String>,
        template: KClass<out Any>,
        classPath: List<File>,
        logger: Logger,
        fileSystemAccess: FileSystemAccess,
        classpathSnapshotCache: KotlinDslClasspathEntrySnapshotCache,
        incrementalCompilationCache: KotlinDslIncrementalCompilationCache,
        scriptIdentity: String,
        pathTranslation: (String) -> String
    ): String

    fun implicitReceiverOf(template: KClass<*>): KClass<*>?
}

@ListenerService
@ServiceScope(Scope.BuildTree::class)
internal
class DefaultKotlinCompiler(private val moduleRegistry: ModuleRegistry) : InternalBuildAdapter(), KotlinCompiler, Stoppable {

    private var btaCompiler: BTACompiler? = null

    override fun projectsEvaluated(gradle: Gradle) {
        // Reclaim the compiler before task execution on the happy path. This listener receives every
        // build's event; act only on the root build's, when the whole tree is configured.
        if (gradle.parent == null) {
            stop()
        }
    }

    // Also runs at build tree close: covers config failure, config-cache hits and tooling-api paths
    // that never reach projectsEvaluated. A later compile opens a new session.
    @Synchronized
    override fun stop() {
        btaCompiler?.close()
        btaCompiler = null
        receiverCache.clear()
        BtaClasspathSnapshotter.closeSession()
    }

    // Under Isolated Projects, projects' scripts are compiled concurrently; only session access is
    // synchronized, compilation runs outside the lock.
    @Synchronized
    private fun btaCompiler(): BTACompiler =
        btaCompiler ?: BTACompiler(moduleRegistry).also { btaCompiler = it }

    override fun compileKotlinScriptToDirectory(
        outputDirectory: File,
        compilerOptions: KotlinCompilerOptions,
        scriptFile: File,
        implicitImports: List<String>,
        template: KClass<out Any>,
        classPath: List<File>,
        logger: Logger,
        fileSystemAccess: FileSystemAccess,
        classpathSnapshotCache: KotlinDslClasspathEntrySnapshotCache,
        incrementalCompilationCache: KotlinDslIncrementalCompilationCache,
        scriptIdentity: String,
        pathTranslation: (String) -> String
    ): String {
        compileKotlinScriptToDirectory(
            outputDirectory,
            compilerOptions,
            scriptFile,
            implicitImports,
            template,
            classPath,
            messageCollectorFor(logger, compilerOptions.allWarningsAsErrors, pathTranslation),
            fileSystemAccess,
            classpathSnapshotCache,
            incrementalCompilationCache,
            scriptIdentity
        )

        return NameUtils.getScriptNameForFile(scriptFile.name).asString()
    }

    private val receiverCache: MutableMap<KClass<*>, KClass<*>> = ConcurrentHashMap()

    override fun implicitReceiverOf(template: KClass<*>): KClass<*>? {
        return receiverCache.getOrPut(template) {
            val compilationConfigurationClass: KClass<out ScriptCompilationConfiguration>? = template.annotations.firstNotNullOfOrNull { (it as? KotlinScript)?.compilationConfiguration }
            return compilationConfigurationClass?.let {
                val compileConfiguration = scriptConfigInstance(compilationConfigurationClass)
                compileConfiguration?.get(ScriptCompilationConfiguration.implicitReceivers)?.firstOrNull()?.fromClass
            }
        }
    }


    private
    fun compileKotlinScriptToDirectory(
        outputDirectory: File,
        compilerOptions: KotlinCompilerOptions,
        scriptFile: File,
        implicitImports: List<String>,
        template: KClass<out Any>,
        classPath: List<File>,
        messageRenderer: LoggingMessageRenderer,
        fileSystemAccess: FileSystemAccess,
        classpathSnapshotCache: KotlinDslClasspathEntrySnapshotCache,
        incrementalCompilationCache: KotlinDslIncrementalCompilationCache,
        scriptIdentity: String
    ) {
        CompilerOutput.withRedirecting(messageRenderer.log) {
            btaCompiler().compile(
                listOf(Path(scriptFile.path)),
                outputDirectory.toPath(),
                compilerOptions,
                classPath,
                template,
                implicitImports,
                messageRenderer,
                fileSystemAccess,
                classpathSnapshotCache,
                incrementalCompilationCache,
                scriptIdentity
            )
            if (messageRenderer.errors.isNotEmpty()) {
                throw ScriptCompilationException(messageRenderer.errors)
            }
        }
    }

}


private
inline fun <reified T : PropertiesCollection> scriptConfigInstance(kclass: KClass<out T>): T? =
    kclass.objectInstance ?: run {
        val noArgsConstructor = kclass.java.constructors.singleOrNull { it.parameters.isEmpty() }
        noArgsConstructor?.let {
            try {
                it.isAccessible = true
            } catch (_: RuntimeException) {
            }
            it.newInstance() as T
        }
    }
