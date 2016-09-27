/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Issue

class TaskEnumTypesInputPropertyIntegrationTest extends AbstractIntegrationSpec {
    def setup(){
        buildFile << """
task someTask {
    inputs.property "someEnum", SomeEnum.E1
    def f = new File("build/e1")
    outputs.dir f
    doLast {
        f.mkdirs()
    }
}

enum SomeEnum {
    E1, E2
}
"""

    }

    @Issue("GRADLE-3018")
    @IgnoreIf({!GradleContextualExecuter.embedded}) // broken across process boundaries
    def "cached task state handles enum input properties"(){
        given:
        run "someTask"

        when:
        run "someTask"

        then:
        skippedTasks.contains(":someTask")
    }

    @Issue("GRADLE-3018")
    @Ignore
    def "cached task state handles enum input properties for changed runtimeclasspath"(){
        given:
        run "someTask"

        when:
        buildFile << """
task someOtherTask
"""
        and:
        run "someTask"

        then:
        skippedTasks.contains(":someTask")
    }
}
