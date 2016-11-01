/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.cleanup

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Ensures that a directory is empty or writes the names of files in
 * the directory to a report file.
 */
class EmptyDirectoryCheck extends DefaultTask {
    @Input
    FileTree targetDir

    @OutputFile
    File report

    @Input
    boolean errorWhenNotEmpty

    @TaskAction
    def ensureEmptiness() {
        def hasFile = false
        targetDir.visit { visitDetails ->
            def f = visitDetails.getFile()
            if (f.isFile()) {
                hasFile = true
                report << f.path + "\n"
            }
        }
        if (hasFile && errorWhenNotEmpty) {
            throw new GradleException("The directory ${targetDir.asPath} was not empty.")
        }
    }
}
