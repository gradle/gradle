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

package gradlebuild.performance.generator.tasks

import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
abstract class TemplateProjectGeneratorTask extends ProjectGeneratorTask {

    @OutputDirectory
    File destDir

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    File templateDirectory

    @InputDirectory
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    File sharedTemplateDirectory

    TemplateProjectGeneratorTask() {
        destDir = project.layout.buildDirectory.dir("$name").get().asFile
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
