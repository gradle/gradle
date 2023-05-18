/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.tasks.javadoc

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue

@Requires(UnitTestPreconditions.Jdk9OrLater)
class JavadocModularizedJavaIntegrationTest extends AbstractIntegrationSpec {

    TestFile testBuildFile

    def setup() {

        settingsFile << """
            include 'test'
"""
        testBuildFile = testDirectory.file('test/build.gradle')

        testBuildFile << """
            plugins {
                id 'java-library'
            }
        """
        file('test/src/main/java/module-info.java') << """
            module test {
                exports test;
            }
        """
        file("test/src/main/java/test/Test.java") << """
            package test;

            import test.internal.TestInternal;

            public class Test {
                public void doSomething() {
                    TestInternal.doSomething();
                }
            }
        """
        file("test/src/main/java/test/internal/TestInternal.java") << """
            package test.internal;

            public class TestInternal {
                public static void doSomething() { }
            }
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/19726")
    def "can build javadoc from modularized java"() {
        when:
        succeeds("javadoc")
        then:
        file("test/build/docs/javadoc/test/test/Test.html").assertExists()
        file("test/build/docs/javadoc/test/test/internal/TestInternal.html").assertExists()
    }

    @Issue("https://github.com/gradle/gradle/issues/19726")
    def "can build javadoc from modularized java with exclusions"() {
        testBuildFile << """
            tasks.withType(Javadoc) {
                exclude("test/internal")
                // This shouldn't be necessarily, but is a workaround for now
                options.addPathOption('-source-path').value.add(file('src/main/java'))
            }
        """

        when:
        succeeds("javadoc")
        then:
        file("test/build/docs/javadoc/test/test/Test.html").assertExists()
        file("test/build/docs/javadoc/test/test/internal/TestInternal.html").assertDoesNotExist()
    }

    @Issue("https://github.com/gradle/gradle/issues/21399")
    def "can build javadoc from modularized java with -module-source-path specified"() {

        testBuildFile << """
            // Module source path needs a folder structure where the module name appears in the hierarchy
            tasks.withType(Javadoc) {
                options.addStringOption('-module-source-path', "\$projectDir/../*/src/main/java")
            }
"""

        when:
        succeeds("javadoc")
        then:
        file("test/build/docs/javadoc/test/test/Test.html").assertExists()
        file("test/build/docs/javadoc/test/test/internal/TestInternal.html").assertExists()
    }
}
