/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.EnableModelDsl
import org.gradle.test.fixtures.archive.JarTestFixture

public class MixedLegacyAndComponentJvmPluginIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        EnableModelDsl.enable(executer)
    }

    def "can combine legacy java and jvm-component plugins in a single project"() {
        settingsFile << "rootProject.name = 'test'"
        buildFile << '''
            apply plugin: "java"
            apply plugin: "jvm-component"
            apply plugin: "java-lang"

            model {
                components {
                    jvmLib(JvmLibrarySpec)
                }
                tasks {
                    create("checkModel") {
                        def components = $("components")
                        doLast {
                            assert components.size() == 1
                            assert components.jvmLib instanceof JvmLibrarySpec

                            assert project.binaries.size() == 3
                            assert project.binaries.jvmLibJar instanceof JarBinarySpec
                            assert project.binaries.mainClasses instanceof ClassDirectoryBinarySpec
                            assert project.binaries.testClasses instanceof ClassDirectoryBinarySpec
                        }
                    }
                }
            }
'''
        expect:
        succeeds "checkModel"
    }

    def "can build legacy java and jvm-component plugins in a single project"() {
        given:
        file("src/main/java/org/gradle/test/Legacy.java") << """
    package org.gradle.test;
    interface Legacy {}
"""
        file("src/main/resources/legacy.txt") << "Resource for the legacy component"

        and:
        file("src/jvmLib/java/org/gradle/test/Component.java") << """
    package org.gradle.test;
    interface Component {}
"""
        file("src/jvmLib/resources/component.txt") << "Resource for the jvm component"

        when:
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            apply plugin: "java"
            apply plugin: "jvm-component"
            apply plugin: "java-lang"

            model {
                components {
                    jvmLib(JvmLibrarySpec)
                }
            }
"""

        and:
        succeeds "assemble"

        then:
        executed ':compileJava', ':processResources', ':classes', ':jar',
                ':compileJvmLibJarJvmLibJava', ':processJvmLibJarJvmLibResources', ':createJvmLibJar', ':jvmLibJar'

        and:
        new JarTestFixture(file("build/jars/jvmLibJar/jvmLib.jar")).hasDescendants("org/gradle/test/Component.class", "component.txt");
        new JarTestFixture(file("build/libs/test.jar")).hasDescendants("org/gradle/test/Legacy.class", "legacy.txt");
    }
}
