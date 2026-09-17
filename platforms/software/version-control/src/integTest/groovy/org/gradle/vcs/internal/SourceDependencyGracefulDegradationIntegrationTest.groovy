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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.gradle.vcs.fixtures.GitFileRepository
import org.junit.Rule
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/36610")
@Requires(value = TestExecutionPreconditions.NotConfigCached, reason = "handles CC explicitly")
class SourceDependencyGracefulDegradationIntegrationTest extends AbstractIntegrationSpec implements SourceDependencies {

    @Rule
    GitFileRepository repo = new GitFileRepository('dep', temporaryFolder.getTestDirectory())

    def configurationCache = newConfigurationCacheFixture()

    @Override
    void setupExecuter() {
        super.setupExecuter()
        executer.withConfigurationCacheEnabled()
    }

    def setup() {
        settingsFile << """
            rootProject.name = 'consumer'
            sourceControl.vcsMappings.withModule("org.test:dep") {
                from(GitVersionControlSpec) {
                    url = uri('${repo.url}')
                }
            }
        """

        repo.file("settings.gradle") << """
            rootProject.name = 'dep'
            gradle.rootProject {
                configurations.create('default')
                group = 'org.test'
                version = '1.0'
            }
        """
        repo.commit('initial')
        repo.createLightWeightTag('1.0')
    }

    def "resolves a source dependency at execution time when the configuration cache gracefully degrades"() {
        given:
        buildFile << """
            abstract class ResolveTask extends DefaultTask {
                @javax.inject.Inject
                abstract org.gradle.api.internal.ConfigurationCacheDegradationController getDegradationController()
            }

            configurations {
                compile
            }
            dependencies {
                compile 'org.test:dep:1.0'
            }
            tasks.register('resolve', ResolveTask) { task ->
                // Opt this task into graceful configuration-cache degradation. This keeps
                // the configuration-cache fingerprint controller in its 'Paused' state and
                // permits execution-time project access. Resolving the source dependency
                // then configures its build while the controller is 'Paused', which is the
                // scenario that must degrade gracefully rather than throw.
                degradationController.requireConfigurationCacheDegradation(task, provider { "Resolves a source dependency at execution time" })
                doLast {
                    project.configurations.compile.each { }
                }
            }
        """

        expect:
        succeeds('resolve')
    }
}
