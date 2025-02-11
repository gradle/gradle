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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.GradleVersion
import spock.lang.Issue

class NebulaPluginsSmokeTest extends AbstractPluginValidatingSmokeTest implements ValidationMessageChecker {

    @Issue('https://plugins.gradle.org/plugin/com.netflix.nebula.dependency-recommender')
    @ToBeFixedForConfigurationCache
    def 'nebula recommender plugin'() {
        when:
        buildFile << """
            plugins {
                id "java"
                id "com.netflix.nebula.dependency-recommender" version "${TestedVersions.nebulaDependencyRecommender}"
            }

            ${mavenCentralRepository()}

            dependencyRecommendations {
                mavenBom module: 'netflix:platform:latest.release'
            }

            dependencies {
                implementation 'com.google.guava:guava' // no version, version is recommended
                implementation 'commons-lang:commons-lang:2.6' // I know what I want, don't recommend
            }
            """

        then:
        runner('build')
            .expectDeprecationWarning(
                "Declaring an 'is-' property with a Boolean type has been deprecated. Starting with Gradle 9.0, this property will be ignored by Gradle. The combination of method name and return type is not consistent with Java Bean property rules and will become unsupported in future versions of Groovy. Add a method named 'getStrictMode' with the same behavior and mark the old one with @Deprecated, or change the type of 'netflix.nebula.dependency.recommender.provider.RecommendationProviderContainer.isStrictMode' (and the setter) to 'boolean'. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#groovy_boolean_properties",
                "https://github.com/nebula-plugins/nebula-dependency-recommender-plugin/issues/127"
            ).build()
    }

    @Issue('https://plugins.gradle.org/plugin/com.netflix.nebula.plugin-plugin')
    def 'nebula plugin plugin'() {
        when:
        buildFile << """
            plugins {
                id 'com.netflix.nebula.plugin-plugin' version '${TestedVersions.nebulaPluginPlugin}'
            }
        """

        file("src/main/groovy/pkg/Thing.java") << """
            package pkg;

            import java.util.ArrayList;
            import java.util.List;

            public class Thing {
                private List<String> firstOrderDepsWithoutVersions = new ArrayList<>();
            }
        """

        then:
        runner('groovydoc', '-s').build()
    }

    @Issue('https://plugins.gradle.org/plugin/nebula.lint')
    def 'nebula lint plugin'() {
        given:
        buildFile << """
            buildscript {
                ${mavenCentralRepository()}
            }

            plugins {
                id "com.netflix.nebula.lint" version "${TestedVersions.nebulaLint}"
            }

            ${mavenCentralRepository()}

            apply plugin: 'java'

            gradleLint.rules = ['dependency-parentheses']

            dependencies {
                testImplementation('junit:junit:4.7')
            }
        """.stripIndent()

        when:
        def result = runner('autoLintGradle').build()

        then:
        int numOfRepoBlockLines = 15 + 2 * mavenCentralRepository().readLines().size()
        result.output.contains("parentheses are unnecessary for dependencies")
        result.output.contains("warning   dependency-parentheses")
        result.output.contains("build.gradle:$numOfRepoBlockLines")
        result.output.contains("testImplementation('junit:junit:4.7')")
        buildFile.text.contains("testImplementation('junit:junit:4.7')")

        when:
        result = runner('fixGradleLint').build()

        then:
        result.output.contains("""fixed          dependency-parentheses             parentheses are unnecessary for dependencies
build.gradle:$numOfRepoBlockLines
testImplementation('junit:junit:4.7')""")
        buildFile.text.contains("testImplementation 'junit:junit:4.7'")
    }

    @Issue('https://plugins.gradle.org/plugin/com.netflix.nebula.dependency-lock')
    def 'nebula dependency lock plugin #nebulaDepLockVersion'() {
        when:
        buildFile << """
            plugins {
                id "com.netflix.nebula.dependency-lock" version "$nebulaDepLockVersion"
            }
        """.stripIndent()

        then:
        runner('buildEnvironment', 'generateLock').build()

        where:
        nebulaDepLockVersion << TestedVersions.nebulaDependencyLock.versions
    }

    @Issue("gradle/gradle#3798")
    @ToBeFixedForConfigurationCache(because = "Gradle.buildFinished and TaskExecutionGraph.addTaskExecutionListener")
    def "nebula dependency lock plugin version #version binary compatibility"() {
        when:
        buildFile << """
            plugins {
                id 'java-library'
                id 'com.netflix.nebula.dependency-lock' version '$version'
            }

            ${mavenCentralRepository()}

            dependencies {
                api 'org.apache.commons:commons-math3:3.6.1'
            }

            task resolve {
                doFirst {
                    configurations.compileClasspath.each { println it.name }
                }
            }
        """
        file('dependencies.lock') << '''{
    "compileClasspath": {
        "org.apache.commons:commons-math3": {
            "locked": "3.6.1",
            "requested": "3.6.1"
        }
    },
    "default": {
        "org.apache.commons:commons-math3": {
            "locked": "3.6.1",
            "requested": "3.6.1"
        }
    },
    "runtimeClasspath": {
        "org.apache.commons:commons-math3": {
            "locked": "3.6.1",
            "requested": "3.6.1"
        }
    },
    "testCompileClasspath": {
        "org.apache.commons:commons-math3": {
            "locked": "3.6.1",
            "requested": "3.6.1"
        }
    },
    "testRuntimeClasspath": {
        "org.apache.commons:commons-math3": {
            "locked": "3.6.1",
            "requested": "3.6.1"
        }
    }
}'''

        then:
        runner('dependencies').build()
        runner('generateLock').build()
        runner('resolve').build()

        where:
        version << TestedVersions.nebulaDependencyLock
    }

    @Issue('https://plugins.gradle.org/plugin/com.netflix.nebula.resolution-rules')
    @Requires(UnitTestPreconditions.Jdk11OrEarlier)
    def 'nebula resolution rules plugin'() {
        when:
        file('rules.json') << """
            {
                "replace" : [
                    {
                        "module" : "asm:asm",
                        "with" : "org.ow2.asm:asm",
                        "reason" : "The asm group id changed for 4.0 and later",
                        "author" : "Example Person <person@example.org>",
                        "date" : "2015-10-07T20:21:20.368Z"
                    }
                ]
            }
"""
        buildFile << """
            plugins {
                id 'java-library'
                id 'com.netflix.nebula.resolution-rules' version '${TestedVersions.nebulaResolutionRules}'
            }

            ${mavenCentralRepository()}

            dependencies {
                resolutionRules files('rules.json')

                // Need a non-empty configuration to trigger the plugin
                api 'org.apache.commons:commons-math3:3.6.1'
            }
        """.stripIndent()

        then:
        runner('dependencies').build()
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'com.netflix.nebula.dependency-recommender': Versions.of(TestedVersions.nebulaDependencyRecommender),
            'com.netflix.nebula.plugin-plugin': Versions.of(TestedVersions.nebulaPluginPlugin),
            'com.netflix.nebula.lint': Versions.of(TestedVersions.nebulaLint),
            'com.netflix.nebula.dependency-lock': TestedVersions.nebulaDependencyLock,
            'com.netflix.nebula.resolution-rules': Versions.of(TestedVersions.nebulaResolutionRules)
        ]
    }
}


