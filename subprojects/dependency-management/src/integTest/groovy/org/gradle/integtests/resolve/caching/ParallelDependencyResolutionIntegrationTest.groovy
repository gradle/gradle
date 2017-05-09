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

package org.gradle.integtests.resolve.caching

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf

@IgnoreIf({ GradleContextualExecuter.parallel })
// no point, always runs in parallel
class ParallelDependencyResolutionIntegrationTest extends AbstractHttpDependencyResolutionTest {


    def setup() {
        executer.withArgument('--parallel')
        executer.withArgument('--max-workers=3') // needs to be set to the maximum number of expectConcurrentExecution() calls
        executer.withArgument('--info')

        executer.requireOwnGradleUserHomeDir()

    }

    def "dependency is only downloaded at most once per build using Maven"() {
        def module = mavenHttpRepo.module("com.acme", "dummy", "1.0-SNAPSHOT").publish()

        given:
        module.metaData.expectGet()
        ('a'..'z').each {
            settingsFile << "include '$it'\n"
            file("${it}/build.gradle") << """
                apply plugin: 'java-library'
                
                repositories {
                    maven {
                        url '${mavenHttpRepo.uri}'
                    }
                }

                dependencies {
                    implementation 'com.acme:dummy:1.0-SNAPSHOT'
                }

                task resolveDependencies {
                    doLast {
                        configurations.compileClasspath.resolve()
                    }
                }
            """
        }

        module.pom.expectGet()
        module.artifact.expectGet()

        when:
        run 'resolveDependencies'

        then:
        noExceptionThrown()
    }

    def "dependency is only downloaded at most once per build using Ivy"() {
        def module = ivyHttpRepo.module('com.acme', 'dummy', '1.0-SNAPSHOT').publish()

        given:
        ('a'..'z').each {
            settingsFile << "include '$it'\n"
            file("${it}/build.gradle") << """
                apply plugin: 'java-library'
                
                repositories {
                    ivy {
                        url '${ivyHttpRepo.uri}'
                    }
                }

                dependencies {
                    implementation 'com.acme:dummy:1.0-SNAPSHOT'
                }

                task resolveDependencies {
                    doLast {
                        configurations.compileClasspath.resolve()
                    }
                }
            """
        }

        module.ivy.expectGet()
        module.artifact.expectGet()

        when:
        run 'resolveDependencies'

        then:
        noExceptionThrown()
    }

}
