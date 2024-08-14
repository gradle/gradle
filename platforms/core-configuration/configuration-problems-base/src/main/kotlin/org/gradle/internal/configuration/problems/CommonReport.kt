/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.configuration.problems

import org.apache.groovy.json.internal.CharBuf
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.buildoption.InternalFlag
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.cc.impl.problems.HtmlReportWriter
import org.gradle.internal.cc.impl.problems.JsonModelWriter
import org.gradle.internal.cc.impl.problems.JsonSource
import org.gradle.internal.cc.impl.problems.JsonWriter
import org.gradle.internal.cc.impl.problems.ProblemSeverity
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.extensions.stdlib.capitalized
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
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

val logger: Logger = getLogger(CommonReport::class.java)

enum class DiagnosticKind {
    PROBLEM,
    INPUT,
    INCOMPATIBLE_TASK
}

@ServiceScope(Scope.BuildTree::class)
class CommonReport(
    executorFactory: ExecutorFactory,
    temporaryFileProvider: TemporaryFileProvider,
    internalOptions: InternalOptions,
    reportContext: String,
    reportFileName: String
) : Closeable {

    companion object {
        private
        val stacktraceHashes = InternalFlag("org.gradle.configuration-cache.internal.report.stacktrace-hashes", false)
    }

    private
    val isStacktraceHashes = internalOptions.getOption(stacktraceHashes).get()

    private
    val documentationRegistry = DocumentationRegistry()


    private
    fun keyFor(kind: DiagnosticKind) = when (kind) {
        DiagnosticKind.PROBLEM -> "problem"
        DiagnosticKind.INPUT -> "input"
        DiagnosticKind.INCOMPATIBLE_TASK -> "incompatibleTask"
    }

    private
    fun problemSeverity(kind: DiagnosticKind): ProblemSeverity {
        return when (kind) {
            DiagnosticKind.PROBLEM -> ProblemSeverity.Failure
            DiagnosticKind.INCOMPATIBLE_TASK -> ProblemSeverity.Warning
            DiagnosticKind.INPUT -> ProblemSeverity.Info
        }
    }


    sealed class State {

        open fun onDiagnostic(problem: JsonSource): State =
            illegalState()

        /**
         * Writes the report file to the given [outputDirectory] if and only if
         * there are diagnostics to report.
         *
         * @return a pair with the new [State] and the written [File], which will be `null` when there are no diagnostics.
         */
        open fun commitReportTo(
            outputDirectory: File,
            details: JsonSource
        ): Pair<State, File?> =
            illegalState()

        open fun close(): State =
            illegalState()

        private
        fun illegalState(): Nothing =
            error("Operation is not valid in ${javaClass.simpleName} state.")

        class Idle(
            private val onFirstDiagnostic: (problem: JsonSource) -> State
        ) : State() {

            /**
             * There's nothing to write, return null.
             */
            override fun commitReportTo(
                outputDirectory: File,
                details: JsonSource
            ): Pair<State, File?> =
                this to null

            override fun onDiagnostic(problem: JsonSource): State =
                onFirstDiagnostic(problem)

            override fun close(): State =
                this
        }

        class Spooling(
            spoolFileProvider: TemporaryFileProvider,
            private val reportFileName: String,
            val executor: ManagedExecutor,
            /**
             * [JsonModelWriter] uses Groovy's [CharBuf] for fast json encoding.
             */
            private val groovyJsonClassLoader: ClassLoader
        ) : State() {

            private
            val spoolFile = spoolFileProvider.createTemporaryFile(reportFileName, ".html")

            private
            val hashingStream = HashingOutputStream(Hashing.md5(), spoolFile.outputStream().buffered())

            private
            val writer = createHtmlReportWriter(hashingStream)

            init {
                executor.submit {
                    Thread.currentThread().contextClassLoader = groovyJsonClassLoader
                    writer.beginHtmlReport()
                }
            }

            private fun createHtmlReportWriter(hashingStream: HashingOutputStream): HtmlReportWriter {
                val htmlReportTemplateLoader = HtmlReportTemplateLoader().load()
                val hashingWriter = hashingStream.writer()
                val jsonModelWriter = JsonModelWriter(JsonWriter(hashingWriter))
                return HtmlReportWriter(hashingWriter, htmlReportTemplateLoader, jsonModelWriter)
            }

            override fun onDiagnostic(problem: JsonSource): State {
                executor.submit {
                    problem.writeToJson(writer.jsonModelWriter.modelWriter)
                }
                return this
            }

            override fun commitReportTo(
                outputDirectory: File,
                details: JsonSource
            ): Pair<State, File?> {

                val reportFile = try {
                    executor
                        .submit(Callable {
                            closeHtmlReport(details)
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
            fun closeHtmlReport(details: JsonSource) {
                writer.endHtmlReport(details)
                writer.close()
            }

            private
            fun ManagedExecutor.shutdownAndAwaitTermination() {
                shutdown()
                if (!awaitTermination(1, TimeUnit.SECONDS)) {
                    val unfinishedTasks = shutdownNow()
                    logger.warn(
                        "${reportFileName.capitalized()} is taking too long to write... "
                            + "The build might finish before the report has been completely written."
                    )
                    logger.info("Unfinished tasks: {}", unfinishedTasks)
                }
            }

            private
            fun moveSpoolFileTo(outputDirectory: File): File {
                val reportDir = outputDirectory.resolve(reportHash())
                val reportFile = reportDir.resolve("$reportFileName.html")
                if (!reportFile.exists()) {
                    if (!reportDir.exists()) {
                        require(reportDir.mkdirs()) {
                            "Could not create $reportFileName directory '$reportDir'"
                        }
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
    var state: State = State.Idle { problem ->

        State.Spooling(
            temporaryFileProvider,
            reportFileName,
            executorFactory.create("${reportContext.capitalized()} writer", 1),
            CharBuf::class.java.classLoader
        ).onDiagnostic(problem)
    }

    private
    val stateLock = Object()

    private
    val failureDecorator = FailureDecorator()

    private
    fun decorateProblem(problem: PropertyProblem, severity: ProblemSeverity, kind: String): JsonSource {
        val failure = problem.stackTracingFailure
        val link = problem.documentationSection?.let { section ->
            this.documentationRegistry.documentationLinkFor(section)
        }
        return DecoratedReportProblemJsonSource(
            DecoratedReportProblem(
                problem.trace,
                decorateMessage(problem, failure),
                decoratedFailureFor(failure, severity),
                link,
                kind
            )
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
        onPropertyProblem(DiagnosticKind.PROBLEM, problem)
    }

    fun onIncompatibleTask(problem: PropertyProblem) {
        onPropertyProblem(DiagnosticKind.INCOMPATIBLE_TASK, problem)
    }

    fun onInput(problem: PropertyProblem) {
        onPropertyProblem(DiagnosticKind.INPUT, problem)
    }

    private fun onPropertyProblem(
        kind: DiagnosticKind,
        problem: PropertyProblem
    ) {
        onProblem(decorateProblem(problem, problemSeverity(kind), keyFor(kind)))
    }

    fun onProblem(decoratedProblem: JsonSource) {
        modifyState {
            onDiagnostic(decoratedProblem)
        }
    }

    /**
     * Writes the report file to [outputDirectory].
     *
     * The file is laid out in such a way as to allow extracting the pure JSON model,
     * see [HtmlReportWriter].
     */

    fun writeReportFileTo(outputDirectory: File, details: JsonSource): File? {
        var reportFile: File?
        modifyState {
            val (newState, outputFile) = commitReportTo(outputDirectory, details)
            reportFile = outputFile
            newState
        }
        return reportFile
    }

    @OptIn(ExperimentalContracts::class)
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
