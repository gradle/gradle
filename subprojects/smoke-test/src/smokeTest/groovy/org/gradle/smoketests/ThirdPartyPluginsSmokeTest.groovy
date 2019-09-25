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

import org.gradle.util.ports.ReleasingPortAllocator
import org.gradle.vcs.fixtures.GitFileRepository
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class ThirdPartyPluginsSmokeTest extends AbstractSmokeTest {

    @Rule
    final ReleasingPortAllocator portAllocator = new ReleasingPortAllocator()

    @Unroll
    @Issue('https://plugins.gradle.org/plugin/com.github.johnrengelman.shadow')
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
                "The compile configuration has been deprecated for dependency declaration. This will fail with an error in Gradle 7.0. Please use the implementation configuration instead."
            )
        }

        where:
        version << TestedVersions.shadow
    }

    @Issue('https://github.com/asciidoctor/asciidoctor-gradle-plugin/releases')
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
            "Type 'AsciidoctorTask': non-property method 'asGemPath()' should not be annotated with: @Optional, @InputDirectory. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.",
            "Property 'logDocuments' has redundant getters: 'getLogDocuments()' and 'isLogDocuments()'. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.",
            "Property 'separateOutputDirs' has redundant getters: 'getSeparateOutputDirs()' and 'isSeparateOutputDirs()'. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.",
        )
    }

    @Issue('https://github.com/asciidoctor/asciidoctor-gradle-plugin/releases')
    def 'asciidoctor plugin'() {
        given:
        buildFile << """
            plugins {
                id 'org.asciidoctor.convert' version '${TestedVersions.asciidoctor}'
            }
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
            "You are using one or more deprecated Asciidoctor task or plugins. These will be removed in a future release. To help you migrate we have compiled some tips for you based upon your current usage:",
            "  - 'org.asciidoctor.convert' is deprecated. When you have time please switch over to 'org.asciidoctor.jvm.convert'.",
            "Property 'logDocuments' is annotated with @Optional that is not allowed for @Console properties. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0.",
        )
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
                    tag = 'jettyapp:1.115'
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
            "Property 'classesJarScanningRequired' is private and annotated with @Internal. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0."
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
    def 'spotbugs plugin'() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'com.github.spotbugs' version '${TestedVersions.spotbugs}'
            }

            ${jcenterRepository()}

            """.stripIndent()

        file('src/main/java/example/Application.java') << """
            package example;

            public class Application {
                public static void main(String[] args) {}
            }
        """.stripIndent()


        when:
        def result = runner('check').build()

        then:
        file('build/reports/spotbugs').isDirectory()

        expectDeprecationWarnings(result,
            "Property 'showProgress' @Input properties with primitive type 'boolean' cannot be @Optional. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0."
        )
    }

    @Issue("https://github.com/gradle/gradle/issues/9897")
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
        expectDeprecationWarnings(result,
            "Property 'options.compilerArgumentProviders.errorprone\$0.name' is not annotated with an input or output annotation. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0."
        )
    }

}
