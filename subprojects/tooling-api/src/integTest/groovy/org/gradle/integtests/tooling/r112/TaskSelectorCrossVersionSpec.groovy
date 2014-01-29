/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.tooling.r112

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.UnsupportedMethodException

@ToolingApiVersion(">=1.12")
class TaskSelectorCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        file('settings.gradle') << '''
include 'a'
include 'b'
include 'b:c'
rootProject.name = 'test'
'''
        file('b').mkdirs()
        file('b').file('build.gradle').text = '''
task t1 << {
    println "t1 in $project.name"
}
'''
    }

    @TargetGradleVersion(">=1.8 <=1.11")
    def "no task selectors when running action in older container"() {
        when:
        withConnection { connection -> connection.action(new FetchTaskSelectorsBuildAction()).run() }

        then:
        Exception e = thrown()
        e.cause.message.startsWith('Unsupported method: GradleProject.getTaskSelectors().')
    }

    @TargetGradleVersion(">=1.12")
    def "can request task selectors in action"() {
        when:
        Map<String, Set<String>> result = withConnection { connection ->
            connection.action(new FetchTaskSelectorsBuildAction()).run() }

        then:
        result != null
        result.keySet() == ['test', 'a', 'b', 'c'] as Set
        result['test'].contains('t1')
        result['b'].contains('t1')
        !result['c'].contains('t1')
    }

    def "can request task selectors from obtained GradleProject model"() {
        when:
        GradleProject result = withConnection { it.getModel(GradleProject.class) }

        then:
        result.path == ':'
        result.getTaskSelectors().find { it.name == 't1' } != null
        result.findByPath(':b').getTaskSelectors().find { it.name == 't1' } != null
        result.findByPath(':b:c').getTaskSelectors().find { it.name == 't1' } == null
    }
}
