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
package org.gradle.plugins.ide.tooling.m8

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.tooling.model.eclipse.EclipseProject

class ToolingApiEclipseModelCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {
    def "can customize model late in the configuration phase"() {
        projectDir.file('build.gradle').text = """
apply plugin: 'java'

gradle.projectsEvaluated {
    ${mavenCentralRepository()}
}
dependencies {
    ${implementationConfiguration} 'commons-lang:commons-lang:2.5'
}
"""

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        project.classpath[0].file.name == 'commons-lang-2.5.jar'
    }
}
