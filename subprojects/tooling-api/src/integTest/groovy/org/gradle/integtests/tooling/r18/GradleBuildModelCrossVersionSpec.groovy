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
import org.gradle.tooling.model.GradleBuild
import org.gradle.tooling.model.GradleProject

@ToolingApiVersion(">=1.8")
class GradleBuildModelCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        file('settings.gradle') << '''
include 'a'
include 'b'
include 'b:c'
rootProject.name = 'test'
'''
        buildFile << '''
allprojects {
    description = 'some project'
    task buildStuff
}
'''
    }

    @TargetGradleVersion(">=1.8")
    def "can request models from various projects"() {
        when:
        Map<String, GradleProject> result = withConnection { connection -> connection.action(new MultiProjectAction()).run() }

        then:
        result != null
        // TODO:ADAM - switch this on
//        result.keySet() == [':', ':a', ':b', ':b:c'] as Set
//        result.values().each {
//            assert it.description == 'some project'
//            assert it.tasks.any {it.name == 'buildStuff' }
//        }
    }

    // TODO:ADAM - make this work for all target versions
    @TargetGradleVersion(">=1.8")
    def "can request GradleBuild model"() {
        when:
        GradleBuild model = withConnection { connection -> connection.getModel(GradleBuild) }

        then:
        model != null
        // TODO:ADAM - switch this on
//        model.projects*.name as Set == ['test', 'a', 'b', 'c'] as Set
    }
}
