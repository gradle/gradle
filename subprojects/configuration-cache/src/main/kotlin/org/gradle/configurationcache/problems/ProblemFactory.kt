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

import org.gradle.internal.service.scopes.EventScope
import org.gradle.internal.service.scopes.Scopes


@EventScope(Scopes.BuildTree::class)
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
    fun problem(message: StructuredMessage, exception: Throwable? = null, documentationSection: DocumentationSection? = null): PropertyProblem

    /**
     * Creates a problem with the given message.
     *
     * By default, the problem has no exception or documentation, and a default location is inferred from the calling thread's state.
     */
    fun problem(messageBuilder: StructuredMessage.Builder.() -> Unit): Builder

    interface Builder {
        /**
         * Creates a InvalidUserCodeException with the given message for this problem.
         */
        fun exception(message: String): Builder

        /**
         * Creates a InvalidUserCodeException message for this problem.
         */
        fun exception(): Builder

        fun documentationSection(documentationSection: DocumentationSection): Builder

        /**
         * Allows the default location to be changed. The closure is called by `build()`
         */
        fun mapLocation(mapper: (PropertyTrace) -> PropertyTrace): Builder

        fun build(): PropertyProblem
    }
}
