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

package org.gradle.api.plugins

import org.gradle.api.internal.component.BuildableJavaComponent
import org.gradle.api.internal.component.ComponentRegistry
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.InspectsConfigurationReport
import spock.lang.Issue

class JavaPluginIntegrationTest extends AbstractIntegrationSpec implements InspectsConfigurationReport {

    def appliesBasePluginsAndAddsConventionObject() {
        given:
        buildFile << """
            apply plugin: 'java'

            task expect {

                def component = project.services.get(${ComponentRegistry.canonicalName}).mainComponent
                assert component instanceof ${BuildableJavaComponent.canonicalName}
                assert component.runtimeClasspath != null
                assert component.compileDependencies == project.configurations.compileClasspath

                def buildTasks = component.buildTasks as List
                doLast {
                    assert buildTasks == [ JavaBasePlugin.BUILD_TASK_NAME ]
                }
            }
        """
        expect:
        succeeds "expect"
    }

    def "Java plugin adds outgoing variant for main source set"() {
        buildFile << """
            plugins {
                id 'java'
            }
            """

        expect:
        succeeds "outgoingVariants"

        outputContains("""
--------------------------------------------------
Variant mainSourceElements (i)
--------------------------------------------------
List of source directories contained in the Main SourceSet.

Capabilities
    - :${getTestDirectory().getName()}:unspecified (default capability)
Attributes
    - org.gradle.category            = verification
    - org.gradle.dependency.bundling = external
    - org.gradle.verificationtype    = main-sources
Artifacts
    - src${File.separator}main${File.separator}java (artifactType = directory)
    - src${File.separator}main${File.separator}resources (artifactType = directory)""")

        and:
        hasIncubatingLegend()
    }

    def "Java plugin adds outgoing variant for main source set containing additional directories"() {
        buildFile << """
            plugins {
                id 'java'
            }

            sourceSets.main.java.srcDir 'src/more/java'
            """
        file("src/more/java").createDir()

        expect:
        succeeds "outgoingVariants"

        outputContains("""
--------------------------------------------------
Variant mainSourceElements (i)
--------------------------------------------------
List of source directories contained in the Main SourceSet.

Capabilities
    - :${getTestDirectory().getName()}:unspecified (default capability)
Attributes
    - org.gradle.category            = verification
    - org.gradle.dependency.bundling = external
    - org.gradle.verificationtype    = main-sources
Artifacts
    - src${File.separator}main${File.separator}java (artifactType = directory)
    - src${File.separator}main${File.separator}resources (artifactType = directory)
    - src${File.separator}more${File.separator}java (artifactType = directory)""")

        and:
        hasIncubatingLegend()
    }

    def "mainSourceElements can be consumed by another task via Dependency Management"() {
        buildFile << """
            plugins {
                id 'java'
            }

            // A resolvable configuration to collect source data
            def sourceElementsConfig = configurations.create("sourceElements") {
                visible = true
                canBeResolved = true
                canBeConsumed = false
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.VERIFICATION))
                    attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType.class, VerificationType.MAIN_SOURCES))
                }
            }

            dependencies {
                sourceElements project
            }

            def expectedResolvedFiles = [project.file("src/main/resources"), project.file("src/main/java")]

            def testResolve = tasks.register('testResolve') {
                doLast {
                    assert sourceElementsConfig.getResolvedConfiguration().getFiles().containsAll(expectedResolvedFiles)
                }
            }
            """.stripIndent()

        file("src/main/java/com/example/MyClass.java") << """
            package com.example;

            public class MyClass {
                public void hello() {
                    System.out.println("Hello");
                }
            }
            """.stripIndent()

        expect:
        succeeds('testResolve')
    }

    def "mainSourceElements can be consumed by another task in a different project via Dependency Management"() {
        def subADir = createDir("subA")
        def buildFileA = subADir.file("build.gradle") << """
            plugins {
                id 'java'
            }
            """.stripIndent()

        subADir.file("src/test/java/com/exampleA/MyClassA.java") << """
            package com.exampleA;

            public class MyClassA {
                public void hello() {
                    System.out.println("Hello");
                }
            }
            """.stripIndent()

        def subBDir = createDir("subB")
        def buildFileB = subBDir.file("build.gradle") << """
            plugins {
                id 'java'
            }
            """.stripIndent()

        subBDir.file("src/test/java/com/exampleB/MyClassB.java") << """
            package com.exampleB;

            public class MyClassB {
                public void hello() {
                    System.out.println("Hello");
                }
            }
            """.stripIndent()

        settingsFile << """
            include ':subA'
            include ':subB'
            """.stripIndent()

        buildFile << """
            plugins {
                id 'java'
            }

            dependencies {
                implementation project(':subA')
                implementation project(':subB')
            }

            // A resolvable configuration to collect JaCoCo coverage data
            def sourceElementsConfig = configurations.create("sourceElements") {
                visible = true
                canBeResolved = true
                canBeConsumed = false
                extendsFrom(configurations.implementation)
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.VERIFICATION))
                    attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType, VerificationType.MAIN_SOURCES))
                }
            }

            def expectedResolvedFiles = [project(':subA').file("src/main/resources"),
                                         project(':subA').file("src/main/java"),
                                         project(':subB').file("src/main/resources"),
                                         project(':subB').file("src/main/java")]

            def testResolve = tasks.register('testResolve') {
                doLast {
                    assert sourceElementsConfig.getResolvedConfiguration().getFiles().containsAll(expectedResolvedFiles)
                }
            }
            """.stripIndent()

        expect:
        succeeds('testResolve')
    }

