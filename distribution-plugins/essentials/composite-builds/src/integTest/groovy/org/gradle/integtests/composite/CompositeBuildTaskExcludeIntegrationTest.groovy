/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CompositeBuildTaskExcludeIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << """
            rootProject.name = 'root'
            include('sub')
            includeBuild('included')
        """
        file('included/settings.gradle') << """
            include('sub')
        """
        buildFile << """
            def test = tasks.register('test') {
                doLast {
                     println 'test executed'
                }
            }

            tasks.register('build') {
                doLast {
                     println 'build executed'
                }
                dependsOn test
                dependsOn gradle.includedBuild('included').task(':build')
            }

            project(':sub') {
                def subTest = tasks.register('test') {
                    doLast {
                         println 'sub test executed'
                    }
                }

                tasks.register('build') {
                    doLast {
                        println 'sub build executed'
                    }
                    dependsOn subTest
                    dependsOn gradle.includedBuild('included').task(':sub:build')
                }
            }
        """
        file('included/build.gradle') << """
            def test = tasks.register('test') {
                doLast {
                     println 'included test executed'
                }
            }

            tasks.register('build') {
                doLast {
                     println 'included build executed'
                }
                dependsOn test
            }

            project(':sub') {
                def subTest = tasks.register('test') {
                    doLast {
                         println 'included sub test executed'
                    }
                }
                tasks.register('build') {
                    doLast {
                         println 'included sub build executed'
                    }
                    dependsOn subTest
                }
            }
        """
    }

    def "can exclude tasks from an included build"() {
        when:
        succeeds("build", "-x", ":included:sub:test")

        then:
        result.assertTasksExecuted(":test", ":build", ":sub:test", ":sub:build", ":included:test", ":included:build" , ":included:sub:build")
        result.assertTaskNotExecuted(":included:sub:test")
    }

    def "excluding a task from a root project does not affect included task with same path"() {
        when:
        succeeds("build", "-x", ":sub:test")

        then:
        result.assertTasksExecuted(":test", ":build", ":sub:build", ":included:test", ":included:build", ":included:sub:test", ":included:sub:build")
        result.assertTaskNotExecuted(":sub:test")
    }

    def "cannot use unqualified task paths to exclude tasks from included build roots"() {
        when:
        run("build", "-x", "test")

        then:
        result.assertTasksExecuted(":build", ":sub:build", ":included:build", ":included:sub:build", ":included:test", ":included:sub:test")
        result.assertTaskNotExecuted(":test")
        result.assertTaskNotExecuted(":sub:test")
    }

    def "cannot use unqualified absolute paths to to exclude task from included build root"() {
        expect:
        runAndFail("build", "-x", "included:test")
    }

    def "cannot use unqualified task paths to exclude tasks from included build subproject"() {
        when:
        run("build", "-x", "sub:test")

        then:
        result.assertTasksExecuted(":test", ":build", ":sub:build", ":included:test", ":included:build", ":included:sub:test", ":included:sub:build")
        result.assertTaskNotExecuted(":sub:test")
    }
}
