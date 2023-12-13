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

package org.gradle.configurationcache.problems

import org.apache.groovy.json.internal.CharBuf
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.configurationcache.logger
import org.gradle.internal.buildoption.InternalFlag
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.hash.HashingOutputStream
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.contracts.contract


@ServiceScope(Scopes.BuildTree::class)
class ConfigurationCacheReport(
    executorFactory: ExecutorFactory,
    temporaryFileProvider: TemporaryFileProvider,
    internalOptions: InternalOptions
) : Closeable {

    companion object {
        private
        val problemHashing = InternalFlag("org.gradle.configuration-cache.internal.report.problem-hashing", false)
    }

    private
    sealed class State {

        open fun onDiagnostic(kind: DiagnosticKind, problem: PropertyProblem): State =
            illegalState()

        /**
         * Writes the report file to the given [outputDirectory] if and only if
         * there are diagnostics to report.
         *
         * @return a pair with the new [State] and the written [File], which will be `null` when there are no diagnostics.
         */
        open fun commitReportTo(
            outputDirectory: File,
            cacheAction: String,
            requestedTasks: String,
            totalProblemCount: Int
        ): Pair<State, File?> =
            illegalState()

        open fun close(): State =
            illegalState()

        private
        fun illegalState(): Nothing =
            throw IllegalStateException("Operation is not valid in ${javaClass.simpleName} state.")

        class Idle(
            private val onFirstDiagnostic: (kind: DiagnosticKind, problem: PropertyProblem) -> State
        ) : State() {

            /**
             * There's nothing to write, return null.
             */
            override fun commitReportTo(
                outputDirectory: File,
                cacheAction: String,
                requestedTasks: String,
                totalProblemCount: Int
            ): Pair<State, File?> =
                this to null

            override fun onDiagnostic(kind: DiagnosticKind, problem: PropertyProblem): State =
                onFirstDiagnostic(kind, problem)

            override fun close(): State =
                this
        }

        class Spooling(
            val spoolFile: File,
            val executor: ManagedExecutor,
            /**
             * [JsonModelWriter] uses Groovy's [CharBuf] for fast json encoding.
             */
            val groovyJsonClassLoader: ClassLoader,
            val decorateProblem: ((PropertyProblem) -> PropertyProblem)? = null
        ) : State() {

            private
            val hashingStream = HashingOutputStream(Hashing.md5(), spoolFile.outputStream().buffered())

            private
            val writer = HtmlReportWriter(hashingStream.writer())

            init {
                executor.submit {
                    Thread.currentThread().contextClassLoader = groovyJsonClassLoader
                    writer.beginHtmlReport()
                }
            }

            override fun onDiagnostic(kind: DiagnosticKind, problem: PropertyProblem): State {
                executor.submit {
                    writer.writeDiagnostic(kind, decorateProblem?.invoke(problem) ?: problem)
                }
                return this
            }

            override fun commitReportTo(outputDirectory: File, cacheAction: String, requestedTasks: String, totalProblemCount: Int): Pair<State, File?> {
                lateinit var reportFile: File
                executor.submit {
                    closeHtmlReport(cacheAction, requestedTasks, totalProblemCount)
                    reportFile = moveSpoolFileTo(outputDirectory)
                }
                executor.shutdownAndAwaitTermination()
                return Closed to reportFile
            }

            override fun close(): State {
                if (executor.isShutdown) {
                    writer.close()
                } else {
                    executor.submit {
                        writer.close()
                    }
                    executor.shutdown()
                }
                return Closed
            }

            private
            fun closeHtmlReport(cacheAction: String, requestedTasks: String, totalProblemCount: Int) {
                writer.endHtmlReport(cacheAction, requestedTasks, totalProblemCount)
                writer.close()
            }

            private
            fun ManagedExecutor.shutdownAndAwaitTermination() {
                shutdown()
                if (!awaitTermination(30, TimeUnit.SECONDS)) {
                    logger.warn(
                        "Configuration cache report is taking too long to write... "
                            + "The build might finish before the report has been completely written."
                    )
                }
            }

            private
            fun moveSpoolFileTo(outputDirectory: File): File {
                val reportDir = outputDirectory.resolve(reportHash())
                val reportFile = reportDir.resolve(HtmlReportTemplate.reportHtmlFileName)
                if (!reportFile.exists()) {
                    require(reportDir.mkdirs()) {
                        "Could not create configuration cache report directory '$reportDir'"
                    }
                    Files.move(spoolFile.toPath(), reportFile.toPath())
                }
                return reportFile
            }

            private
            fun reportHash() =
                hashingStream.hash().toCompactString()
        }

        object Closed : State() {
            override fun close(): State = this
        }
    }

    private
    val isProblemHashing = internalOptions.getOption(problemHashing).get()

    private
    var state: State = State.Idle { kind, problem ->
        State.Spooling(
            temporaryFileProvider.createTemporaryFile("configuration-cache-report", "html"),
            executorFactory.create("Configuration cache report writer", 1),
            CharBuf::class.java.classLoader,
            if (isProblemHashing) ::decorateProblemWithHash else null
        ).onDiagnostic(kind, problem)
    }

    private
    val stateLock = Object()

    override fun close() {
        modifyState {
            close()
        }
    }

    fun onProblem(problem: PropertyProblem) {
        modifyState {
            onDiagnostic(DiagnosticKind.PROBLEM, problem)
        }
    }

    fun onInput(input: PropertyProblem) {
        modifyState {
            onDiagnostic(DiagnosticKind.INPUT, input)
        }
    }

    /**
     * Writes the report file to [outputDirectory].
     *
     * The file is laid out in such a way as to allow extracting the pure JSON model,
     * see [HtmlReportWriter].
     */
    internal
    fun writeReportFileTo(outputDirectory: File, cacheAction: String, requestedTasks: String, totalProblemCount: Int): File? {
        var reportFile: File?
        modifyState {
            val (newState, outputFile) = commitReportTo(outputDirectory, cacheAction, requestedTasks, totalProblemCount)
            reportFile = outputFile
            newState
        }
        return reportFile
    }

    private
    inline fun modifyState(f: State.() -> State) {
        contract {
            callsInPlace(f, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
        }
        synchronized(stateLock) {
            state = state.f()
        }
    }

    private
    fun decorateProblemWithHash(problem: PropertyProblem): PropertyProblem {
        val exception = problem.exception
            ?: return problem

        return hashedProblemFor(problem, exception)
    }

    private
    fun hashedProblemFor(problem: PropertyProblem, exception: Throwable): PropertyProblem {
        val exceptionHash = exception.hashWithoutMessage()
        val exceptionMarker = "[${exceptionHash.toCompactString()}]"
        return problem.prependMessageFragments(
            StructuredMessage.Fragment.Reference(exceptionMarker),
            StructuredMessage.Fragment.Text(" ")
        )
    }

    private
    fun PropertyProblem.prependMessageFragments(vararg fragments: StructuredMessage.Fragment): PropertyProblem {
        val newFragments = fragments.toList() + message.fragments
        return copy(message = message.copy(fragments = newFragments))
    }

    private
    fun Throwable.hashWithoutMessage(): HashCode {
        val exceptionType = this@hashWithoutMessage.javaClass.name
        return Hashing.newHasher().apply {
            putString(exceptionType)
            // Take all stacktrace lines except the first message
            stackTraceToString().lineSequence()
                .dropWhile { !it.trim().startsWith("at ") }
                .forEach { putString(it) }
        }.hash()
    }
}
