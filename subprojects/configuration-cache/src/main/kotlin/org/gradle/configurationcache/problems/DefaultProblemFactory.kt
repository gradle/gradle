/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.InvalidUserCodeException
import org.gradle.configuration.internal.UserCodeApplicationContext
import org.gradle.problems.buildtree.ProblemLocationAnalyzer


class DefaultProblemFactory(
    private val userCodeContext: UserCodeApplicationContext,
    private val locationAnalyzer: ProblemLocationAnalyzer
) : ProblemFactory {
    override fun locationForCaller(consumer: String?): PropertyTrace {
        val currentApplication = userCodeContext.current()
        return if (currentApplication != null) {
            PropertyTrace.BuildLogic(currentApplication.displayName, null)
        } else if (consumer != null) {
            PropertyTrace.BuildLogicClass(consumer)
        } else {
            PropertyTrace.Unknown
        }
    }

    override fun problem(message: StructuredMessage, exception: Throwable?, documentationSection: DocumentationSection?): PropertyProblem {
        val trace = locationForCaller(exception)
        return PropertyProblem(trace, message, exception, documentationSection)
    }

    override fun problem(messageBuilder: StructuredMessage.Builder.() -> Unit): ProblemFactory.Builder {
        val message = StructuredMessage.build(messageBuilder)
        return object : ProblemFactory.Builder {
            var exception: Throwable? = null
            var documentationSection: DocumentationSection? = null
            var locationMapper: (PropertyTrace) -> PropertyTrace = { it }

            override fun exception(message: String): ProblemFactory.Builder {
                exception = InvalidUserCodeException(message)
                return this
            }

            override fun exception(): ProblemFactory.Builder {
                exception = InvalidUserCodeException(message.toString())
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
                val location = locationMapper(locationForCaller(exception))
                return PropertyProblem(location, message, exception, documentationSection)
            }
        }
    }

    private
    fun locationForCaller(exception: Throwable?): PropertyTrace {
        if (exception != null) {
            val location = locationAnalyzer.locationForUsage(exception.stackTrace.toList(), false)
            if (location != null) {
                return PropertyTrace.BuildLogic(location.sourceShortDisplayName, location.lineNumber)
            }
        }
        return locationForCaller(null as String?)
    }
}
