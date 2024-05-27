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

import com.google.common.base.Supplier
import org.gradle.api.InvalidUserCodeException
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.code.UserCodeSource
import org.gradle.internal.extensions.stdlib.capitalized
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

    override fun problem(message: StructuredMessage, exception: Throwable?, documentationSection: DocumentationSection?): PropertyProblem {
        val diagnostics = problemStream.forCurrentCaller(exception)
        val trace = locationForCaller(null, diagnostics)
        return PropertyProblem(trace, message, exception, diagnostics.failure, documentationSection)
    }

    override fun problem(consumer: String?, messageBuilder: StructuredMessage.Builder.() -> Unit): ProblemFactory.Builder {
        val message = StructuredMessage.build(messageBuilder)
        return object : ProblemFactory.Builder {
            var exceptionMessage: String? = null
            var documentationSection: DocumentationSection? = null
            var locationMapper: (PropertyTrace) -> PropertyTrace = { it }

            override fun exception(message: String): ProblemFactory.Builder {
                exceptionMessage = message
                return this
            }

            override fun exception(): ProblemFactory.Builder {
                exceptionMessage = message.toString().capitalized()
                return this
            }

            override fun exception(builder: (String) -> String): ProblemFactory.Builder {
                exceptionMessage = builder(message.toString().capitalized())
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
                val exceptionMessage = exceptionMessage
                val diagnostics = if (exceptionMessage == null) {
                    problemStream.forCurrentCaller()
                } else {
                    problemStream.forCurrentCaller(Supplier { InvalidUserCodeException(exceptionMessage) })
                }
                val location = locationMapper(locationForCaller(consumer, diagnostics))
                return PropertyProblem(location, message, diagnostics.exception, diagnostics.failure, documentationSection)
            }
        }
    }

    private
    fun locationForCaller(consumer: String?, diagnostics: ProblemDiagnostics): PropertyTrace {
        val location = diagnostics.location
        return if (location != null) {
            PropertyTrace.BuildLogic(location.sourceShortDisplayName, location.lineNumber)
        } else {
            locationForCaller(consumer, diagnostics.source)
        }
    }

    private
    fun locationForCaller(consumer: String?, source: UserCodeSource?): PropertyTrace {
        return if (source != null) {
            PropertyTrace.BuildLogic(source.displayName, null)
        } else if (consumer != null) {
            PropertyTrace.BuildLogicClass(consumer)
        } else {
            PropertyTrace.Unknown
        }
    }
}
