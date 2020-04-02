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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue
import spock.lang.Unroll

class NebulaPluginsSmokeTest extends AbstractSmokeTest {

    @Issue('https://plugins.gradle.org/plugin/nebula.dependency-recommender')
    @ToBeFixedForInstantExecution
    def 'nebula recommender plugin'() {
        when:
        buildFile << """
            plugins {
                id "java"
                id "nebula.dependency-recommender" version "${TestedVersions.nebulaDependencyRecommender}"
            }

            ${jcenterRepository()}

            dependencyRecommendations {
                mavenBom module: 'netflix:platform:latest.release'
            }

            dependencies {
                compile 'com.google.guava:guava' // no version, version is recommended
                compile 'commons-lang:commons-lang:2.6' // I know what I want, don't recommend
            }
            """

        then:
        runner('build').build()
    }

    @Issue('https://plugins.gradle.org/plugin/nebula.plugin-plugin')
    @ToBeFixedForInstantExecution
    def 'nebula plugin plugin'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.plugin-plugin' version '${TestedVersions.nebulaPluginPlugin}'
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
        runner('groovydoc').build()
    }

    @Issue('https://plugins.gradle.org/plugin/nebula.lint')
    @ToBeFixedForInstantExecution
    def 'nebula lint plugin'() {
        given:
        buildFile << """
            buildscript {
                ${jcenterRepository()}
            }

            plugins {
                id "nebula.lint" version "${TestedVersions.nebulaLint}"
            }

            apply plugin: 'java'

            gradleLint.rules = ['dependency-parentheses']

            dependencies {
                testCompile('junit:junit:4.7')
            }
        """.stripIndent()

        when:
        def result = runner('autoLintGradle').build()

        then:
        int numOfRepoBlockLines = 14 + jcenterRepository().readLines().size()
        result.output.contains("parentheses are unnecessary for dependencies")
        result.output.contains("warning   dependency-parentheses")
        result.output.contains("build.gradle:$numOfRepoBlockLines")
        result.output.contains("testCompile('junit:junit:4.7')")
        buildFile.text.contains("testCompile('junit:junit:4.7')")

        when:
        result = runner('fixGradleLint').build()

        then:
        result.output.contains("""fixed          dependency-parentheses             parentheses are unnecessary for dependencies
build.gradle:$numOfRepoBlockLines
testCompile('junit:junit:4.7')""")
        buildFile.text.contains("testCompile 'junit:junit:4.7'")
    }

    @Issue('https://plugins.gradle.org/plugin/nebula.dependency-lock')
    @ToBeFixedForInstantExecution(because = ":buildEnvironment")
    def 'nebula dependency lock plugin'() {
        when:
        buildFile << """
            plugins {
                id "nebula.dependency-lock" version "${TestedVersions.nebulaDependencyLock.latest()}"
            }
        """.stripIndent()

        then:
        runner('buildEnvironment', 'generateLock').build()
    }

    @Issue("gradle/gradle#3798")
    @Unroll
    @ToBeFixedForInstantExecution
    def "nebula dependency lock plugin version #version binary compatibility"() {
        when:
        buildFile << """
            plugins {
                id 'java-library'
                id 'nebula.dependency-lock' version '$version'
            }

            ${jcenterRepository()}

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

    @Issue('https://plugins.gradle.org/plugin/nebula.resolution-rules')
    @Requires(TestPrecondition.JDK11_OR_EARLIER)
    @ToBeFixedForInstantExecution
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
                id 'nebula.resolution-rules' version '${TestedVersions.nebulaResolutionRules}'
            }

            ${jcenterRepository()}

            dependencies {
                resolutionRules files('rules.json')

                // Need a non-empty configuration to trigger the plugin
                api 'org.apache.commons:commons-math3:3.6.1'
            }
        """.stripIndent()

        then:
        runner('dependencies').build()
    }
}
