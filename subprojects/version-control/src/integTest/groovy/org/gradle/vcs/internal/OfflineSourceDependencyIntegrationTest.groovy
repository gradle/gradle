/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.vcs.fixtures.GitHttpRepository
import org.junit.Rule

class OfflineSourceDependencyIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    BlockingHttpServer httpServer = new BlockingHttpServer()
    @Rule
    GitHttpRepository repo = new GitHttpRepository(httpServer, 'dep', temporaryFolder.getTestDirectory())

    def setup() {
        httpServer.start()
        settingsFile << """
            rootProject.name = 'consumer'
            gradle.rootProject {
                configurations {
                    compile
                }
                dependencies {
                    compile 'test:test:1.2'
                }
                tasks.register('resolve') {
                    inputs.files configurations.compile
                    doLast { configurations.compile.each { } }
                }
            }
            sourceControl.vcsMappings.withModule('test:test') {
                from(GitVersionControlSpec) {
                    url = uri('${repo.url}')
                }
            }
        """

        repo.file('settings.gradle') << """
            rootProject.name = 'test'
            gradle.rootProject {
                group = 'test'
                configurations {
                    create('default')
                }
            }
        """
        repo.commit('initial version')
        repo.createLightWeightTag('1.2')
    }

    @ToBeFixedForConfigurationCache
    def "uses previous checkout when offline"() {
        given:
        repo.expectListVersions()
        repo.expectCloneSomething()
        succeeds('resolve')

        when:
        executer.withArgument('--offline')
        succeeds('resolve')

        then:
        noExceptionThrown()
    }

    def "fails when previous checkout is not available"() {
        when:
        executer.withArgument('--offline')
        fails('resolve')

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':resolve'.")
        failure.assertHasCause("Could not resolve all task dependencies for configuration ':compile'.")
        failure.assertHasCause("""Cannot resolve test:test:1.2 from Git repository at ${repo.url} in offline mode.
Required by:
    project :""")
    }
}
