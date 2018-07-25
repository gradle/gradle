/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.plugins.jsoup

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskInputs
import org.jsoup.nodes.Document

import org.gradle.kotlin.dsl.*


private
val DEFAULT_TRANSFORM_EXTENSIONS = listOf("html")


class JsoupTransformTarget(val document: Document, val fileCopyDetails: FileCopyDetails)


open class JsoupCopyExtension(private val task: Copy) {

    val project: Project
        get() = task.project

    val inputs: TaskInputs
        get() = task.inputs

    fun plugins(vararg plugins: Any) =
        project.files(plugins).forEach {
            inputs.file(it)
            project.apply {
                from(it)
                to(this@JsoupCopyExtension)
            }
        }

    fun transform(action: Action<JsoupTransformTarget>) =
        transform(DEFAULT_TRANSFORM_EXTENSIONS, action)

    fun transform(extensions: List<String>, action: Action<JsoupTransformTarget>) {
        task.eachFile {
            if (extensions.any { name.endsWith(".$it") }) {
                filter<JsoupFilterReader>("fileCopyDetails" to this, "action" to action)
            }
        }
    }

    fun transformDocument(action: Action<Document>) =
        transformDocument(DEFAULT_TRANSFORM_EXTENSIONS, action)

    fun transformDocument(extensions: List<String>, action: Action<Document>) =
        transform(extensions, Action {
            action(document)
        })
}
