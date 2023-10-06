/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

@Requires(UnitTestPreconditions.Jdk9OrLater)
class EclipseJavaProjectModulesIntegrationTest extends AbstractEclipseIntegrationSpec {

    def "depend on modular project"() {
        setup:
        /*
        This is the multi-module project structure the integration test works with:
        -root
          -api
          -util
        */
        file("/api/src/main/java/module-info.java") << """
            module api {
                exports api
            }
        """

        file("/util/src/main/java/module-info.java") << """
            module util {
                requires api
            }
        """

        settingsFile << """
            rootProject.name = 'root'
            include 'api'
            include 'util'

        """

        buildFile << """
            allprojects {
                apply plugin: 'java'
                apply plugin: 'eclipse'
            }

            project(':util') {
                dependencies {
                    implementation project(':api')
                }
            }

        """

        when:
        succeeds "eclipse"

        then:
        def projects = classpath("util").projects
        assert projects.size() == 1
        projects.get(0).assertModularDependency()
    }
}
