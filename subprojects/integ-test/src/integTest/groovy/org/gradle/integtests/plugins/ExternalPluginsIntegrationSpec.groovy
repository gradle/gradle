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

package org.gradle.integtests.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ExternalPluginsIntegrationSpec extends AbstractIntegrationSpec {

    def 'shadow plugin'() {
        when:
        buildScript """
            import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer

            plugins {
              id 'java' // or 'groovy' Must be explicitly applied
              id 'com.github.johnrengelman.shadow' version '1.2.3'
            }

            repositories {
               jcenter()
            }

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

        then:
        succeeds 'shadowJar'
    }

    def 'kotlin plugin'() {
        when:
        def kotlinVersion = '1.0.2'
        buildScript """
            buildscript {
               ext.kotlin_version = '$kotlinVersion'

               repositories {
                 mavenCentral()
               }

               dependencies {
                 classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion"
               }
            }

            apply plugin: 'kotlin'

            repositories {
               mavenCentral()
            }

            dependencies {
              compile "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion"
            }
        """

        file('src/main/kotlin/pkg/HelloWorld.kt') << """
        package pkg

        fun getGreeting(): String {
            val words = mutableListOf<String>()
            words.add("Hello,")
            words.add("world!")

            return words.joinToString(separator = " ")
        }

        fun main(args: Array<String>) {
            println(getGreeting())
        }
        """

        then:
        executer.expectDeprecationWarning().withStackTraceChecksDisabled()
        succeeds 'build'
    }

    def 'asciidoctor plugin'() {
        given:
        buildScript """
            buildscript {
                repositories {
                    jcenter()
                }

                dependencies {
                    classpath 'org.asciidoctor:asciidoctor-gradle-plugin:1.5.3'
                }
            }

            apply plugin: 'org.asciidoctor.convert'
            """.stripIndent()

        file('src/docs/asciidoc/test.adoc') << """
            = Line Break Doc Title
            :hardbreaks:

            Rubies are red,
            Topazes are blue.
            """

        expect:
        succeeds 'asciidoc'
        file('build/asciidoc').isDirectory()
    }

    def 'docker plugin'() {
        given:
        buildScript """
            buildscript {
                repositories {
                    jcenter()
                }

                dependencies {
                    classpath 'com.bmuschko:gradle-docker-plugin:2.6.8'
                }
            }

            apply plugin: 'java'
            apply plugin: 'application'
            apply plugin: 'com.bmuschko.docker-java-application'

            mainClassName = 'org.gradle.JettyMain'

            docker {
                javaApplication {
                    baseImage = 'dockerfile/java:openjdk-7-jre'
                    port = 9090
                    tag = 'jettyapp:1.115'
                }
            }
            """.stripIndent()

        expect:
        succeeds 'dockerCopyDistResources'
    }
}
