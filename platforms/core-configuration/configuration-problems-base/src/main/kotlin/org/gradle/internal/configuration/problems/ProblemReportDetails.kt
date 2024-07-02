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

import org.gradle.api.internal.DocumentationRegistry


data class ProblemReportDetails(
    val buildDisplayName: String?,
    val cacheAction: String,
    val cacheActionDescription: StructuredMessage,
    val requestedTasks: String?,
    val totalProblemCount: Int
) : JsonSource {
    override fun writeToJson(jsonWriter: JsonModelWriterCommon) {
        with(jsonWriter) {
            property("totalProblemCount") {
                write(totalProblemCount.toString())
            }
            buildDisplayName?.let {
                comma()
                property("buildName", it)
            }
            requestedTasks?.let {
                comma()
                property("requestedTasks", it)
            }
            comma()
            property("cacheAction", cacheAction)
            comma()
            property("cacheActionDescription") {
                writeStructuredMessage(cacheActionDescription)
            }
            comma()
            property("documentationLink", DocumentationRegistry().getDocumentationFor("configuration_cache"))
        }
    }
}

