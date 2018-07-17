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
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class ThirdPartyPluginsSmokeTest extends AbstractSmokeTest {

    @Rule final ReleasingPortAllocator portAllocator = new ReleasingPortAllocator()

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

        where:
        version << ["2.0.4"]
    }

    @Issue('https://github.com/asciidoctor/asciidoctor-gradle-plugin/releases')
    @Ignore
    def 'asciidoctor plugin'() {
        given:
        buildFile << """
            buildscript {
                ${jcenterRepository()}
                dependencies {
                    classpath "org.asciidoctor:asciidoctor-gradle-plugin:1.5.7"
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
                id "com.bmuschko.docker-java-application" version "3.3.6"
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
                id 'io.spring.dependency-management' version '1.0.5.RELEASE'
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
        result.output.contains('org.springframework:spring-core -> 4.0.3.RELEASE')
    }

    @Issue('https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-gradle-plugin')
    def 'spring boot plugin'() {
        given:
        buildFile << """
            plugins {
                id "org.springframework.boot" version "2.0.3.RELEASE"
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
                id 'io.spring.dependency-management' version '1.0.5.RELEASE' 
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

    @Issue('https://plugins.gradle.org/plugin/com.bmuschko.tomcat')
    def 'tomcat plugin'() {
        given:
        def httpPort = portAllocator.assignPort()
        def httpsPort = portAllocator.assignPort()
        def stopPort = portAllocator.assignPort()
        buildFile << """
            plugins {
                id "com.bmuschko.tomcat" version "2.5"
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

        expect:
        runner('integrationTest').build()
    }

    @Issue('https://plugins.gradle.org/plugin/org.gosu-lang.gosu')
    def 'gosu plugin'() { // Requires JDK 8 or later
        given:
        buildFile << """
            plugins {
                id 'org.gosu-lang.gosu' version '0.3.10'
            }

            ${mavenCentralRepository()}

            dependencies {
                compile group: 'org.gosu-lang.gosu', name: 'gosu-core-api', version: '1.14.9'
            }
            """.stripIndent()

        file('src/main/gosu/example/Foo.gs') << """
            package example

            public class Foo {

              function doSomething(arg : String) : String {
                return "Hello, got the argument '\${arg}'"
              }
            }
            """.stripIndent()


        when:
        def result = runner('build').build()

        then:
        result.task(':compileGosu').outcome == SUCCESS
    }

    @Issue('https://plugins.gradle.org/plugin/org.xtext.xtend')
    def 'xtend plugin'() {
        given:
        buildFile << """
            plugins {
                id "org.xtext.xtend" version "1.0.21"
            }

            ${mavenCentralRepository()}

            dependencies {
                compile 'org.eclipse.xtend:org.eclipse.xtend.lib:2.11.0'
            }
            """.stripIndent()

        file('src/main/java/HelloWorld.xtend') << """
            class HelloWorld {
                def static void main(String[] args) {
                    println("Hello World")
                }
            }
            """

        when:
        def result = runner('build').build()

        then:
        result.task(':generateXtext').outcome == SUCCESS
    }

    @Issue('https://plugins.gradle.org/plugin/org.ajoberstar.grgit')
    def 'org.ajoberstar.grgit plugin'() {
        given:
        GitFileRepository.init(testProjectDir.root)
        buildFile << """
            plugins {
                id "org.ajoberstar.grgit" version "2.2.1"
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
    }
}