    @Issue("https://github.com/gradle/gradle/issues/19914")
    def "calling jar task doesn't force realization of test task"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            dependencies {
                testImplementation 'junit:junit:4.13'
            }

            tasks.withType(Test).configureEach {
                throw new RuntimeException("Test task should not have been realized")
            }""".stripIndent()

        file("src/main/java/com/example/SampleClass.java") << """
            package com.example;

            public class SampleClass {
                public String hello() {
                    return "hello";
                }
            }
            """.stripIndent()

        file("src/test/java/com/example/SampleTest.java") << """
            package com.example;

            import org.junit.Test;
            import static org.junit.Assert.assertEquals;

            public class SampleTest {
                @Test
                public void checkHello() {
                    SampleClass sampleClass = new SampleClass();
                    assertEquals("hello", sampleClass.hello());
                }
            }""".stripIndent()

        expect:
        succeeds "jar"
    }

    @Issue("https://github.com/gradle/gradle/issues/19914")
    def "calling test task doesn't force execution of jar task"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            dependencies {
                testImplementation 'junit:junit:4.13'
            }

            tasks.withType(Jar).configureEach {
                doLast {
                    throw new RuntimeException("Jar task should not have been executed")
                }
            }""".stripIndent()

        file("src/main/java/com/example/SampleClass.java") << """
            package com.example;

            public class SampleClass {
                public String hello() {
                    return "hello";
                }
            }
            """.stripIndent()

        file("src/test/java/com/example/SampleTest.java") << """
            package com.example;

            import org.junit.Test;
            import static org.junit.Assert.assertEquals;

            public class SampleTest {
                @Test
                public void checkHello() {
                    SampleClass sampleClass = new SampleClass();
                    assertEquals("hello", sampleClass.hello());
                }
            }""".stripIndent()

        expect:
        succeeds "test"
    }

    @Issue("https://github.com/gradle/gradle/issues/19914")
    def "running test and jar tasks in that order should result in jar running first"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            dependencies {
                testImplementation 'junit:junit:4.13'
            }""".stripIndent()

        file("src/main/java/com/example/SampleClass.java") << """
            package com.example;

            public class SampleClass {
                public String hello() {
                    return "hello";
                }
            }
            """.stripIndent()

        file("src/test/java/com/example/SampleTest.java") << """
            package com.example;

            import org.junit.Test;
            import static org.junit.Assert.assertEquals;

            public class SampleTest {
                @Test
                public void checkHello() {
                    SampleClass sampleClass = new SampleClass();
                    assertEquals("hello", sampleClass.hello());
                }
            }""".stripIndent()

        expect:
        succeeds "test", "jar"
        result.assertTaskOrder(":jar", ":test")
    }

    @Issue("https://github.com/gradle/gradle/issues/19914")
    def "when project B depends on A, running tests in B should cause As jar task to run prior to As tests"() {
        given:
        settingsFile << """
            include ':subA'
            include ':subB'
        """.stripIndent()

        def subADir = createDir("subA")
        subADir.file("build.gradle") << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            dependencies {
                testImplementation 'junit:junit:4.13'
            }""".stripIndent()


        subADir.file("src/main/java/com/exampleA/SampleClassA.java") << """
            package com.exampleA;

            public class SampleClassA {
                public String hello() {
                    return "hello";
                }
            }
            """.stripIndent()

        subADir.file("src/test/java/com/exampleA/SampleTestA.java") << """
            package com.exampleA;

            import org.junit.Test;
            import static org.junit.Assert.assertEquals;

            public class SampleTestA {
                @Test
                public void checkHello() {
                    SampleClassA sampleClass = new SampleClassA();
                    assertEquals("hello", sampleClass.hello());
                }
            }""".stripIndent()

        def subBDir = createDir("subB")
        subBDir.file("build.gradle") << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
            }

            dependencies {
                implementation project(':subA')
                testImplementation 'junit:junit:4.13'
            }
            """.stripIndent()

        subBDir.file("src/main/java/com/exampleB/SampleClassB.java") << """
            package com.exampleB;

            import com.exampleA.SampleClassA;

            public class SampleClassB {
                public String hello() {
                    SampleClassA sampleClassA = new SampleClassA();
                    return sampleClassA.hello() + " there";
                }
            }
            """.stripIndent()

        subBDir.file("src/test/java/com/exampleB/SampleTestB.java") << """
            package com.exampleB;

            import org.junit.Test;
            import static org.junit.Assert.assertEquals;

            public class SampleTestB {
                @Test
                public void checkHello() {
                    SampleClassB sampleClass = new SampleClassB();
                    assertEquals("hello there", sampleClass.hello());
                }
            }""".stripIndent()

        expect:
        succeeds ":subB:test"
        result.assertTaskOrder(":subA:jar", ":subB:test")
        result.assertTaskNotExecuted(":subA:test")
    }
}
