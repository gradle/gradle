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
import org.gradle.internal.configuration.problems.DecoratedFailure
import org.gradle.internal.configuration.problems.DecoratedPropertyProblem
import org.gradle.internal.configuration.problems.FailureDecorator
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.hash.HashingOutputStream
import org.gradle.internal.problems.failure.Failure
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import kotlin.contracts.contract


@ServiceScope(Scope.BuildTree::class)
class ConfigurationCacheReport(
    executorFactory: ExecutorFactory,
    temporaryFileProvider: TemporaryFileProvider,
    internalOptions: InternalOptions
) : Closeable {

    companion object {
        private
        val stacktraceHashes = InternalFlag("org.gradle.configuration-cache.internal.report.stacktrace-hashes", false)
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
            buildDisplayName: String?,
            cacheAction: String,
            requestedTasks: String?,
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
                buildDisplayName: String?,
                cacheAction: String,
                requestedTasks: String?,
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
            val decorate: (PropertyProblem, ProblemSeverity) -> DecoratedPropertyProblem
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
                    val severity = if (kind == DiagnosticKind.PROBLEM) ProblemSeverity.Failure else ProblemSeverity.Info
                    writer.writeDiagnostic(kind, decorate(problem, severity))
                }
                return this
            }

            override fun commitReportTo(
                outputDirectory: File,
                buildDisplayName: String?,
                cacheAction: String,
                requestedTasks: String?,
                totalProblemCount: Int
            ): Pair<State, File?> {

                val reportFile = try {
                    executor
                        .submit(Callable {
                            closeHtmlReport(buildDisplayName, cacheAction, requestedTasks, totalProblemCount)
                            moveSpoolFileTo(outputDirectory)
                        })
                        .get(30, TimeUnit.SECONDS)
                } finally {
                    executor.shutdownAndAwaitTermination()
                }
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
            fun closeHtmlReport(buildDisplayName: String?, cacheAction: String, requestedTasks: String?, totalProblemCount: Int) {
                writer.endHtmlReport(buildDisplayName, cacheAction, requestedTasks, totalProblemCount)
                writer.close()
            }

            private
            fun ManagedExecutor.shutdownAndAwaitTermination() {
                shutdown()
                if (!awaitTermination(1, TimeUnit.SECONDS)) {
                    val unfinishedTasks = shutdownNow()
                    logger.warn(
                        "Configuration cache report is taking too long to write... "
                            + "The build might finish before the report has been completely written."
                    )
                    logger.info("Unfinished tasks: {}", unfinishedTasks)
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
    val isStacktraceHashes = internalOptions.getOption(stacktraceHashes).get()

    private
    var state: State = State.Idle { kind, problem ->
        State.Spooling(
            temporaryFileProvider.createTemporaryFile("configuration-cache-report", "html"),
            executorFactory.create("Configuration cache report writer", 1),
            CharBuf::class.java.classLoader,
            ::decorateProblem
        ).onDiagnostic(kind, problem)
    }

    private
    val stateLock = Object()

    private
    val failureDecorator = FailureDecorator()

    private
    fun decorateProblem(problem: PropertyProblem, severity: ProblemSeverity): DecoratedPropertyProblem {
        val failure = problem.stackTracingFailure
        return DecoratedPropertyProblem(
            problem.trace,
            decorateMessage(problem, failure),
            decoratedFailureFor(failure, severity),
            problem.documentationSection
        )
    }

    private
    fun decoratedFailureFor(failure: Failure?, severity: ProblemSeverity): DecoratedFailure? {
        return when {
            failure != null -> failureDecorator.decorate(failure)
            severity == ProblemSeverity.Failure -> DecoratedFailure.MARKER
            else -> null
        }
    }

    private
    fun decorateMessage(problem: PropertyProblem, failure: Failure?): StructuredMessage {
        if (!isStacktraceHashes || failure == null) {
            return problem.message
        }

        val failureHash = failure.hashWithoutMessages()
        return StructuredMessage.build {
            reference("[${failureHash.toCompactString()}]")
            text(" ")
            message(problem.message)
        }
    }

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
    fun writeReportFileTo(outputDirectory: File, buildDisplayName: String?, cacheAction: String, requestedTasks: String?, totalProblemCount: Int): File? {
        var reportFile: File?
        modifyState {
            val (newState, outputFile) = commitReportTo(outputDirectory, buildDisplayName, cacheAction, requestedTasks, totalProblemCount)
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

    /**
     * A heuristic to get the same hash for different instances of an exception
     * occurring at the same location.
     */
    private
    fun Failure.hashWithoutMessages(): HashCode {
        val root = this@hashWithoutMessages
        val hasher = Hashing.newHasher()
        for (failure in sequence { visitFailures(root) }) {
            hasher.putString(failure.exceptionType.name)
            for (element in failure.stackTrace) {
                hasher.putString(element.toString())
            }
        }
        return hasher.hash()
    }

    private
    suspend fun SequenceScope<Failure>.visitFailures(failure: Failure) {
        yield(failure)
        failure.suppressed.forEach { visitFailures(it) }
        failure.causes.forEach { visitFailures(it) }
    }
}
