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
package org.gradle.java

import org.gradle.api.reporting.components.AbstractComponentReportIntegrationTest

class PluginsComponentReportIntegrationTest extends AbstractComponentReportIntegrationTest {

    def "shows details of legacy Java project"() {
        given:
        buildFile << """
plugins {
    id 'java'
}
"""
        when:
        succeeds "components"

        then:
        outputMatches """
No components defined for this project.

Additional source sets
----------------------
Java source 'main:java'
    srcDir: src/main/java
    limit to: **/*.java
Java source 'test:java'
    srcDir: src/test/java
    limit to: **/*.java
JVM resources 'main:resources'
    srcDir: src/main/resources
JVM resources 'test:resources'
    srcDir: src/test/resources

Additional binaries
-------------------
Classes 'main'
    build using task: :classes
    target platform: $currentJava
    tool chain: $currentJdk
    classes dir: build/classes/java/main
    resources dir: build/resources/main
Classes 'test'
    build using task: :testClasses
    target platform: $currentJava
    tool chain: $currentJdk
    classes dir: build/classes/java/test
    resources dir: build/resources/test
"""
    }

    def "shows details of mixed legacy Java and JVM library project"() {
        given:
        buildFile << """
plugins {
    id 'java'
    id 'jvm-component'
}
model {
    components {
        lib(JvmLibrarySpec)
    }
}
"""
        when:
        succeeds "components"

        then:
        outputMatches """
JVM library 'lib'
-----------------

Source sets
    No source sets.

Binaries
    Jar 'lib:jar'
        build using task: :libJar
        target platform: $currentJava
        tool chain: $currentJdk
        classes dir: build/classes/lib/jar
        resources dir: build/resources/lib/jar
        API Jar file: build/jars/lib/jar/api/lib.jar
        Jar file: build/jars/lib/jar/lib.jar

Additional source sets
----------------------
Java source 'main:java'
    srcDir: src/main/java
    limit to: **/*.java
Java source 'test:java'
    srcDir: src/test/java
    limit to: **/*.java
JVM resources 'main:resources'
    srcDir: src/main/resources
JVM resources 'test:resources'
    srcDir: src/test/resources

Additional binaries
-------------------
Classes 'main'
    build using task: :classes
    target platform: $currentJava
    tool chain: $currentJdk
    classes dir: build/classes/java/main
    resources dir: build/resources/main
Classes 'test'
    build using task: :testClasses
    target platform: $currentJava
    tool chain: $currentJdk
    classes dir: build/classes/java/test
    resources dir: build/resources/test
"""
    }

    def "shows details of legacy Java project with custom source sets"() {
        given:
        buildFile << """
plugins {
    id 'java'
}
sourceSets {
    custom {
        java.srcDirs = ['src/custom', 'src/custom2']
    }
}
"""
        when:
        succeeds "components"

        then:
        outputMatches """
No components defined for this project.

Additional source sets
----------------------
Java source 'custom:java'
    srcDir: src/custom
    srcDir: src/custom2
    limit to: **/*.java
Java source 'main:java'
    srcDir: src/main/java
    limit to: **/*.java
Java source 'test:java'
    srcDir: src/test/java
    limit to: **/*.java
JVM resources 'custom:resources'
    srcDir: src/custom/resources
JVM resources 'main:resources'
    srcDir: src/main/resources
JVM resources 'test:resources'
    srcDir: src/test/resources

Additional binaries
-------------------
Classes 'custom'
    build using task: :customClasses
    target platform: $currentJava
    tool chain: $currentJdk
    classes dir: build/classes/java/custom
    resources dir: build/resources/custom
Classes 'main'
    build using task: :classes
    target platform: $currentJava
    tool chain: $currentJdk
    classes dir: build/classes/java/main
    resources dir: build/resources/main
Classes 'test'
    build using task: :testClasses
    target platform: $currentJava
    tool chain: $currentJdk
    classes dir: build/classes/java/test
    resources dir: build/resources/test
"""
    }
}
