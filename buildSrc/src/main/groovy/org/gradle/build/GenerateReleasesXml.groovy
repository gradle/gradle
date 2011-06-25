/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.build

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class GenerateReleasesXml extends DefaultTask {
    @Input
    String getVersion() { return project.version.versionNumber }

    @Input
    Date getBuildTime() { return project.version.buildTime }

    @OutputFile
    File destFile

    @InputFile
    File getSrcFile() { return project.releases.releasesFile }

    @TaskAction
    def void generate() {
        logger.info('Write release xml to: {}', destFile)
        project.releases.generateTo(destFile)
    }
}
