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

package org.gradle.integtests.tooling.r42

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.util.Requires

import static org.gradle.util.TestPrecondition.KOTLIN_SCRIPT
import static org.gradle.util.TestPrecondition.NOT_WINDOWS

@ToolingApiVersion(">=2.5")
@TargetGradleVersion(">=4.2")
class BuildProgressTaskActionsCrossVersionSpec extends ToolingApiSpecification {

    ProgressEvents events

    def setup() {
        events = ProgressEvents.create()
        settingsFile << "rootProject.name = 'root'"
    }

    //This is not a task action, but it is logged on the same level as the actions
    def "clean stale output action has an informative name"() {
        given:
        buildFile << "task custom { doLast {} }"

        when:
        runCustomTask()

        then:
        def op = events.operation('Clean stale outputs')
        op.parent.children.size() == 2
    }

    //This is the current behavior. Snapshoting might become not-a-task-action in the future.
    def "snapshot task inputs action has an informative name"() {
        given:
        buildFile << "task custom { doLast {} }"
        file("gradle.properties") << "org.gradle.caching=true"

        when:
        runCustomTask()

        then:
        def op = events.operation('Snapshot task inputs for :custom')
        op.parent.children.size() == 3
    }

    //This is the current behavior. Validating input might become not-a-task-action in the future.
    def "validate task inputs action has an informative name"() {
        given:
        buildFile << """
        task custom(type: CustomTask) {
            customInString1 = "1"
            customInString2 = "2"
            customOut = file("\$buildDir/out")
        }
        
        class CustomTask extends DefaultTask {
            @OutputDirectory File customOut
            @Input String customInString1
            @Input String customInString2
            @TaskAction void doSomthing() { }
        }    
        """

        when:
        runCustomTask()

        then:
        def op = events.operation('Validate task inputs for :custom')
        op.parent.children.size() == 4
    }

    //This is the current behavior. The behavior of creating each output directory in a separate action might change in the future.
    def "create output directories actions have informative names"() {
        given:
        buildFile << """
        task custom(type: CustomTask) {
            customInString = ""
            customOut1 = file("\$buildDir/out1")
            customOut2 = file("\$buildDir/out2")
        }
        
        class CustomTask extends DefaultTask {
            @OutputDirectory File customOut1
            @OutputDirectory File customOut2
            @Input String customInString
            @TaskAction void doSomething() { }
        }    
        """

        when:
        runCustomTask()

        then:
        def op1 = events.operation('Create customOut1 output directory for :custom')
        def op2 = events.operation('Create customOut2 output directory for :custom')
        op1.parent == op2.parent
        op1.parent.children.size() == 5
    }

    def "task actions implemented in annotated methods are named after the method"() {
        given:
        buildFile << """
        task custom(type: CustomTask)
        
        class CustomTask extends DefaultTask {
            @TaskAction void doSomethingAmazing() { }
        }    
        """

        when:
        runCustomTask()

        then:
        def op = events.operation('Execute doSomethingAmazing for :custom')
        op.parent.children.size() == 2
    }

    def "task actions defined in doFirst and doLast blocks of Groovy build scripts have informative names"() {
        given:
        buildFile << """
            task custom { 
                doFirst {}
                doLast {}
            }
        """

        when:
        runCustomTask()

        then:
        def op1 = events.operation('Execute doFirst {} action for :custom')
        def op2 = events.operation('Execute doLast {} action for :custom')
        op1.parent == op2.parent
        op1.parent.children.size() == 3
    }

    @Requires([KOTLIN_SCRIPT, NOT_WINDOWS])
    def "task actions defined in doFirst and doLast blocks of Kotlin build scripts have informative names"() {
        given:
        buildFileKts << """
            tasks { "custom" { 
                doFirst {}
                doLast {}
            }}
        """

        when:
        runCustomTask()

        then:
        def op1 = events.operation('Execute doFirst {} action for :custom')
        def op2 = events.operation('Execute doLast {} action for :custom')
        op1.parent == op2.parent
        op1.parent.children.size() == 3
    }

    def "task actions defined in doFirst and doLast blocks of Groovy build scripts can be named"() {
        given:
        buildFile << """
            task custom { 
                doFirst("A first step") {}
                doLast("One last thing...") {}
            }
        """

        when:
        runCustomTask()

        then:
        def op1 = events.operation('Execute A first step for :custom')
        def op2 = events.operation('Execute One last thing... for :custom')
        op1.parent == op2.parent
        op1.parent.children.size() == 3
    }

    @Requires([KOTLIN_SCRIPT, NOT_WINDOWS])
    def "task actions defined in doFirst and doLast blocks of Kotlin build scripts can be named"() {
        given:
        buildFileKts << """
            tasks { "custom" { 
                doFirst("A first step") {}
                doLast("One last thing...") {}
            }}
        """

        when:
        runCustomTask()

        then:
        def op1 = events.operation('Execute A first step for :custom')
        def op2 = events.operation('Execute One last thing... for :custom')
        op1.parent == op2.parent
        op1.parent.children.size() == 3
    }

    private runCustomTask() {
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().
                    forTasks('custom').addProgressListener(events).run()
        }
    }
}
