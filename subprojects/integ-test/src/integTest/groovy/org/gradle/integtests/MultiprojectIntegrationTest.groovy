/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.ToBeImplemented
import spock.lang.Issue

class MultiprojectIntegrationTest extends AbstractIntegrationSpec {

    def "can inject configuration from parent project"() {
        given:
        settingsFile << 'include "a", "b"'
        buildFile << '''
            allprojects {
                def destDir = buildDir
                task test {
                    doLast {
                        destDir.mkdirs()
                        new File(destDir, 'test.txt') << 'content'
                    }
                }
                gradle.taskGraph.whenReady {
                    destDir.mkdirs()
                    new File(destDir, 'whenReady.txt') << 'content'
                }
                afterEvaluate {
                    destDir.mkdirs()
                    new File(destDir, 'afterEvaluate.txt') << 'content'
                }
            }
        '''

        when:
        run("test")

        then:
        file("build").assertHasDescendants('test.txt', 'whenReady.txt', 'afterEvaluate.txt')
        file('a/build').assertHasDescendants('test.txt', 'whenReady.txt', 'afterEvaluate.txt')
        file('b/build').assertHasDescendants('test.txt', 'whenReady.txt', 'afterEvaluate.txt')
    }

    @Issue("gradle/gradle#4799")
    @ToBeImplemented("This should succeed")
    def "evaluationDependsOn nephew whose name is AFTER mine in the alphabet"() {
        given:
        settingsFile << 'include ":a", ":b:c"'
        file("a/build.gradle") << "evaluationDependsOn(':b:c')"

        when:
        fails("help")

        then:
        failureHasCause("Attempt to define scope class loader before scope is locked")
    }
}
