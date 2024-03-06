/*
 * Copyright 2011 the original author or authors.
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

class MixedJavaAndWebProjectIntegrationTest extends AbstractIntegrationSpec {
    def "project can use classes from WAR project"() {
        given:
        createDirs("a", "b")
        file("settings.gradle") << 'include "a", "b"'

        and:
        buildFile << """
            project(":a") {
                apply plugin: 'war'
            }
            project(":b") {
                apply plugin: 'java'
                dependencies {
                    implementation project(":a")
                }
                compileJava.doFirst {
                    assert classpath.collect { it.name } == ['a.jar']
                }
            }
        """

        and:
        file("a/src/main/java/org/gradle/test/Person.java") << """
            package org.gradle.test;
            interface Person { }
        """

        and:
        file("b/src/main/java/org/gradle/test/PersonImpl.java") << """
            package org.gradle.test;
            class PersonImpl implements Person { }
        """

        expect:
        succeeds "assemble"
    }

    def "war contains runtime classpath of upstream java project"() {
        given:
        createDirs("a", "b", "c", "d", "e")
        file("settings.gradle") << 'include "a", "b", "c", "d", "e"'

        and:
        buildFile << """
            project(":a") {
                apply plugin: 'war'
                dependencies {
                    implementation project(":b")
                }
                war.doFirst {
                    assert classpath.collect { it.name }.containsAll(['b.jar', 'c.jar', 'd.jar'])
                    assert !classpath.collect { it.name }.contains('e.jar')
                }
            }
            project(":b") {
                apply plugin: 'java'
                dependencies {
                    implementation project(':c')
                    compileOnly project(':e')
                }
            }
            project(":c") {
                apply plugin: 'java-library'
                dependencies {
                    implementation project(':d')
                }
            }
            project(":d") {
                apply plugin: 'java'
            }
            project(":e") {
                apply plugin: 'java'
            }
        """

        expect:
        succeeds "assemble"
    }

    def "war contains runtime classpath of upstream java-library project"() {
        given:
        createDirs("a", "b", "c", "d", "e")
        file("settings.gradle") << 'include "a", "b", "c", "d", "e"'

        and:
        buildFile << """
            project(":a") {
                apply plugin: 'war'
                dependencies {
                    implementation project(":b")
                }
                war.doFirst {
                    assert classpath.collect { it.name }.containsAll(['b.jar', 'c.jar', 'd.jar'])
                    assert !classpath.collect { it.name }.contains('e.jar')
                }
            }
            project(":b") {
                apply plugin: 'java-library'
                dependencies {
                    api project(':c')
                    compileOnly project(':e')
                }
            }
            project(":c") {
                apply plugin: 'java'
                dependencies {
                    implementation project(':d')
                }
            }
            project(":d") {
                apply plugin: 'java-library'
            }
            project(":e") {
                apply plugin: 'java-library'
            }
        """

        expect:
        succeeds "assemble"
    }
}
