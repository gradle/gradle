/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.java.fixtures

class JavaLibraryTestFixturesIntegrationTest extends AbstractJavaProjectTestFixturesIntegrationTest {
    @Override
    String getPluginName() {
        'java-library'
    }

    @Override
    List getSkippedJars(boolean compileClasspathPackaging) {
        compileClasspathPackaging ? [] : [':jar', ':testFixturesJar']
    }

    def "can consume test fixtures of subproject written in Groovy"() {
        settingsFile << """
            include 'sub'
        """
        file("sub/build.gradle") << """
            apply plugin: 'java-test-fixtures'
            apply plugin: 'groovy'

            dependencies {
               api(localGroovy())
               testFixturesApi(localGroovy())
            }
        """
        buildFile << """
            dependencies {
                testImplementation(testFixtures(project(":sub")))
            }
        """
        addPersonDomainClass("sub", "groovy")
        addPersonTestFixture("sub", "groovy")
        // the test will live in the current project, instead of "sub"
        // which demonstrates that the test fixtures are exposed
        addPersonTestUsingTestFixtures()

        when:
        succeeds ':compileTestJava'

        then:
        executedAndNotSkipped(
            ":sub:compileTestFixturesGroovy"
        )
    }
}
