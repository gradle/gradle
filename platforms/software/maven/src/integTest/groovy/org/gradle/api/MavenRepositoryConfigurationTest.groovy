/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Ignore

class MavenRepositoryConfigurationTest extends AbstractIntegrationSpec {

    def "supports groovy syntax with #hint"() {
        given:
        buildFile << """
            repositories {
                $declaration
            }
        """
        expect:
        succeeds 'build'

        where:
        hint              | declaration
        "uri"             | """maven { url = uri("https://some.example.com") }"""
        "string"          | """maven { url = "https://some.example.com" }"""
        "file"            | """maven { url = file("./maven-repo") }"""
        "layout dir"      | """maven { url = layout.buildDirectory.dir("maven-repo") }"""
        "uri provider"    | """maven { url = provider { uri("https://some.example.com") } }"""
        "string provider" | """maven { url = provider { "https://some.example.com" } }"""
        "file provider"   | """maven { url = provider { file("./maven-repo") } }"""
    }

    // TODO: Remove in 9.0
    @Ignore("can't deprecate it yet since it's used in our smoke-tested plugins")
    def "complaints about deprecated groovy syntax with #hint"() {
        given:
        buildFile << """
            repositories {
                $declaration
            }
        """
        expect:
        executer.expectDeprecationWarningWithPattern("The MavenArtifactRepository.setUrl\\(Object\\) configuration with `url Object` syntax method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use getUrl property instead.*")
        succeeds 'build'

        where:
        hint                    | declaration
        "uri string with space" | """maven { url "https://some.example.com" }"""
        "uri with space"        | """maven { url uri("https://some.example.com") }"""
        "file with space"       | """maven { url file("./maven-repo") }"""
    }

    def "supports kotlin syntax with #hint"() {
        given:
        buildKotlinFile << """
            repositories {
                $declaration
            }
        """
        expect:
        succeeds 'build'

        where:
        hint              | declaration
        "uri"             | """maven { url = uri("https://some.example.com") }"""
        "string"          | """maven { url = "https://some.example.com" }"""
        "file"            | """maven { url = file("./maven-repo") }"""
        "layout dir"      | """maven { url = layout.buildDirectory.dir("maven-repo") }"""
        "uri provider"    | """maven { url = provider { uri("https://some.example.com") } }"""
        "string provider" | """maven { url = provider { "https://some.example.com" } }"""
        "file provider"   | """maven { url = provider { file("./maven-repo") } }"""
    }

    def "complaints about file paths passed as strings in kotlin"() {
        given:
        buildKotlinFile << """
            plugins {
                `java-library`
            }

            repositories {
                maven { url = "dummy" }
            }

            dependencies {
                implementation("org.gradle:gradle-core:1.0")
            }
        """
        expect:
        fails 'dependencies'

        and:
        failureHasCause("Repository URL must have a scheme: 'dummy'. If you are using a local repository, please use 'file()' or derive it from project.layout.")
    }

    def "complaints about file paths passed as strings in groovy"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            repositories {
                maven { url = "dummy" }
            }

            dependencies {
                implementation("org.gradle:gradle-core:1.0")
            }
        """
        expect:
        fails 'dependencies'
        and:
        failureHasCause("Repository URL must have a scheme: 'dummy'. If you are using a local repository, please use 'file()' or derive it from project.layout.")
    }
}
