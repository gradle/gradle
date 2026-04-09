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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForIsolatedProjects
import org.gradle.integtests.fixtures.build.BuildTestFile
import spock.lang.Issue

class ExtraPropertiesIntegrationTest extends AbstractIntegrationSpec {

    @ToBeFixedForIsolatedProjects(because = "Cross-project configuration")
    def 'extra properties are inherited to child and grandchild projects'() {
        given:
        extraPropertiesMultiBuild()

        when:
        executer.expectDocumentedDeprecationWarning("Accessing a property from a parent project has been deprecated. This will fail with an error in Gradle 10. Property 'testProp' was not found in project ':a' and was dynamically resolved from root project 'extra-properties'. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_accessing_parent_project_properties")
        executer.expectDocumentedDeprecationWarning("Accessing a property from a parent project has been deprecated. This will fail with an error in Gradle 10. Property 'testProp' was not found in project ':b' and was dynamically resolved from root project 'extra-properties'. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_accessing_parent_project_properties")
        executer.expectDocumentedDeprecationWarning("Accessing a property from a parent project has been deprecated. This will fail with an error in Gradle 10. Property 'testProp' was not found in project ':a:a1' and was dynamically resolved from project ':a'. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_accessing_parent_project_properties")

        then:
        succeeds checkTestPropTasks()
    }

    @Issue('GRADLE-3530')
    @ToBeFixedForIsolatedProjects(because = "Cross-project configuration")
    def 'extra properties can be overridden on child projects'() {
        given:
        extraPropertiesMultiBuild('a': 'aValue', 'a:a1': 'aValue') {
            buildFile << """
                project(':a') {
                    ext.testProp = 'aValue'
                }
            """.stripIndent()
        }

        when:
        executer.expectDocumentedDeprecationWarning("Accessing a property from a parent project has been deprecated. This will fail with an error in Gradle 10. Property 'testProp' was not found in project ':b' and was dynamically resolved from root project 'extra-properties'. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_accessing_parent_project_properties")
        executer.expectDocumentedDeprecationWarning("Accessing a property from a parent project has been deprecated. This will fail with an error in Gradle 10. Property 'testProp' was not found in project ':a:a1' and was dynamically resolved from project ':a'. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_accessing_parent_project_properties")

        then:
        succeeds checkTestPropTasks()
    }

    BuildTestFile extraPropertiesMultiBuild(Map expectedPropPerProject = [:], @DelegatesTo(BuildTestFile) Closure configuration = {}) {
        expectedPropPerProject = [a: 'rootValue', b: 'rootValue', 'a:a1': 'rootValue'] + expectedPropPerProject
        def root = multiProjectBuild('extra-properties', ['a', 'b']) {
            createDirs("a", "a/a1")
            settingsFile << "include ':a:a1'"

            buildFile """
                ext.testProp = 'rootValue'

                task checkTestProp {
                    def testPropValue = provider { testProp }
                    doLast {
                        assert testPropValue.get() == 'rootValue'
                    }
                }
            """

            ['a', 'b', 'a:a1'].each {
                buildFile """
                    project(':${it}') {
                        task checkTestProp {
                            def testPropValue = provider { testProp }
                            doLast {
                                assert testPropValue.get() == '${expectedPropPerProject[it]}'
                            }
                        }
                    }
            """
            }
        }
        root.with(configuration)
        root
    }

    String[] checkTestPropTasks() {
        ['', ':a', ':b', ':a:a1'].collect { "${it}:checkTestProp".toString() }
    }
}
