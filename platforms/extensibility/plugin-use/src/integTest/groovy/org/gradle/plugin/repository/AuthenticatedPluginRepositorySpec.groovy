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

package org.gradle.plugin.repository

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.IvyHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpModule

import static org.gradle.test.fixtures.server.http.AuthScheme.BASIC
import static org.gradle.test.fixtures.server.http.AuthScheme.DIGEST
import static org.gradle.test.fixtures.server.http.AuthScheme.HIDE_UNAUTHORIZED
import static org.gradle.test.fixtures.server.http.AuthScheme.NTLM

@LeaksFileHandles
class AuthenticatedPluginRepositorySpec extends AbstractHttpDependencyResolutionTest {
    private static final String MAVEN = 'maven'
    private static final String IVY = 'ivy'
    private static final String USERNAME = 'username'
    private static final String PASSWORD = 'password'

    private PluginBuilder.PluginPublicationResults publishTestPlugin(String repoType) {
        def pluginBuilder = new PluginBuilder(testDirectory.file("plugin"))

        def message = "from plugin"
        def taskName = "pluginTask"

        pluginBuilder.addPluginWithPrintlnTask(taskName, message, "org.example.plugin")

        if (repoType == IVY) {
            return pluginBuilder.publishAs("org.example.plugin:plugin:1.0", ivyHttpRepo, executer)
        } else if (repoType == MAVEN) {
            return pluginBuilder.publishAs("org.example.plugin:plugin:1.0", mavenHttpRepo, executer)
        }
        return null
    }

    private def useCustomRepository(String repoType, String configuredAuthentication) {
        def repoUrl = 'Nothing'
        if (repoType == MAVEN) {
            repoUrl = mavenHttpRepo.uri
        } else if (repoType == IVY) {
            repoUrl = ivyHttpRepo.uri
        }
        settingsFile << """
          pluginManagement {
            repositories {
              ${repoType} {
                  url = "${repoUrl}"
                  credentials {
                      password = '${PASSWORD}'
                      username = '${USERNAME}'
                  }
                  ${configuredAuthentication}
              }
            }
          }
        """
    }

    def "can resolve plugin from Ivy repo with #authSchemeName and #serverAuthScheme"() {
        given:
        def pluginResults = publishTestPlugin(IVY)
        buildFile """
          plugins {
              id "org.example.plugin" version "1.0"
          }
        """

        and:
        useCustomRepository(IVY, configuredAuthentication)
        server.authenticationScheme = serverAuthScheme
        def pluginModule = (IvyHttpModule) pluginResults.pluginModule
        def markerModule = (IvyHttpModule) pluginResults.markerModules.get(0)

        expect:
        markerModule.ivy.expectGet(USERNAME, PASSWORD)
        markerModule.jar.expectGet(USERNAME, PASSWORD)
        pluginModule.ivy.expectGet(USERNAME, PASSWORD)
        pluginModule.jar.expectGet(USERNAME, PASSWORD)
        succeeds("pluginTask")
        output.contains("from plugin")
        server.authenticationAttempts.asList() == authenticationAttempts

        where:
        authSchemeName     | configuredAuthentication                                                      | serverAuthScheme  | authenticationAttempts
        'basic'            | 'authentication { auth(BasicAuthentication) }'                                | BASIC             | ['Basic']
        'digest'           | 'authentication { auth(DigestAuthentication) }'                               | DIGEST            | ['None', 'Digest']
        'default'          | ''                                                                            | BASIC             | ['None', 'Basic']
        'default'          | ''                                                                            | DIGEST            | ['None', 'Digest']
        'default'          | ''                                                                            | NTLM              | ['None', 'NTLM']
        'basic'            | 'authentication { auth(BasicAuthentication) }'                                | HIDE_UNAUTHORIZED | ['Basic']
        'basic and digest' | 'authentication { basic(BasicAuthentication)\ndigest(DigestAuthentication) }' | DIGEST            | ['Basic', 'Digest']
    }

    def "can resolve plugin from Maven repo with #authSchemeName and #serverAuthScheme"() {
        given:
        def pluginResults = publishTestPlugin(MAVEN)
        buildFile """
          plugins {
              id "org.example.plugin" version "1.0"
          }
        """

        and:
        useCustomRepository(MAVEN, configuredAuthentication)
        server.authenticationScheme = serverAuthScheme
        def pluginModule = (MavenHttpModule) pluginResults.pluginModule
        def markerModule = (MavenHttpModule) pluginResults.markerModules.get(0)

        expect:
        markerModule.pom.expectGet(USERNAME, PASSWORD)
        markerModule.artifact.expectGet(USERNAME, PASSWORD)
        pluginModule.pom.expectGet(USERNAME, PASSWORD)
        pluginModule.artifact.expectGet(USERNAME, PASSWORD)
        succeeds("pluginTask")
        output.contains("from plugin")
        server.authenticationAttempts.asList() == authenticationAttempts

        where:
        authSchemeName     | configuredAuthentication                                                      | serverAuthScheme  | authenticationAttempts
        'basic'            | 'authentication { auth(BasicAuthentication) }'                                | BASIC             | ['Basic']
        'digest'           | 'authentication { auth(DigestAuthentication) }'                               | DIGEST            | ['None', 'Digest']
        'default'          | ''                                                                            | BASIC             | ['None', 'Basic']
        'default'          | ''                                                                            | DIGEST            | ['None', 'Digest']
        'default'          | ''                                                                            | NTLM              | ['None', 'NTLM']
        'basic'            | 'authentication { auth(BasicAuthentication) }'                                | HIDE_UNAUTHORIZED | ['Basic']
        'basic and digest' | 'authentication { basic(BasicAuthentication)\ndigest(DigestAuthentication) }' | DIGEST            | ['Basic', 'Digest']
    }
}
