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

import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.archives.TestReproducibleArchives
import org.gradle.internal.os.OperatingSystem

@TestReproducibleArchives
class ChangesDuringBuildContinuousIntegrationTest extends AbstractContinuousIntegrationTest {

    def setup() {
        def quietPeriod = OperatingSystem.current().isMacOsX() ? 2000 : 250
        waitAtEndOfBuildForQuietPeriod(quietPeriod)
    }

    def "should trigger rebuild when java source file is changed during build execution"() {
        given:
        def inputFile = file("src/main/java/Thing.java")
        inputFile << "class Thing {}"
        inputFile.makeOlder()

        when:
        buildFile """
            apply plugin: 'java'

            task postCompile {
                def changeTriggered = file('change-triggered')
                def thingJava = file("src/main/java/Thing.java")
                doLast {
                    if (!changeTriggered.exists()) {
                        println "Modifying 'Thing.java' after initial compile task"
                        thingJava.text = "class Thing { private static final boolean CHANGED=true; }"
                        changeTriggered.text = 'done'
                    }
                }
                dependsOn 'classes'
            }
            jar.dependsOn postCompile
        """
        then:
        succeeds("build")

        when:
        int count = 5
        while (count >= 0 && !output.contains("modified: ")) {
            println "Waiting for 'Thing.java' modification detection ($count)..."
            Thread.sleep(100)
            count--
        }
        sendEOT()

        then:
        cancelsAndExits()

        when:
        def classloader = new URLClassLoader([javaClassFile("").toURI().toURL()] as URL[])

        then:
        assert classloader.loadClass('Thing').getDeclaredFields()*.name == ["CHANGED"]
    }

    @UnsupportedWithConfigurationCache(because = "taskGraph.afterTask")
    def "new build should be triggered when input files to tasks are changed after each task has been executed, but before the build has completed (changed: #changingInput)"() {
        given:
        ['a', 'b', 'c', 'd'].each { file(it).createDir() }

        when:
        buildFile << """
            task a {
              inputs.dir "a"
              outputs.file "build/a"
              doLast {}
            }
            task b {
              dependsOn "a"
              inputs.dir "b"
              outputs.file "build/b"
              doLast {}
            }
            task c {
              dependsOn "b"
              inputs.dir "c"
              outputs.file "build/c"
              doLast {}
            }
            task d {
              dependsOn "c"
              inputs.dir "d"
              outputs.file "build/d"
              doLast {}
            }

            gradle.taskGraph.afterTask { Task task ->
                if(task.path == ':$changingInput' && !file('change-triggered').exists()) {
                   file('$changingInput/input.txt').text = 'New input file'
                   file('change-triggered').text = 'done'
                }
            }
        """

        then:
        succeeds("d")
        sendEOT()
        results.size() == 2
        results.each {
            assert it.assertTasksExecuted(':a', ':b', ':c', ':d')
        }

        where:
        changingInput << ['a', 'b', 'c', 'd']
    }

    def "new build should be triggered when input files to tasks are changed during the task is executing (changed: #changingInput)"() {
        given:
        ['a', 'b', 'c', 'd'].each { file(it).createDir() }

        when:
        buildFile << """
            def changeTriggerFile = file('change-triggered')
            def inputFile = file('$changingInput/input.txt')
            def taskAction = { Task task ->
                if (task.path == ':$changingInput' && !changeTriggerFile.exists()) {
                   inputFile.text = 'New input file'
                   changeTriggerFile.text = 'done'
                }
            }

            task a {
              inputs.dir "a"
              outputs.file "build/a"
              doLast taskAction
            }
            task b {
              dependsOn "a"
              inputs.dir "b"
              outputs.file "build/b"
              doLast taskAction
            }
            task c {
              dependsOn "b"
              inputs.dir "c"
              outputs.file "build/c"
              doLast taskAction
            }
            task d {
              dependsOn "c"
              inputs.dir "d"
              outputs.file "build/d"
              doLast taskAction
            }
        """

        then:
        succeeds("d")
        sendEOT()
        results.size() == 2
        results.each {
            assert it.assertTasksExecuted(':a', ':b', ':c', ':d')
        }

        where:
        changingInput << ['a', 'b', 'c', 'd']
    }

    def "#description for change in #changingInput and failure in task :c"() {
        given:
        ['a', 'b', 'c', 'd'].each { file(it).createDir() }

        when:
        buildFile << """
            task a {
              inputs.dir "a"
              outputs.file "build/a"
              doLast {}
            }
            task b {
              dependsOn "a"
              inputs.dir "b"
              outputs.file "build/b"
              doLast {}
            }
            task c {
              dependsOn "b"
              inputs.dir "c"
              outputs.file "build/c"
              doLast {
                throw new Exception("Failure in :c")
              }
            }
            task d {
              dependsOn "c"
              inputs.dir "d"
              outputs.file "build/d"
              doLast {}
            }
        """

        then:
        def result = fails("d")

        when:
        file("$changingInput/input.txt").text = 'input changed'

        then:
        if (shouldTrigger) {
            buildTriggeredAndFailed()
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
        description = shouldTrigger ? "build should be triggered" : "build should not be triggered"
    }
}
