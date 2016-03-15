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

package org.gradle.integtests.resolve

import com.google.common.collect.Maps
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.testfixtures.ProjectBuilder

class GradleApiJarIntegrationTest extends AbstractIntegrationSpec {

    def "cannot use classes from external dependencies provided by the Gradle API"() {
        when:
        requireGradleHome()
        buildFile << """
            plugins { id "groovy" }
            repositories { jcenter() }
            dependencies {
                compile localGroovy()
                compile fatGradleApi()
                compile "junit:junit:4.12"
            }
        """

        file("src/test/groovy/MyTest.groovy") << """
            class MyTest extends groovy.util.GroovyTestCase {

                void testImplIsHidden() {
                    try {
                        getClass().classLoader.loadClass("$Maps.name")
                        assert false : "expected $Maps.name not to be visible"
                    } catch (ClassNotFoundException ignore) {
                        // expected
                    }
                }

                void testCanUseProjectBuilder() {
                    ${ProjectBuilder.name}.builder().build().tasks.create("newTask")
                }
            }
        """

        then:
        succeeds "build"
    }
}