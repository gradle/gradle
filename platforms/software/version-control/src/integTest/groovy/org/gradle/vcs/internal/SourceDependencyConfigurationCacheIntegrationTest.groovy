/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.vcs.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import groovy.test.NotYetImplemented
import org.gradle.vcs.fixtures.GitFileRepository
import org.gradle.vcs.git.GitVersionControlSpec
import org.junit.Rule
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/13506")
@Requires(value = TestExecutionPreconditions.NotConfigCached, reason = "handles CC explicitly")
class SourceDependencyConfigurationCacheIntegrationTest extends AbstractIntegrationSpec implements SourceDependencies {

    @Rule
    GitFileRepository repo = new GitFileRepository('dep', temporaryFolder.getTestDirectory())

    BuildTestFile depProject

    def configurationCache = newConfigurationCacheFixture()

    @Override
    void setupExecuter() {
        super.setupExecuter()
        executer.withConfigurationCacheEnabled()
    }

    def setup() {
        buildFile << """
            apply plugin: 'java'
            group = 'org.gradle'
            version = '2.0'

            dependencies {
                implementation "org.test:dep:latest.integration"
            }
        """
        file("src/main/java/Main.java") << """
            public class Main {
                Dep dep = null;
            }
        """
        buildTestFixture.withBuildInSubDir()
        depProject = singleProjectBuild("dep") {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    group = 'org.test'
                }
            """
            file("src/main/java/Dep.java") << "public class Dep {}"
        }
        repo.commit('initial')

        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:dep") {
                        from(${GitVersionControlSpec.name}) {
                            url = uri("${repo.url}")
                        }
                    }
                }
            }
        """
    }

    def "stores configuration cache entry for build using a source dependency"() {
        when:
        succeeds("assemble")

        then:
        configurationCache.assertStateStored()
        result.assertTaskExecuted(":dep:jar")
        result.assertTaskExecuted(":compileJava")
    }

    def "reuses configuration cache entry on second invocation"() {
        when:
        succeeds("assemble")

        then:
        configurationCache.assertStateStored()

        when:
        succeeds("assemble")

        then:
        configurationCache.assertStateLoaded()
    }

    def "invalidates configuration cache when vcsMappings block changes"() {
        when:
        succeeds("assemble")

        then:
        configurationCache.assertStateStored()

        when:
        // Change the mapping rule — adding an unrelated mapping should still invalidate
        // because the settings script is part of the CC fingerprint.
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:other") {
                        from(${GitVersionControlSpec.name}) {
                            url = uri("does-not-exist")
                        }
                    }
                }
            }
        """
        succeeds("assemble")

        then:
        configurationCache.assertStateStored()
    }

    @NotYetImplemented
    def "invalidates configuration cache when source dependency upstream commit changes"() {
        when:
        succeeds("assemble")

        then:
        configurationCache.assertStateStored()

        when:
        // Add a new commit to the source-dep repo. With `latest.integration` resolving to the
        // branch HEAD, a fresh run would pick up the new commit; CC must invalidate.
        depProject.file("src/main/java/Dep.java").text = "public class Dep { int extra; }"
        repo.commit('updated')
        succeeds("assemble")

        then:
        configurationCache.assertStateStored()
    }
}
