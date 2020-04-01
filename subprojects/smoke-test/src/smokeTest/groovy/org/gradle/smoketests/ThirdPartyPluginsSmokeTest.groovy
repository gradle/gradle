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

package org.gradle.smoketests

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.util.GradleVersion
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.VersionNumber
import org.gradle.util.ports.ReleasingPortAllocator
import org.gradle.vcs.fixtures.GitFileRepository
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

class ThirdPartyPluginsSmokeTest extends AbstractSmokeTest {

    @Rule
    final ReleasingPortAllocator portAllocator = new ReleasingPortAllocator()

    @Unroll
    @Issue('https://plugins.gradle.org/plugin/com.github.johnrengelman.shadow')
    @ToBeFixedForInstantExecution
    def 'shadow plugin #version'() {
        given:
        buildFile << """
            import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer

            plugins {
                id 'java' // or 'groovy' Must be explicitly applied
                id 'com.github.johnrengelman.shadow' version '$version'
            }

            ${jcenterRepository()}

            dependencies {
                compile 'commons-collections:commons-collections:3.2.2'
            }

            shadowJar {
                transform(ServiceFileTransformer)

                manifest {
                    attributes 'Test-Entry': 'PASSED'
                }
            }
            """.stripIndent()

        when:
        def result = runner('shadowJar').build()

        then:
        result.task(':shadowJar').outcome == SUCCESS

        if (version == TestedVersions.shadow.latest()) {
            expectDeprecationWarnings(result,
                "Property 'transformers.\$0.serviceEntries' is not annotated with an input or output annotation. " +
                    "This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. " +
                    "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/more_about_tasks.html#sec:up_to_date_checks for more details."
            )
        }

        where:
        version << TestedVersions.shadow
    }

    @Issue('https://github.com/asciidoctor/asciidoctor-gradle-plugin/releases')
    @ToBeFixedForInstantExecution(because = "Task.getProject() during execution")
    def 'asciidoctor legacy plugin'() {
        given:
        buildFile << """
            buildscript {
                ${jcenterRepository()}
                dependencies {
                    classpath "org.asciidoctor:asciidoctor-gradle-plugin:1.5.11"
                }
            }

            apply plugin: 'org.asciidoctor.gradle.asciidoctor'
            """.stripIndent()

        file('src/docs/asciidoc/test.adoc') << """
            = Line Break Doc Title
            :hardbreaks:

            Rubies are red,
            Topazes are blue.
            """.stripIndent()

        when:
        def result = runner('asciidoc').build()

        then:
        file('build/asciidoc').isDirectory()

        expectDeprecationWarnings(result,
            "Type 'AsciidoctorTask': non-property method 'asGemPath()' should not be annotated with: @Optional, @InputDirectory. " +
                "This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. " +
                "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/more_about_tasks.html#sec:up_to_date_checks for more details.",
            "Property 'logDocuments' has redundant getters: 'getLogDocuments()' and 'isLogDocuments()'. " +
                "This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. " +
                "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/more_about_tasks.html#sec:up_to_date_checks for more details.",
            "Property 'separateOutputDirs' has redundant getters: 'getSeparateOutputDirs()' and 'isSeparateOutputDirs()'. " +
                "This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. " +
                "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/more_about_tasks.html#sec:up_to_date_checks for more details.",
        )
    }

