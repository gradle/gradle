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

package org.gradle.integtests

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

/**
 * Tests for classloading related bugs build scripts.
 */
class ScriptClassloadingIntegrationTest extends AbstractIntegrationSpec {

    @Issue(['GRADLE-3526', 'GRADLE-3553'])
    @NotYetImplemented
    def 'apply the same script file causing different buildscript paths in different projects'() {
        given:
        multiProjectBuild('root', ['project1', 'project2']) {
            file('script.gradle') << """
                buildscript {
                    File searchDir = project.projectDir
                    def version = new File(searchDir, 'version.txt').text
                    repositories {
                        mavenCentral()
                    }
                    dependencies {
                        // Dynamically changing the classpath here surfaces problems with the ClassLoaderCache
                        classpath "org.apache.commons:commons-lang3:\${version}"
                    }
                }

                task doStringOp {
                    doLast {
                        println org.apache.commons.lang3.StringUtils.join('Hello', 'world')
                    }
                }
            """.stripIndent()

            file('project1/build.gradle') << """
                apply from: '${file('script.gradle').absolutePath}'
            """.stripIndent()
            file('project1/version.txt') << '3.4'

            file('project2/build.gradle') << """
                apply from: '${file('script.gradle').absolutePath}'
            """.stripIndent()
            file('project2/version.txt') << '3.3'
        }

        executer.requireOwnGradleUserHomeDir()

        expect:
        succeeds('doStringOp')
        // The problem only surfaces on the second run when we start with a clean Gradle user home
        succeeds('doStringOp')
    }
}
