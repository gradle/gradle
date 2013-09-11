/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests.tooling.r18

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.gradle.GradleBuild

@ToolingApiVersion(">=1.8")
class GradleBuildModelCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        file('settings.gradle') << '''
include 'a'
include 'b'
include 'b:c'
rootProject.name = 'test'
'''
        buildFile << """
allprojects {
    description = "project \$name"
    task buildStuff
}
"""
    }

    //TODO implement mapping of 1.0- versions
    @TargetGradleVersion(">=1.0-milestone-5")
    def "can request GradleBuild model"() {
        when:
        GradleBuild model = withConnection { connection -> connection.getModel(GradleBuild) }

        then:
        model.rootProject.name == 'test'
        model.rootProject.path == ':'
        model.rootProject.parent == null
        model.rootProject.projectDirectory == projectDir
        model.rootProject.children.size() == 2
        model.rootProject.children.every { it.parent == model.rootProject }
        model.projects*.name == ['test', 'a', 'b', 'c']
        model.projects*.path == [':', ':a', ':b', ':b:c']
        model.projects*.projectDirectory == [projectDir, file('a'),file('b'),file('b/c')]
    }
}
