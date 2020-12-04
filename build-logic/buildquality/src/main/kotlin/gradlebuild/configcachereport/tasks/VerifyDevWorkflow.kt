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

package gradlebuild.configcachereport.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction


abstract class VerifyDevWorkflow : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val stageDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        stageDirectory.get().asFile.let { stage ->
            val stagedFiles = stage.listFiles()
            val expected = setOf(
                stage.resolve("configuration-cache-report.html"),
                stage.resolve("configuration-cache-report-data.js")
            )
            require(stagedFiles?.toSet() == expected) {
                "Unexpected staged files, found ${stagedFiles?.map { it.relativeTo(stage).path }}"
            }
        }
    }
}
