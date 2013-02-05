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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

/**
 * by Szczepan Faber, created at: 2/5/13
 */
@Issue("GRADLE-2477")
class NestedConfigurationResolutionIntegrationTest extends AbstractIntegrationSpec {

    def "resolving project dependency at configuration time causes target project configuration and consequently classpath resolution"() {
        settingsFile << "include 'impl'"
        buildFile << """
            allprojects { apply plugin: 'java' }
            dependencies { compile project(":impl") }

            //resolve at configuration time:
            configurations.compile.files
        """

        when:
        run()

        then:
        noExceptionThrown()
    }

    def "resolving one configuration may incur resolution of different configuration"() {
        buildFile << """
            configurations {
                dirs
                compile
            }
            repositories {
                flatDir {
                    //resolving of 'compile' will result in resolving 'dirs'
                    dirs configurations.dirs, "."
                }
            }
            dependencies {
                compile files(".")
            }
            task resolveDeps << { configurations.compile.files }
        """

        when:
        run("resolveDeps")

        then:
        noExceptionThrown()
    }
}