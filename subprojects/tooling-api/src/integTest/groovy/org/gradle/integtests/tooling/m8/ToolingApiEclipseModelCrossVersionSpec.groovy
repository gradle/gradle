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
package org.gradle.integtests.tooling.m8

import org.gradle.integtests.tooling.fixture.MinTargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.eclipse.EclipseProject

@ToolingApiVersion('>=1.0-milestone-3')
@MinTargetGradleVersion('1.0-milestone-8')
class ToolingApiEclipseModelCrossVersionSpec extends ToolingApiSpecification {
    def "can customise model late in the configuration phase"() {
        projectDir.file('build.gradle').text = """
apply plugin: 'java'

gradle.projectsEvaluated {
    repositories { mavenCentral() }
}
dependencies {
    compile 'commons-lang:commons-lang:2.5'
}
"""

        when:
        EclipseProject project = withConnection { it.getModel(EclipseProject.class) }

        then:
        project.classpath[0].file.name == 'commons-lang-2.5.jar'
    }
}
