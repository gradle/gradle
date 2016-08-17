/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testing.performance.generator.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory

abstract class ProjectGeneratorTask extends DefaultTask {
    @OutputDirectory
    File destDir

    @InputDirectory
    File templateDirectory

    @InputDirectory
    @Optional
    File sharedTemplateDirectory

    ProjectGeneratorTask() {
        destDir = project.file("${project.buildDir}/${name}")
        templateDirectory = project.file("src/templates")
    }

    protected File resolveTemplate(String templateName) {
        File templateFile = new File(templateDirectory, templateName)
        if (!templateFile.exists()) {
            if (sharedTemplateDirectory?.exists()) {
                templateFile = new File(sharedTemplateDirectory, templateName)
            }
        }
        templateFile
    }
}
