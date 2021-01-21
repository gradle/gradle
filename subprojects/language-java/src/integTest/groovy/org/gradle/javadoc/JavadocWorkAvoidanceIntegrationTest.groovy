/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.javadoc

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.archive.ZipTestFixture
import spock.lang.IgnoreIf
import spock.lang.Issue

@IgnoreIf({ GradleContextualExecuter.parallel })
class JavadocWorkAvoidanceIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << "include 'a', 'b'"
        buildFile << '''
            allprojects {
                apply plugin: 'java'
            }
        '''

        file('a/build.gradle') << '''
            dependencies {
                implementation project(':b')
            }
        '''

        file('a/src/main/java/A.java') << '''
            public class A {
                public void foo() {
                }
            }
        '''
        file('a/src/main/resources/A.properties') << '''
            aprop=avalue
        '''

        file('b/src/main/java/B.java') << '''
            public class B {
                public int truth() { return 0; }
            }
        '''
        file('b/src/main/resources/B.properties') << '''
            bprop=bvalue
        '''
    }

    def "does not regenerate javadoc when the upstream jar is just rebuilt without changes"() {
        given:
        succeeds(":a:javadoc")
        def bJar = file("b/build/libs/b.jar")
        def oldHash = bJar.md5Hash
        when:
        // Timestamps in the jar have a 2-second precision, so we need to see a different jar before continuing
        ConcurrentTestUtil.poll(6) {
            // cleaning b and rebuilding will cause b.jar to be different
            succeeds(":b:clean")
            succeeds(":a:javadoc")
            assert oldHash != bJar.md5Hash
        }

        then:
        result.assertTasksNotSkipped(":b:compileJava", ":b:processResources", ":b:classes", ":b:jar")
        result.assertTasksSkipped(":a:compileJava", ":a:processResources", ":a:classes", ":a:javadoc")
    }

    def "order of upstream jar entries does not matter"() {
        given:
        file("a/build.gradle") << '''
            dependencies {
                implementation rootProject.files("build/libs/external.jar")
            }
        '''
        buildFile << """
            task alphabetic(type: Jar) {
                from("external/a")
                from("external/b")
                from("external/c")
                from("external/d")

                archiveFileName = "external.jar"
            }
            task reverseAlphabetic(type: Jar) {
                from("external/d")
                from("external/c")
                from("external/b")
                from("external/a")

                archiveFileName = "external.jar"
            }
        """
        ['a', 'b', 'c', 'd'].each {
            file("external/$it").touch()
        }
        // Generate external jar with entries in alphabetical order
        def externalJar = file('build/libs/external.jar')
        expectMissingDependencyDeprecation(":alphabetic", ":a:compileJava")
        expectMissingDependencyDeprecation(":alphabetic", ":a:javadoc")
        succeeds("alphabetic", ":a:javadoc")
        new ZipTestFixture(externalJar).hasDescendantsInOrder('META-INF/MANIFEST.MF', 'a', 'b', 'c', 'd')

        when:
        // Re-generate external jar with entries in reverse alphabetical order
        succeeds("reverseAlphabetic")
        and:
        succeeds(":a:javadoc")
        then:
        // javadoc should still be up-to-date even though the upstream external.jar changed
        new ZipTestFixture(externalJar).hasDescendantsInOrder('META-INF/MANIFEST.MF', 'd', 'c', 'b', 'a')
        result.assertTasksSkipped(":b:compileJava", ":b:processResources", ":b:classes", ":b:jar",
            ":a:compileJava", ":a:processResources", ":a:classes", ":a:javadoc")
    }

    def "timestamp of upstream jar entries does not matter"() {
        given:
        file("a/build.gradle") << '''
            dependencies {
                implementation rootProject.files("build/libs/external.jar")
            }
        '''
        buildFile << """
            task currentTime(type: Jar) {
                from("external/a")
                from("external/b")
                from("external/c")
                from("external/d")

                archiveFileName = "external.jar"
            }
            task oldTime(type: Jar) {
                from("external/a")
                from("external/b")
                from("external/c")
                from("external/d")

                archiveFileName = "external.jar"
                preserveFileTimestamps = false
            }
        """
        def externalJar = file("build/libs/external.jar")
        ['a', 'b', 'c', 'd'].each {
            file("external/$it").touch()
        }
        // Generate external jar with entries with a current timestamp
        expectMissingDependencyDeprecation(":currentTime", ":a:compileJava")
        expectMissingDependencyDeprecation(":currentTime", ":a:javadoc")
        succeeds("currentTime", ":a:javadoc")
        def oldHash = externalJar.md5Hash
        when:
        // Re-generate external jar with entries with a different/fixed timestamp
        succeeds("oldTime")
        and:
        succeeds(":a:javadoc")
        then:
        // check that the upstream jar definitely changed
        oldHash != externalJar.md5Hash
        result.assertTasksSkipped(":b:compileJava", ":b:processResources", ":b:classes", ":b:jar",
            ":a:compileJava", ":a:processResources", ":a:classes", ":a:javadoc")
    }

    def "duplicates in an upstream jar are not ignored"() {
        given:
        file("a/build.gradle") << '''
            dependencies {
                implementation rootProject.files("build/libs/external.jar")
            }
        '''
        buildFile << """
            task duplicate(type: Jar) {
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
                from("external/a")
                from("external/b")
                from("external/c")
                from("external/d")
                from("duplicate/a")
                archiveFileName = "external.jar"
            }
        """
        def externalJar = file("build/libs/external.jar")
        ['a', 'b', 'c', 'd'].each {
            file("external/$it").text = "original"
        }
        def original = file("external/a")
        def duplicate = file("duplicate/a")
        duplicate.text = "duplicate"

        // Generate external jar with entries with a duplicate 'a' file
        expectMissingDependencyDeprecation(":duplicate", ":a:compileJava")
        expectMissingDependencyDeprecation(":duplicate", ":a:javadoc")
        succeeds("duplicate", ":a:javadoc")
        def oldHash = externalJar.md5Hash

        when:
        // change the second duplicate
        duplicate.text = "changed to something else"
        succeeds("duplicate")
        then:
        // check that the upstream jar definitely changed
        oldHash != externalJar.md5Hash

        when:
        succeeds(":a:javadoc")
        then:
        result.assertTasksNotSkipped(":a:javadoc")
        result.assertTasksSkipped(":b:compileJava", ":b:processResources", ":b:classes", ":b:jar",
            ":a:compileJava", ":a:processResources", ":a:classes")
        when:
        // change the first duplicate
        original.text = "changed to something else"
        succeeds("duplicate")
        then:
        // check that the upstream jar definitely changed
        oldHash != externalJar.md5Hash

        when:
        succeeds(":a:javadoc")
        then:
        result.assertTasksNotSkipped(":a:javadoc")
        result.assertTasksSkipped(":b:compileJava", ":b:processResources", ":b:classes", ":b:jar",
            ":a:compileJava", ":a:processResources", ":a:classes")
    }

    @Issue("https://github.com/gradle/gradle/issues/6168")
    def "removes stale outputs from last execution"() {
        def aaJava = file('a/src/main/java/AA.java')
        aaJava << '''
            public class AA {
                public void foo() {
                }
            }
        '''

        when:
        succeeds(":a:javadoc")
        then:
        file("a/build/docs/javadoc/A.html").isFile()
        file("a/build/docs/javadoc/AA.html").isFile()

        when:
        assert aaJava.delete()
        succeeds(":a:javadoc")
        then:
        executedAndNotSkipped(":a:javadoc")
        file("a/build/docs/javadoc/A.html").isFile()
        !file("a/build/docs/javadoc/AA.html").isFile()
    }

    void expectMissingDependencyDeprecation(String producer, String consumer) {
        executer.expectDocumentedDeprecationWarning(
            "Task '${consumer}' uses the output of task '${producer}', without declaring an explicit dependency (using Task.dependsOn() or Task.mustRunAfter()) or an implicit dependency (declaring task '${producer}' as an input). " +
                "This can lead to incorrect results being produced, depending on what order the tasks are executed. " +
                "This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. " +
                "Execution optimizations are disabled due to the failed validation. " +
                "See https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:up_to_date_checks for more details.")
    }
}
