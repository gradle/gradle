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

package org.gradle.api.tasks.compile

import org.apache.commons.io.FileUtils
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildCacheOperationFixtures
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.junit.Rule
import spock.lang.Issue

class JavaCompileAvoidanceWithBuildCacheServiceIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    @Rule
    HttpServer server

    def "classes from cache are used when dependent class is changed in ABI compatible way"() {
        given:
        project_a_depends_on_project_b()

        when:
        withBuildCache().run 'assemble'

        then:
        executedAndNotSkipped ':b:jar'
        executedAndNotSkipped ':a:jar'

        when:
        make_abi_compatible_change_on_b()

        withBuildCache().run 'clean', 'assemble'

        then:
        executedAndNotSkipped ':b:jar'
        skipped ':a:compileJava'
    }

    def "classes from cache are not used when dependent class is changed in ABI breaking way"() {
        given:
        project_a_depends_on_project_b()

        when:
        withBuildCache().run 'assemble'

        then:
        executedAndNotSkipped ':b:jar'
        executedAndNotSkipped ':a:jar'

        when:
        make_abi_breaking_change_on_b()

        withBuildCache().run 'clean', 'assemble'

        then:
        executedAndNotSkipped ':b:jar', ':a:compileJava'
    }

    @Issue("https://github.com/gradle/gradle/issues/32464")
    def "kotlin library on build script classpath and Java classpath doesn't affect Java compilation build cache key"() {
        given:
        // We need to work with remote Kotlin libraries with inline functions to reproduce the cache issue
        def repo = publishKotlinLibraryWithInlineFunctionToRemote()
        def kotlinLibraryWithInlineFunction = "com.example:kotlin-library:1.0.0"

        file("a/build.gradle") << """
            plugins {
                id("java-library")
            }
            repositories {
                maven { url = uri("${repo.uri}") }
            }
            dependencies {
                implementation("$kotlinLibraryWithInlineFunction")
            }
        """
        file("a/settings.gradle") << """
            rootProject.name = "a"
        """
        file('a/src/main/java/A.java') << '''
            public class A {
                public void foo() {
                }
            }
        '''
        file("a/gradle/init.gradle.kts") << """
            initscript {
                repositories {
                    maven { url = uri("${repo.uri}") }
                }
                dependencies {
                    classpath("$kotlinLibraryWithInlineFunction")
                }
            }
            // We need to have some code in body of Kotlin init script
            // to ensure that Kotlin buildscript classpath is snapshotted
            println("Script body")
        """
        def aRelocated = file("a-relocated")
        FileUtils.copyDirectory(file("a"), aRelocated)

        when:
        def firstOperations = new BuildCacheOperationFixtures(new BuildOperationsFixture(executer, testDirectoryProvider, "first-operations"))
        result = executer
            .withGradleUserHomeDir(testDirectoryProvider.getTestDirectory().file("first-user-home"))
            .inDirectory(file("a"))
            .withArgument("--init-script")
            .withArgument("gradle/init.gradle.kts")
            .withBuildCacheEnabled()
            .withTasks("compileJava")
            .run()

        then:
        executedAndNotSkipped ':compileJava'

        when:
        def secondOperations = new BuildCacheOperationFixtures(new BuildOperationsFixture(executer, testDirectoryProvider, "second-operations"))
        result = executer
            .withGradleUserHomeDir(testDirectoryProvider.getTestDirectory().file("second-user-home"))
            .inDirectory(aRelocated)
            .withBuildCacheEnabled()
            .withTasks("compileJava")
            .run()

        then:
        executedAndNotSkipped ':compileJava'

        and:
        def firstCacheKey = firstOperations.getCacheKeyForTask(":compileJava")
        def secondCacheKey = secondOperations.getCacheKeyForTask(":compileJava")
        firstCacheKey == secondCacheKey
    }

    void make_abi_compatible_change_on_b() {
        file('b/src/main/java/B.java').text = '''
            public class B {
                public int truth() {
                    System.out.println("I knew it!");
                    return 42;
                }
            }
        '''
    }

    void make_abi_breaking_change_on_b() {
        file('b/src/main/java/B.java').text = '''
            public class B {
                public int truth() { return 42; }
                public void newMethod() { }
            }
        '''
    }

    void project_a_depends_on_project_b() {
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
            public class A extends B {
                public void foo() {
                    int x = truth();
                }
            }
        '''
        file('b/src/main/java/B.java') << '''
            public class B {
                public int truth() { return 0; }
            }
        '''
    }


    def publishKotlinLibraryWithInlineFunctionToRemote() {
        file("kotlin-library/build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
                `maven-publish`
            }
            group = "com.example"
            version = "1.0.0"

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            publishing {
                repositories {
                    maven {
                        url = uri(layout.buildDirectory.dir("repo"))
                    }
                }
            }
        """
        file("kotlin-library/settings.gradle") << """
            rootProject.name = "kotlin-library"
        """
        file("kotlin-library/src/main/kotlin/MyClass.kt") << """
            class MyClass {
                private val availableCpuPermits: Int inline get() = availableCpuPermits(8)
                public inline fun availableCpuPermits(state: Long): Int = state.toInt()
            }
        """

        def repo = new MavenHttpRepository(server, "/repo", new MavenFileRepository(file("./kotlin-library/build/repo")))
        repo.module("com.example", "kotlin-library", "1.0.0").allowAll()
        server.start()

        executer
            .inDirectory(file("kotlin-library"))
            .withTasks("publish")
            .run()

        return repo
    }
}
