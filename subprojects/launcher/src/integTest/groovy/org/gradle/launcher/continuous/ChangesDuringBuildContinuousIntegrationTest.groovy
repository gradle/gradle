/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.continuous

import spock.lang.Unroll

// Continuous build will trigger a rebuild when an input file is changed during build execution
class ChangesDuringBuildContinuousIntegrationTest extends Java7RequiringContinuousIntegrationTest {
    def "should trigger rebuild when java source file is changed during build execution"() {
        given:
        file("src/main/java/Thing.java") << "class Thing {}"

        when:
        buildFile << """
apply plugin: 'java'
gradle.taskGraph.afterTask { Task task ->
    if(task.path == ':classes' && !file('changetrigged').exists()) {
       file("src/main/java/Thing.java").text = "class Thing { private static final boolean CHANGED=true; }"
       file('changetrigged').text = 'done'
    }
}
"""
        then:
        succeeds("build")

        when:
        sendEOT()

        then:
        cancelsAndExits()

        when:
        def classloader = new URLClassLoader([file("build/classes/main").toURI().toURL()] as URL[])

        then:
        assert classloader.loadClass('Thing').getDeclaredField("CHANGED") != null
    }

    def "new build should be triggered when input files to tasks are changed after each task has been executed, but before the build has completed"(changingInput) {
        given:
        ['a', 'b', 'c', 'd'].each { file(it).createDir() }

        when:
        buildFile << """
            task a {
              inputs.dir "a"
              doLast {}
            }
            task b {
              dependsOn "a"
              inputs.dir "b"
              doLast {}
            }
            task c {
              dependsOn "b"
              inputs.dir "c"
              doLast {}
            }
            task d {
              dependsOn "c"
              inputs.dir "d"
              doLast {}
            }

            gradle.taskGraph.afterTask { Task task ->
                if(task.path == ':$changingInput' && !file('changetrigged').exists()) {
                   file('$changingInput/input.txt').text = 'New input file'
                   file('changetrigged').text = 'done'
                }
            }
        """

        then:
        def result = succeeds("d")
        sendEOT()
        result.executedTasks == [':a', ':b', ':c', ':d', ':a', ':b', ':c', ':d']
        result.assertOutputContains('Change detected, executing build...')

        where:
        changingInput << ['a', 'b', 'c', 'd']
    }

    def "new build should be triggered when input files to tasks are changed during the task is executing"(changingInput) {
        given:
        ['a', 'b', 'c', 'd'].each { file(it).createDir() }

        when:
        buildFile << """
            def taskAction = { Task task ->
                if(task.path == ':$changingInput' && !file('changetrigged').exists()) {
                   file('$changingInput/input.txt').text = 'New input file'
                   file('changetrigged').text = 'done'
                }
            }

            task a {
              inputs.dir "a"
              doLast taskAction
            }
            task b {
              dependsOn "a"
              inputs.dir "b"
              doLast taskAction
            }
            task c {
              dependsOn "b"
              inputs.dir "c"
              doLast taskAction
            }
            task d {
              dependsOn "c"
              inputs.dir "d"
              doLast taskAction
            }
        """

        then:
        def result = succeeds("d")
        sendEOT()
        result.executedTasks == [':a', ':b', ':c', ':d', ':a', ':b', ':c', ':d']
        result.assertOutputContains('Change detected, executing build...')

        where:
        changingInput << ['a', 'b', 'c', 'd']
    }

    @Unroll
    def "check build executing and failing in task :c - change in :#changingInput"(changingInput, shouldTrigger) {
        given:
        ['a', 'b', 'c', 'd'].each { file(it).createDir() }

        when:
        buildFile << """
            task a {
              inputs.dir "a"
              doLast {}
            }
            task b {
              dependsOn "a"
              inputs.dir "b"
              doLast {}
            }
            task c {
              dependsOn "b"
              inputs.dir "c"
              doLast {
                throw new Exception("Failure in :c")
              }
            }
            task d {
              dependsOn "c"
              inputs.dir "d"
              doLast {}
            }
        """

        then:
        def result = fails("d")

        when:
        file("$changingInput/input.txt").text = 'input changed'

        then:
        if (shouldTrigger) {
            fails()
        } else {
            noBuildTriggered()
        }
        sendEOT()

        where:
        changingInput | shouldTrigger
        'a'           | true
        'b'           | true
        'c'           | true
        'd'           | false
    }
}
