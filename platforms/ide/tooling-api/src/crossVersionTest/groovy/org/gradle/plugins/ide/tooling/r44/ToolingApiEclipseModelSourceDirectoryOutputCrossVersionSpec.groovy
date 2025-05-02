/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r44

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.eclipse.EclipseProject

@TargetGradleVersion(">=4.4")
class ToolingApiEclipseModelSourceDirectoryOutputCrossVersionSpec extends ToolingApiSpecification {

    def "Source directories have default output"() {
        setup:
        settingsFile << 'rootProject.name = "root"'
        buildFile << "apply plugin: 'java'"
        file('src/main/java').mkdirs()

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        project.sourceDirectories.size() == 1
        project.sourceDirectories[0].output == 'bin/main'
    }

    def "Source directory has custom output"() {
        setup:
        settingsFile << 'rootProject.name = "root"'
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'eclipse'

            eclipse.classpath.file.whenMerged {
                entries.find { entry -> entry.path == 'src/test/java' }.output = null
                entries.find { entry -> entry.path == 'src/test/resources' }.output = 'out/test-resources'
            }
        """

        file('src/test/java').mkdirs()
        file('src/test/resources').mkdirs()

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        project.sourceDirectories.size() == 2
        project.sourceDirectories[0].output == null
        project.sourceDirectories[1].output == 'out/test-resources'
    }

}