    @Issue('https://github.com/asciidoctor/asciidoctor-gradle-plugin/releases')
    @Unroll
    @ToBeFixedForInstantExecution(because = "Task.getProject() during execution")
    def 'asciidoctor plugin #version'() {
        given:
        def version3 = VersionNumber.parse("3.0.0")
        final pluginId
        // asciidoctor changed plugin ids after 3.0
        if (VersionNumber.parse(version) >= version3) {
            pluginId = "org.asciidoctor.jvm.convert"
        } else {
            pluginId = "org.asciidoctor.convert"
        }
        buildFile << """
            plugins {
                id '${pluginId}' version '${version}'
            }

            repositories {
                ${jcenterRepository()}
            }
        """

        file('src/docs/asciidoc/test.adoc') << """
            = Line Break Doc Title
            :hardbreaks:

            Rubies are red,
            Topazes are blue.
            """.stripIndent()

        when:
        def result = runner('asciidoc').build()

        then:
        if (VersionNumber.parse(version) >= version3) {
            file('build/docs/asciidoc').isDirectory()
        } else {
            file('build/asciidoc').isDirectory()
            expectDeprecationWarnings(result,
                    "You are using one or more deprecated Asciidoctor task or plugins. These will be removed in a future release. To help you migrate we have compiled some tips for you based upon your current usage:",
                    "  - 'org.asciidoctor.convert' is deprecated. When you have time please switch over to 'org.asciidoctor.jvm.convert'.",
                    "Property 'logDocuments' is annotated with @Optional that is not allowed for @Console properties. " +
                            "This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. " +
                            "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/more_about_tasks.html#sec:up_to_date_checks for more details.",
            )
        }

        where:
        version << TestedVersions.asciidoctor
    }

