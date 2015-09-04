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

import org.gradle.api.JavaVersion
import org.gradle.api.reporting.components.AbstractComponentReportIntegrationTest

class PluginsComponentReportIntegrationTest extends AbstractComponentReportIntegrationTest {
    private JavaVersion currentJvm = JavaVersion.current()
    private String currentJava = "Java SE " + currentJvm.majorVersion
    private String currentJdk = String.format("JDK %s (%s)", currentJvm.majorVersion, currentJvm);

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
        outputMatches output, """
No components defined for this project.

Additional source sets
----------------------
Java source 'main:java'
    srcDir: src/main/java
Java source 'test:java'
    srcDir: src/test/java
JVM resources 'main:resources'
    srcDir: src/main/resources
JVM resources 'test:resources'
    srcDir: src/test/resources

Additional binaries
-------------------
Classes 'main'
    build using task: :classes
    targetPlatform: $currentJava
    tool chain: $currentJdk
    classes dir: build/classes/main
    resources dir: build/resources/main
Classes 'test'
    build using task: :testClasses
    targetPlatform: $currentJava
    tool chain: $currentJdk
    classes dir: build/classes/test
    resources dir: build/resources/test
"""
    }
}
