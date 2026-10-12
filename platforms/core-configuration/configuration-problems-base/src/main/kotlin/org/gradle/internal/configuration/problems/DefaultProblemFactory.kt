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

import org.gradle.api.Action
import org.gradle.api.InvalidUserCodeException
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.code.UserCodeSource
import org.gradle.internal.problems.NoOpProblemDiagnosticsFactory
import org.gradle.problems.ProblemDiagnostics
import org.gradle.problems.buildtree.ProblemDiagnosticsFactory


class DefaultProblemFactory(
    private val userCodeContext: UserCodeApplicationContext,
    problemDiagnosticsFactory: ProblemDiagnosticsFactory
) : ProblemFactory {
    private
    val problemStream = problemDiagnosticsFactory.newStream()

    override fun locationForCaller(consumer: String?): PropertyTrace =
        locationForCaller(consumer, userCodeContext.current()?.source)

    override fun problem(message: StructuredMessage, exception: Throwable?, documentationSection: DocumentationSection?, getStackTrace: Boolean): PropertyProblem {
        val diagnostics = getProblemDiagnostics(exception, getStackTrace)
        val trace = locationForCaller(null, diagnostics)
        return PropertyProblem(trace, message, exception, diagnostics.failure, documentationSection)
    }

    private fun getProblemDiagnostics(exception: Throwable?, getStackTrace: Boolean): ProblemDiagnostics {
        if (!getStackTrace) {
            return NoOpProblemDiagnosticsFactory.EMPTY_DIAGNOSTICS
        }
        return if (exception != null) {
            problemStream.forThrownException(exception)
        } else {
            problemStream.forCurrentCaller()
        }
    }

    override fun problem(consumer: String?, message: Action<StructuredMessage.Builder>): ProblemFactory.Builder {
        val builtMessage = StructuredMessage.build { message.execute(this) }
        return object : ProblemFactory.Builder {
            var failure = true
            var exceptionMessageBuilder: ((String) -> String)? = null
            var documentationSection: DocumentationSection? = null
            var locationMapper: (PropertyTrace) -> PropertyTrace = { it }

            override fun informational(): ProblemFactory.Builder {
                failure = false
                return this
            }

            override fun exceptionMessage(message: (String) -> String): ProblemFactory.Builder {
                exceptionMessageBuilder = message
                return this
            }

            override fun mapLocation(mapper: (PropertyTrace) -> PropertyTrace): ProblemFactory.Builder {
                locationMapper = mapper
                return this
            }

            override fun documentationSection(documentationSection: DocumentationSection): ProblemFactory.Builder {
                this.documentationSection = documentationSection
                return this
            }

            override fun build(): PropertyProblem {
                val diagnostics = if (failure) {
                    problemStream.forCurrentCallerWithException { InvalidUserCodeException(exceptionMessage()) }
                } else {
                    problemStream.forCurrentCaller()
                }
                val location = locationMapper(locationForCaller(consumer, diagnostics))
                return PropertyProblem(location, builtMessage, diagnostics.exception, diagnostics.failure, documentationSection)
            }

            private fun exceptionMessage(): String {
                val message = builtMessage.renderCapitalized()
                return exceptionMessageBuilder?.invoke(message) ?: message
            }
        }
    }

    private
    fun locationForCaller(consumer: String?, diagnostics: ProblemDiagnostics): PropertyTrace {
        val location = diagnostics.location
        return if (location != null) {
            PropertyTrace.BuildLogic(location)
        } else {
            locationForCaller(consumer, diagnostics.source)
        }
    }

    private
    fun locationForCaller(consumer: String?, source: UserCodeSource?): PropertyTrace {
        return if (source != null) {
            PropertyTrace.BuildLogic(source)
        } else if (consumer != null) {
            PropertyTrace.BuildLogicClass(consumer)
        } else {
            PropertyTrace.Unknown
        }
    }
}
