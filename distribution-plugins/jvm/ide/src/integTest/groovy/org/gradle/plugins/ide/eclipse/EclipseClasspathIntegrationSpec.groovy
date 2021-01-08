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
package org.gradle.plugins.ide.eclipse

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import spock.lang.Issue

class EclipseClasspathIntegrationSpec extends AbstractEclipseIntegrationSpec {

    // TODO this should be part of EclipseClasspathIntegrationTest, but it uses a legacy superclass

    @Issue("https://github.com/gradle/gradle/issues/10393")
    @ToBeFixedForConfigurationCache
    def  "Does not contain duplicate project dependencies"() {
        setup:
        buildFile <<  """
            allprojects {
                apply plugin: 'java'
                apply plugin: 'eclipse'
            }
        """
        settingsFile << """
            include 'producer', 'consumer'
        """

        file('producer/build.gradle').text = """

            task testJar(type: Jar) {
                archiveClassifier = "test"
            }

            configurations {
                testArtifacts
            }

            configurations.testArtifacts.outgoing.artifact(testJar)
        """

        file('consumer/build.gradle').text = """
            dependencies {
                testImplementation(project(":producer"))
                testImplementation(project(['path' : ":producer", 'configuration' : "testArtifacts"]))
            }
        """

        when:
        run 'eclipse'

        then:
        file('consumer/.classpath').text.count('<classpathentry kind="src" path="/producer"') == 1
    }
}
