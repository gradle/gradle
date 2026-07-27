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

package gradlebuild.basics.util


import com.google.common.annotations.VisibleForTesting
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironmentMode
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.setupIdeaStandaloneExecution
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.KtFile
import java.io.File


class KotlinSourceParser {

    data class ParsedKotlinFiles(

        val ktFiles: List<KtFile>,

        private
        val disposable: Disposable

    ) : AutoCloseable {

        override fun close() {
            Disposer.dispose(disposable)
        }
    }

    fun <T : Any> mapParsedKotlinFiles(vararg sourceRoots: File, block: (KtFile) -> T): List<T> =
        withParsedKotlinSource(sourceRoots.toList()) { ktFiles ->
            ktFiles.map(block)
        }

    fun parseSourceRoots(sourceRoots: List<File>): ParsedKotlinFiles =
        Disposer.newDisposable().let { disposable ->
            ParsedKotlinFiles(disposable.parseKotlinFiles(sourceRoots), disposable)
        }

    private
    fun <T> withParsedKotlinSource(sourceRoots: List<File>, block: (List<KtFile>) -> T) =
        Disposer.newDisposable().use {
            parseKotlinFiles(sourceRoots).let(block)
        }

    private
    fun Disposable.parseKotlinFiles(sourceRoots: List<File>): List<KtFile> {
        val applicationEnvironment = SharedKotlinApplicationEnvironment.acquire()
        Disposer.register(this, SharedKotlinApplicationEnvironment.newReleaseDisposable())
        val psiManager = PsiManager.getInstance(KotlinCoreProjectEnvironment(this, applicationEnvironment).project)
        val localFileSystem = applicationEnvironment.localFileSystem
        return sourceRoots.asSequence()
            .flatMap { it.walkTopDown() }
            .filter { it.isFile && (it.extension == KotlinFileType.EXTENSION || it.extension == KotlinFileType.SCRIPT_EXTENSION) }
            .mapNotNull { sourceFile ->
                localFileSystem.findFileByPath(sourceFile.canonicalFile.invariantSeparatorsPath)
                    ?.let { psiManager.findFile(it) as? KtFile }
            }
            .toList()
    }
}


/**
 * The Kotlin compiler application environment shared by all live [KotlinCoreProjectEnvironment]s.
 *
 * It is expensive to create and installs a process-global [org.jetbrains.kotlin.com.intellij.openapi.application.Application].
 * A single parsing spans multiple source roots, each kept open in its own project environment until the parsing
 * is done, so they share one reference-counted application environment: created with the first project environment,
 * disposed once the last is closed. Disposal resets the global `ApplicationManager`, keeping it out of the
 * long-lived Gradle daemon between runs.
 */
internal
object SharedKotlinApplicationEnvironment {

    @get:VisibleForTesting
    var environment: KotlinCoreApplicationEnvironment? = null
        private set

    private
    var openProjectEnvironments = 0

    @Synchronized
    fun acquire(): KotlinCoreApplicationEnvironment {
        val environment = environment ?: create().also { environment = it }
        openProjectEnvironments++
        return environment
    }

    @Synchronized
    private
    fun release() {
        if (--openProjectEnvironments <= 0) {
            openProjectEnvironments = 0
            environment?.let { Disposer.dispose(it.parentDisposable) }
            environment = null
        }
    }

    /**
     * Returns a fresh [Disposable] that releases the shared environment when disposed; each parse registers one for cleanup.
     *
     * It must be a new instance per parse: [Disposer] disposes any given [Disposable] only once, so sharing one across
     * parses would release only once (hence a non-capturing lambda won't do).
     */
    fun newReleaseDisposable(): Disposable = object : Disposable {
        override fun dispose() = release()
    }

    private
    fun create(): KotlinCoreApplicationEnvironment {
        setIdeaIoUseFallback()
        setupIdeaStandaloneExecution()
        val disposable = Disposer.newDisposable("Disposable for the application environment of KotlinSourceParser")
        return KotlinCoreApplicationEnvironment.create(disposable, KotlinCoreApplicationEnvironmentMode.Production).apply {
            registerFileType(KotlinFileType.INSTANCE, KotlinFileType.EXTENSION)
            registerFileType(KotlinFileType.INSTANCE, KotlinFileType.SCRIPT_EXTENSION)
            registerParserDefinition(KotlinParserDefinition())
        }
    }
}


private
inline fun <T> Disposable.use(action: Disposable.() -> T) =
    try {
        action(this)
    } finally {
        Disposer.dispose(this)
    }