    @Issue('https://plugins.gradle.org/plugin/com.bmuschko.docker-java-application')
    def 'docker plugin'() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'application'
                id "com.bmuschko.docker-java-application" version "${TestedVersions.docker}"
            }

            mainClassName = 'org.gradle.JettyMain'

            docker {
                javaApplication {
                    baseImage = 'dockerfile/java:openjdk-7-jre'
                    ports = [9090]
                    images = ['jettyapp:1.115']
                }
            }
            """.stripIndent()

        when:
        def result = runner('assemble').forwardOutput().build()

        then:
        result.task(':assemble').outcome == SUCCESS

        expectNoDeprecationWarnings(result)
    }

    @Issue('https://plugins.gradle.org/plugin/io.spring.dependency-management')
    @ToBeFixedForInstantExecution
    def 'spring dependency management plugin'() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'io.spring.dependency-management' version '${TestedVersions.springDependencyManagement}'
            }

            ${mavenCentralRepository()}

            dependencyManagement {
                dependencies {
                    dependency 'org.springframework:spring-core:4.0.3.RELEASE'
                    dependency group: 'commons-logging', name: 'commons-logging', version: '1.1.2'
                }
            }

            dependencies {
                implementation 'org.springframework:spring-core'
            }
            """.stripIndent()

        when:
        def result = runner("dependencies", "--configuration", "compileClasspath").build()

        then:
        result.output.contains('org.springframework:spring-core -> 4.0.3.RELEASE')

        expectNoDeprecationWarnings(result)
    }

    @Issue('https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-gradle-plugin')
    @ToBeFixedForInstantExecution(because = ":buildEnvironment")
    def 'spring boot plugin'() {
        given:
        buildFile << """
            plugins {
                id "org.springframework.boot" version "${TestedVersions.springBoot}"
            }
        """.stripIndent()

        file('src/main/java/example/Application.java') << """
            package example;

            public class Application {
                public static void main(String[] args) {}
            }
        """.stripIndent()

        when:
        def result = runner('build').build()
        println(result.output)

        then:
        result.task(':buildEnvironment').outcome == SUCCESS

        expectNoDeprecationWarnings(result)
    }

    @Issue('https://plugins.gradle.org/plugin/com.bmuschko.tomcat')
    @ToBeFixedForInstantExecution(because = "Task.getProject() during execution")
    def 'tomcat plugin'() {
        given:
        def httpPort = portAllocator.assignPort()
        def httpsPort = portAllocator.assignPort()
        def stopPort = portAllocator.assignPort()
        buildFile << """
            plugins {
                id "com.bmuschko.tomcat" version "${TestedVersions.tomcat}"
            }

            ${mavenCentralRepository()}

            dependencies {
                def tomcatVersion = '7.0.59'
                tomcat "org.apache.tomcat.embed:tomcat-embed-core:\${tomcatVersion}",
                       "org.apache.tomcat.embed:tomcat-embed-logging-juli:\${tomcatVersion}",
                       "org.apache.tomcat.embed:tomcat-embed-jasper:\${tomcatVersion}"
            }

            ext {
                tomcatStopPort = ${stopPort}
                tomcatStopKey = 'stopKey'
            }

            tomcat {
                httpPort = ${httpPort}
                httpsPort = ${httpsPort}
            }

            task integrationTomcatRun(type: com.bmuschko.gradle.tomcat.tasks.TomcatRun) {
                stopPort = tomcatStopPort
                stopKey = tomcatStopKey
                daemon = true
            }

            task integrationTomcatStop(type: com.bmuschko.gradle.tomcat.tasks.TomcatStop) {
                stopPort = tomcatStopPort
                stopKey = tomcatStopKey
            }

            task integrationTest(type: Test) {
                include '**/*IntegrationTest.*'
                dependsOn integrationTomcatRun
                finalizedBy integrationTomcatStop
            }

            test {
                exclude '**/*IntegrationTest.*'
            }
            """.stripIndent()

        when:
        def result = runner('integrationTest').build()

        then:
        expectDeprecationWarnings(result,
            "Property 'classesJarScanningRequired' is private and annotated with @Internal. " +
                "This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. " +
                "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/more_about_tasks.html#sec:up_to_date_checks for more details.",
            "The AbstractArchiveTask.baseName property has been deprecated. This is scheduled to be removed in Gradle 7.0. Please use the archiveBaseName property instead. " +
                "See https://docs.gradle.org/${GradleVersion.current().version}/dsl/org.gradle.api.tasks.bundling.AbstractArchiveTask.html#org.gradle.api.tasks.bundling.AbstractArchiveTask:baseName for more details."
        )
    }

    @Issue('https://plugins.gradle.org/plugin/org.ajoberstar.grgit')
    def 'org.ajoberstar.grgit plugin'() {
        given:
        GitFileRepository.init(testProjectDir.root)
        buildFile << """
            plugins {
                id "org.ajoberstar.grgit" version "${TestedVersions.grgit}"
            }

            def sourceFile = file("sourceFile")

            task commit {
                doLast {
                    sourceFile.text = "hello world"
                    grgit.add(patterns: [ 'sourceFile' ])
                    grgit.commit {
                        message = "first commit"
                    }
                }
            }

            task tag {
                dependsOn commit
                doLast {
                    grgit.tag.add {
                        name = 'previous'
                        message = 'previous commit'
                    }

                    sourceFile.text = "goodbye world"
                    grgit.add(patterns: [ 'sourceFile' ])
                    grgit.commit {
                        message = "second commit"
                    }
                }
            }

            task checkout {
                dependsOn tag
                doLast {
                    assert sourceFile.text == 'goodbye world'
                    grgit.checkout {
                        branch = 'previous'
                    }
                    assert sourceFile.text == 'hello world'
                }
            }

            task release {
                dependsOn checkout
            }
        """.stripIndent()

        when:
        def result = runner('release').build()

        then:
        result.task(':commit').outcome == SUCCESS
        result.task(':tag').outcome == SUCCESS
        result.task(':checkout').outcome == SUCCESS

        expectNoDeprecationWarnings(result)
    }

    @Issue('https://plugins.gradle.org/plugin/com.github.spotbugs')
    @Requires(TestPrecondition.JDK11_OR_EARLIER)
    @ToBeFixedForInstantExecution
    def 'spotbugs plugin'() {
        given:
        buildFile << """
            import com.github.spotbugs.snom.SpotBugsTask

            plugins {
                id 'java'
                id 'com.github.spotbugs' version '${TestedVersions.spotbugs}'
            }

            ${jcenterRepository()}

            tasks.withType(SpotBugsTask) {
                reports.create("html")
            }

            """.stripIndent()

        file('src/main/java/example/Application.java') << """
            package example;

            public class Application {
                public static void main(String[] args) {}
            }
        """.stripIndent()


        when:
        def result = runner('spotbugsMain').build()

        then:
        file('build/reports/spotbugs').isDirectory()

        expectNoDeprecationWarnings(result)
    }

    @Issue("https://github.com/gradle/gradle/issues/9897")
    @ToBeFixedForInstantExecution(because = "unsupported Configuration field")
    def 'errorprone plugin'() {
        given:
        buildFile << """
            plugins {
                id('java')
                id("net.ltgt.errorprone") version "${TestedVersions.errorProne}"
            }

            ${mavenCentralRepository()}

            if (JavaVersion.current().java8) {
                dependencies {
                    errorproneJavac("com.google.errorprone:javac:9+181-r4173-1")
                }
            }

            dependencies {
                errorprone("com.google.errorprone:error_prone_core:2.3.3")
            }

            tasks.withType(JavaCompile).configureEach {
                options.fork = true
                options.errorprone {
                    check("DoubleBraceInitialization", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
                }
            }
        """
        file("src/main/java/Test.java") << """
            import java.util.HashSet;
            import java.util.Set;

            public class Test {

                public static void main(String[] args) {
                }

            }
        """
        when:
        def result = runner('compileJava').forwardOutput().build()

        then:
        expectNoDeprecationWarnings(result)
    }

    @Issue("https://plugins.gradle.org/plugin/com.google.protobuf")
    @ToBeFixedForInstantExecution
    def "protobuf plugin"() {
        given:
        buildFile << """
            plugins {
                id('java')
                id("com.google.protobuf") version "${TestedVersions.protobufPlugin}"
            }

            ${mavenCentralRepository()}

            protobuf {
                protoc {
                    artifact = "com.google.protobuf:protoc:${TestedVersions.protobufTools}"
                }
            }
            dependencies {
                implementation "com.google.protobuf:protobuf-java:${TestedVersions.protobufTools}"
            }
        """

        and:
        file("src/main/proto/sample.proto") << """
            syntax = "proto3";
            option java_package = "my.proto";
            option java_multiple_files = true;
            message Msg {
                string text = 1;
            }
        """
        file("src/main/java/my/Sample.java") << """
            package my;
            import my.proto.Msg;
            public class Sample {}
        """

        when:
        def result = runner('compileJava').forwardOutput().build()

        then:
        result.task(":generateProto").outcome == SUCCESS
        result.task(":compileJava").outcome == SUCCESS

        expectNoDeprecationWarnings(result)

        when:
        result = runner('compileJava').forwardOutput().build()

        then:
        result.task(":generateProto").outcome == UP_TO_DATE
        result.task(":compileJava").outcome == UP_TO_DATE
    }

    // Latest AspectJ 1.9.5 is not compatible with JDK14
    @Requires(TestPrecondition.JDK13_OR_EARLIER)
    @Issue('https://plugins.gradle.org/plugin/io.freefair.aspectj')
    @ToBeFixedForInstantExecution(because = "Task.getProject() during execution")
    def 'freefair aspectj plugin'() {
        given:
        buildFile << """
            plugins {
                id "java-library"
                id "io.freefair.aspectj" version "${TestedVersions.aspectj}"
            }

            ${mavenCentralRepository()}

            aspectj {
                version = "1.9.5"
            }

            dependencies {
                inpath "org.apache.httpcomponents:httpcore-nio:4.4.11"
                implementation "org.aspectj:aspectjrt:1.9.5"

                testImplementation "junit:junit:4.12"
            }
        """
        file("src/main/aspectj/StupidAspect.aj") << """
            import org.aspectj.lang.ProceedingJoinPoint;
            import org.aspectj.lang.annotation.Around;
            import org.aspectj.lang.annotation.Aspect;

            @Aspect
            public class StupidAspect {
                @Around("execution(* org.apache.http.util.Args.*(..))")
                public Object stupidAdvice(ProceedingJoinPoint joinPoint) {
                    throw new RuntimeException("Doing stupid things");
                }
            }
        """
        file("src/test/java/StupidAspectTest.aj") << """
            import org.junit.Test;
            import static org.junit.Assert.*;

            public class StupidAspectTest {
                @Test
                public void stupidAdvice() {
                    try {
                        org.apache.http.util.Args.check(true, "foo");
                        fail();
                    } catch (Exception e) {
                        assertTrue(e.getMessage().contains("stupid"));
                    }
                }
            }
        """

        when:
        def result = runner('check').forwardOutput().build()

        then:
        expectNoDeprecationWarnings(result)
    }
}
