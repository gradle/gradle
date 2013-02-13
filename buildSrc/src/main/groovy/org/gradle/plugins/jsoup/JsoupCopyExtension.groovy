/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskInputs
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.ClosureBackedAction
import org.jsoup.nodes.Document

class JsoupCopyExtension {

    public static final List<String> DEFAULT_TRANSFORM_EXTENSIONS = ["html"].asImmutable()

    final Copy task

    JsoupCopyExtension(Copy task) {
        this.task = task
    }

    void plugins(Object... plugins) {
        task.project.files(plugins).each {
            task.inputs.file(it)
            task.project.apply from: it, to: this
        }
    }

    void transform(Action<JsoupTransformTarget> action) {
        transform(DEFAULT_TRANSFORM_EXTENSIONS as String[], action)
    }

    void transform(String[] extensions, Closure action) {
        transform(extensions, new ClosureBackedAction(action))
    }

    void transform(String[] extensions, Action<JsoupTransformTarget> action) {
        task.eachFile { FileCopyDetails fcd ->
            if (extensions.any { fcd.name.endsWith(".$it") }) {
                fcd.filter(fileCopyDetails: fcd, action: action, JsoupFilterReader)
            }
        }
    }

    void transformDocument(Action<Document> action) {
        transformDocument(DEFAULT_TRANSFORM_EXTENSIONS as String[], action)
    }

    void transformDocument(String[] extensions, Closure action) {
        transformDocument(extensions, new ClosureBackedAction(action))
    }

    void transformDocument(String[] extensions, Action<Document> action) {
        transform(extensions, new Action<JsoupTransformTarget>() {
            void execute(JsoupTransformTarget target) {
                action.execute(target.document)
            }
        })
    }

    Project getProject() {
        task.project
    }

    TaskInputs getInputs() {
        task.inputs
    }

}
