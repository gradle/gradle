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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.vcs.fixtures.GitFileRepository
import spock.lang.Issue

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Requires(UnitTestPreconditions.Jdk11OrLater)
class JGitPluginSmokeTest extends AbstractPluginValidatingSmokeTest {
    @Issue('https://plugins.gradle.org/plugin/org.ajoberstar.grgit')
    @ToBeFixedForConfigurationCache(because = "Gradle.buildFinished")
    def 'org.ajoberstar.grgit plugin'() {
        given:
        GitFileRepository.init(testProjectDir)
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
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'org.ajoberstar.grgit': Versions.of(TestedVersions.grgit)
        ]
    }
}
