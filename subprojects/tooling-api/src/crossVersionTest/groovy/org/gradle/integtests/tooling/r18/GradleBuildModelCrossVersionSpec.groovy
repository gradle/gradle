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


import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.gradle.GradleBuild

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

    def "can request GradleBuild model including projectDirectory"() {
        when:
        GradleBuild model = withConnection { connection -> connection.getModel(GradleBuild) }

        then:
        validateModel(model)
        model.projects*.projectDirectory == [projectDir, file('a'), file('b'), file('b/c')]
    }

    def validateModel(GradleBuild model) {
        assert model.rootProject.name == 'test'
        assert model.rootProject.path == ':'
        assert model.rootProject.parent == null
        assert model.rootProject.children.size() == 2
        assert model.rootProject.children.every { it.parent == model.rootProject }
        assert model.projects*.name == ['test', 'a', 'b', 'c']
        assert model.projects*.path == [':', ':a', ':b', ':b:c']
        model
    }
}
