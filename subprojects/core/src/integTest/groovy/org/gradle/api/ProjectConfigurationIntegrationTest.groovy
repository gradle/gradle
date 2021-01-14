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
        settingsFile << "include 'a', 'b'"

        expect:
        executer.expectDocumentedDeprecationWarning("Using method Project.afterEvaluate(Closure) when the project is already evaluated has been deprecated. " +
            "This will fail with an error in Gradle 7.0. " +
            "The configuration given is ignored because the project has already been evaluated. To apply this configuration, remove afterEvaluate. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_5.html#calling_project_afterevaluate_on_an_evaluated_project_has_been_deprecated")
        succeeds()
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
        settingsFile << "include 'a', 'b'"

        expect:
        executer.expectDocumentedDeprecationWarning("Using method Project.afterEvaluate(Action) when the project is already evaluated has been deprecated. " +
            "This will fail with an error in Gradle 7.0. " +
            "The configuration given is ignored because the project has already been evaluated. To apply this configuration, remove afterEvaluate. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_5.html#calling_project_afterevaluate_on_an_evaluated_project_has_been_deprecated")
        succeeds()
    }
}
