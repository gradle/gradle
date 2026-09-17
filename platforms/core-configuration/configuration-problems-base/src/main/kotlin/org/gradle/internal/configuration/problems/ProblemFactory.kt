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
import org.gradle.internal.service.scopes.EventScope
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope


@ServiceScope(Scope.BuildTree::class)
@EventScope(Scope.BuildTree::class)
interface ProblemFactory {
    /**
     * Returns a default location inferred from the calling thread's state.
     */
    fun locationForCaller(consumer: String? = null): PropertyTrace

    /**
     * Creates a problem with the given message and exception.
     *
     * Problem has no documentation, and a default location is inferred from the calling thread's state.
     */
    fun problem(message: StructuredMessage, exception: Throwable? = null, documentationSection: DocumentationSection? = null, getStackTrace: Boolean = true): PropertyProblem

    /**
     * Creates a problem with the given message.
     *
     * By default, the problem has no exception or documentation, and a default location is inferred from the calling thread's state.
     */
    fun problem(consumer: String?, message: Action<StructuredMessage.Builder>): Builder

    /**
     * Creates a problem with the given message, attributed to no particular consumer.
     */
    fun problem(message: Action<StructuredMessage.Builder>): Builder = problem(null, message)

    interface Builder {

        /**
         * Marks this problem as reporting a state rather than blaming user code, so it carries no exception.
         *
         * Such a problem can still appear in the report and the summary, but it shouldn't fail the build.
         */
        fun informational(): Builder

        /**
         * Replaces the exception message, which by default repeats the problem message.
         *
         * Use it only for detail too specific to put in the problem message, which we keep generic because
         * we group problems by it. Treat the exception as a deeper level of detail: expect to lose this
         * detail once the stack-capture budget runs out.
         */
        fun exceptionMessage(message: (String) -> String): Builder

        fun documentationSection(documentationSection: DocumentationSection): Builder

        /**
         * Allows the default location to be changed. The function is called by `build()`
         */
        fun mapLocation(mapper: (PropertyTrace) -> PropertyTrace): Builder

        fun build(): PropertyProblem
    }
}
