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
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.eclipse.EclipseOutputLocation
import org.gradle.tooling.model.eclipse.EclipseProject

@ToolingApiVersion('>=4.4')
@TargetGradleVersion(">=4.4")
class ToolingApiEclipseModelOutputLocationCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile << 'rootProject.name = "root"'
    }

    def "Non-Java project has default output location"() {
        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        EclipseOutputLocation output = project.getOutputLocation()

        then:
        output.path == 'bin/default'
    }

    def "Java project has default output location"() {
        setup:
        buildFile << "apply plugin: 'java'"
        EclipseProject project = loadToolingModel(EclipseProject)

        when:
        EclipseOutputLocation output = project.getOutputLocation()

        then:
        output.path == 'bin/default'
    }
}
