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
import org.junit.Rule
import spock.lang.Issue

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class ThirdPartyPluginsSmokeTest extends AbstractSmokeTest {

    @Rule final ReleasingPortAllocator portAllocator = new ReleasingPortAllocator()

    @Issue('https://plugins.gradle.org/plugin/com.github.johnrengelman.shadow')
    def 'shadow plugin'() {
        given:
        buildFile << """
            import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer

            plugins {
                id 'java' // or 'groovy' Must be explicitly applied
                id 'com.github.johnrengelman.shadow' version '2.0.1'
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
    }

    @Issue('https://github.com/asciidoctor/asciidoctor-gradle-plugin/releases')
    def 'asciidoctor plugin'() {
        given:
        buildFile << """
            buildscript {
                ${jcenterRepository()}
                dependencies {
                    classpath "org.asciidoctor:asciidoctor-gradle-plugin:1.5.6"
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
        runner('asciidoc').build()

        then:
        file('build/asciidoc').isDirectory()
    }

    @Issue('https://plugins.gradle.org/plugin/com.bmuschko.docker-java-application')
    def 'docker plugin'() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'application'
                id "com.bmuschko.docker-java-application" version "3.2.0"
            }

            mainClassName = 'org.gradle.JettyMain'

            docker {
                javaApplication {
                    baseImage = 'dockerfile/java:openjdk-7-jre'
                    port = 9090
                    tag = 'jettyapp:1.115'
                }
            }
            """.stripIndent()

        when:
        def result = runner(':dockerCopyDistResources').build()

        then:
        result.task(':dockerCopyDistResources').outcome == SUCCESS
    }

    @Issue('https://plugins.gradle.org/plugin/io.spring.dependency-management')
    def 'spring dependency management plugin'() {
        given:
        buildFile << """
            plugins {
                id 'java'
                id 'io.spring.dependency-management' version '1.0.3.RELEASE'
            }

            ${mavenCentralRepository()}

            dependencyManagement {
                dependencies {
                    dependency 'org.springframework:spring-core:4.0.3.RELEASE'
                    dependency group: 'commons-logging', name: 'commons-logging', version: '1.1.2'
                }
            }

            dependencies {
                compile 'org.springframework:spring-core'
            }
            """.stripIndent()

        when:
        def result = runner("dependencies", "--configuration", "compile").build()

        then:
        result.output.contains('org.springframework:spring-core: -> 4.0.3.RELEASE')
    }

    @Issue('https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-gradle-plugin/1.5.7.RELEASE')
    def 'spring boot plugin'() {
        given:
        buildFile << """
            buildscript {
                ${mavenCentralRepository()}
                dependencies {
                    classpath('org.springframework.boot:spring-boot-gradle-plugin:1.5.7.RELEASE')
                }
            }

            apply plugin: 'spring-boot'
        """.stripIndent()

        file('src/main/java/example/Application.java') << """
            package example;

            public class Application {
                public static void main(String[] args) {}
            }
        """.stripIndent()

        when:
        def result = runner('build').build()

        then:
        result.task(':findMainClass').outcome == SUCCESS
        result.task(':bootRepackage').outcome == SUCCESS
    }

    @Issue(["gradle/gradle#2480", "https://plugins.gradle.org/plugin/io.spring.dependency-management"])
    def "spring dependency management plugin and BOM"() {
        given:
        buildFile << """
            buildscript {    
                ${mavenCentralRepository()}
            }
            
            plugins { 
                id 'java'
                id 'io.spring.dependency-management' version '1.0.3.RELEASE' 
            }
            
            ${mavenCentralRepository()}
            
            dependencies {
                compile('org.springframework.boot:spring-boot-starter')
                testCompile('org.springframework.boot:spring-boot-starter-test')
            }
            
            dependencyManagement {
                imports { mavenBom("org.springframework.boot:spring-boot-dependencies:1.5.2.RELEASE") }
            }
            
            task resolveDependencies {
                doLast {
                    configurations.compile.files
                    configurations.testCompile.files
                }
            }
        """

        when:
        runner('resolveDependencies').build()

        then:
        noExceptionThrown()
    }

}
