/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.tasks.compile

import org.gradle.api.JavaVersion;
import org.gradle.integtests.fixtures.AbstractIntegrationSpec;

public class JavaCompileDefaultJavaVersionIntegrationTest extends AbstractIntegrationSpec {

    def originalJavaVersion = System.properties["java.version"] as String

    def setup() {
        setJavaVersion "1.6"
    }

    def tearDown() {
        setJavaVersion originalJavaVersion
    }

    public void "not up-to-date when default Java version changes"() {
        given:
        buildFile << """
            apply plugin: "java"

            sourceCompatibility = "1.6"
            targetCompatibility = "1.6"
        """

        and:
        file("src/main/java/org/gradle/Person.java") << """
            package org.gradle;
            class Person {}
        """

        when:
        succeeds "compileJava"
        then:
        nonSkippedTasks.contains ":compileJava"

        when:
        succeeds "compileJava"
        then:
        skippedTasks.contains ":compileJava"

        when:
        setJavaVersion "1.7"
        succeeds "compileJava", "--info"
        then:
        nonSkippedTasks.contains ":compileJava"
    }

    private static void setJavaVersion(String version) {
        System.properties["java.version"] = version
        //noinspection GroovyAccessibility
        JavaVersion.resetCurrent()
    }
}
