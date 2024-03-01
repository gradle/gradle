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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class ProjectConfigurationIntegrationTest extends AbstractIntegrationSpec {

    def "accessing the task by path from containing project is safe"() {
        buildFile << """
            task foobar
            println "the name: " + tasks.getByPath(":foobar").name
        """

        when:
        run()

        then:
        output.contains "the name: foobar"
    }

    def "shows deprecation warning when calling Project#afterEvaluate(Closure) after the project was evaluated"() {
        buildFile << '''
            allprojects { p ->
                println "[1] Adding afterEvaluate for $p.name"
                p.afterEvaluate {
                    println "[1] afterEvaluate $p.name"
                }
            }

            project(':a') {
                println "[2] Adding evaluationDependsOn"
                evaluationDependsOn(':b')
            }

            allprojects { p ->
                println "[3] Adding afterEvaluate for $p.name"
                p.afterEvaluate {
                    println "[3] afterEvaluate $p.name"
                }
            }
        '''
        createDirs("a", "b")
        settingsFile << """
            rootProject.name = 'root'
            include 'a', 'b'
        """

        expect:
        def result = fails()
        result.assertHasDescription("A problem occurred evaluating root project 'root'.")
        failure.assertHasCause("Cannot run Project.afterEvaluate(Closure) when the project is already evaluated.")
    }

    def "shows deprecation warning when calling Project#afterEvaluate(Action) after the project was evaluated"() {
        buildFile '''
            allprojects { p ->
                println "[1] Adding afterEvaluate for $p.name"
                p.afterEvaluate new Action<Project>() {
                    void execute(Project proj) {
                        println "[1] afterEvaluate $proj.name"
                    }
                }
            }

            project(':a') {
                println "[2] Adding evaluationDependsOn"
                evaluationDependsOn(':b')
            }

            allprojects { p ->
                println "[3] Adding afterEvaluate for $p.name"
                p.afterEvaluate new Action<Project>() {
                    void execute(Project proj) {
                        println "[3] afterEvaluate $proj.name"
                    }
                }
            }
        '''
        createDirs("a", "b")
        settingsFile << """
            rootProject.name = 'root'
            include 'a', 'b'
        """

        expect:
        def result = fails()
        result.assertHasDescription("A problem occurred evaluating root project 'root'.")
        failure.assertHasCause("Cannot run Project.afterEvaluate(Action) when the project is already evaluated.")
    }

    @Issue("https://github.com/gradle/gradle/issues/4823")
    def "evaluationDependsOn deep project forces evaluation of parents"() {
        given:
        createDirs("a", "b", "b/c")
        settingsFile << "include(':a', ':b:c')"
        file("a/build.gradle") << "evaluationDependsOn(':b:c')"
        file("b/build.gradle") << "plugins { id('org.gradle.hello-world') version '0.2' apply false }"
        file("b/c/build.gradle") << "import org.gradle.plugin.HelloWorldTask"

        expect:
        succeeds 'help'
    }
}
