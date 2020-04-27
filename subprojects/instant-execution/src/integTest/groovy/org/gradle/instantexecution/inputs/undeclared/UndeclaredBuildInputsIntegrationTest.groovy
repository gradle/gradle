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

package org.gradle.instantexecution.inputs.undeclared


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.instantexecution.AbstractInstantExecutionIntegrationTest

class UndeclaredBuildInputsIntegrationTest extends AbstractInstantExecutionIntegrationTest {
    def "reports build logic reading a system property via the Java API"() {
        buildFile << """
            // not declared
            System.getProperty("CI")
        """

        when:
        instantFails()

        then:
        // TODO - use problems fixture, however build script class is generated
        failure.assertThatDescription(containsNormalizedString("- unknown location: read system property 'CI' from 'build_"))
        failure.assertHasFileName("Build file '${buildFile.absolutePath}'")
        failure.assertHasLineNumber(3)
        failure.assertThatCause(containsNormalizedString("Read system property 'CI' from 'build_"))
    }

    def "plugin can use standard properties without declaring access"() {
        file("buildSrc/src/main/java/SneakyPlugin.java") << """
            import ${Project.name};
            import ${Plugin.name};

            public class SneakyPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    System.out.println("$prop = " + System.getProperty("$prop"));
                }
            }
        """
        buildFile << """
            apply plugin: SneakyPlugin
        """
        def fixture = newInstantExecutionFixture()

        when:
        instantRun()

        then:
        outputContains("$prop = ")

        when:
        instantRun()

        then:
        fixture.assertStateLoaded()
        noExceptionThrown()

        where:
        prop << [
            "os.name",
            "os.version",
            "java.version",
            "java.vm.version",
            "java.specification.version",
            "line.separator",
            "user.name",
            "user.home"
        ]
    }
}
