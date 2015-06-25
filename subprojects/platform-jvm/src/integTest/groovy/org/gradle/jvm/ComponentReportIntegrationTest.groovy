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
package org.gradle.jvm

import org.gradle.api.JavaVersion
import org.gradle.api.reporting.components.AbstractComponentReportIntegrationTest
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class ComponentReportIntegrationTest extends AbstractComponentReportIntegrationTest {
    private JavaVersion currentJvm = JavaVersion.current()
    private String currentJava = "java" + currentJvm.majorVersion
    private String currentJdk = String.format("JDK %s (%s)", currentJvm.majorVersion, currentJvm);

    def "shows details of Java library"() {
        given:
        buildFile << """
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        someLib(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'library-only'
                        project 'project-only'
                        library 'some-library' project 'some-project'
                    }
                }
            }
        }
    }
}
"""
        when:
        succeeds "components"

        then:
        outputMatches output, """
JVM library 'someLib'
---------------------

Source sets
    Java source 'someLib:java'
        srcDir: src/someLib/java
        dependencies
            library 'library-only'
            project 'project-only'
            project 'some-project' library 'some-library'
    JVM resources 'someLib:resources'
        srcDir: src/someLib/resources

Binaries
    Jar 'someLibJar'
        build using task: :someLibJar
        platform: $currentJava
        tool chain: $currentJdk
        Jar file: build/jars/someLibJar/someLib.jar
"""
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "shows details of jvm library with multiple targets"() {
        given:
        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    model {
        components {
            myLib(JvmLibrarySpec) {
                targetPlatform "java5"
                targetPlatform "java6"
                targetPlatform "java7"
            }
        }
    }
"""
        when:
        succeeds "components"

        then:
        outputMatches output, """
JVM library 'myLib'
-------------------

Source sets
    Java source 'myLib:java'
        srcDir: src/myLib/java
    JVM resources 'myLib:resources'
        srcDir: src/myLib/resources

Binaries
    Jar 'java5MyLibJar'
        build using task: :java5MyLibJar
        platform: java5
        tool chain: $currentJdk
        Jar file: build/jars/java5MyLibJar/myLib.jar
    Jar 'java6MyLibJar'
        build using task: :java6MyLibJar
        platform: java6
        tool chain: $currentJdk
        Jar file: build/jars/java6MyLibJar/myLib.jar
    Jar 'java7MyLibJar'
        build using task: :java7MyLibJar
        platform: java7
        tool chain: $currentJdk
        Jar file: build/jars/java7MyLibJar/myLib.jar
"""
    }

    @Requires(TestPrecondition.JDK8_OR_EARLIER)
    def "shows which jvm libraries are buildable"() {
        given:
        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    model {
        components {
            myLib(JvmLibrarySpec) {
                targetPlatform "java5"
                targetPlatform "java6"
                targetPlatform "java9"
            }
            myLib2(JvmLibrarySpec) {
                targetPlatform "java6"
                binaries.all { buildable = false }
            }
        }
    }
"""
        when:
        succeeds "components"

        then:
        outputMatches output, """
JVM library 'myLib'
-------------------

Source sets
    Java source 'myLib:java'
        srcDir: src/myLib/java
    JVM resources 'myLib:resources'
        srcDir: src/myLib/resources

Binaries
    Jar 'java5MyLibJar'
        build using task: :java5MyLibJar
        platform: java5
        tool chain: $currentJdk
        Jar file: build/jars/java5MyLibJar/myLib.jar
    Jar 'java6MyLibJar'
        build using task: :java6MyLibJar
        platform: java6
        tool chain: $currentJdk
        Jar file: build/jars/java6MyLibJar/myLib.jar
    Jar 'java9MyLibJar' (not buildable)
        build using task: :java9MyLibJar
        platform: java9
        tool chain: $currentJdk
        Jar file: build/jars/java9MyLibJar/myLib.jar
        No tool chains can satisfy the requirement: Could not target platform: 'Java SE 9' using tool chain: '${currentJdk}'.

JVM library 'myLib2'
--------------------

Source sets
    Java source 'myLib2:java'
        srcDir: src/myLib2/java
    JVM resources 'myLib2:resources'
        srcDir: src/myLib2/resources

Binaries
    Jar 'myLib2Jar' (not buildable)
        build using task: :myLib2Jar
        platform: java6
        tool chain: $currentJdk
        Jar file: build/jars/myLib2Jar/myLib2.jar
        Disabled by user
"""
    }
}
