/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.resolve.ivy

import org.gradle.test.fixtures.server.RepositoryServer
import org.gradle.test.fixtures.server.http.RepositoryHttpServer
import org.junit.Rule

class IvyHttpRepoResolveIntegrationTest extends AbstractIvyRemoteRepoResolveIntegrationTest {

    @Rule
    final RepositoryHttpServer server = new RepositoryHttpServer(temporaryFolder)

    @Override
    RepositoryServer getServer() {
        return server
    }

    void "fails when configured with AwsCredentials"() {
        given:
        def remoteIvyRepo = server.remoteIvyRepo
        def module = remoteIvyRepo.module('org.group.name', 'projectA', '1.2')
        module.publish()

        and:
        buildFile << """
            repositories {
                ivy {
                    url "${remoteIvyRepo.uri}"
                    credentials(AwsCredentials) {
                        accessKey "someKey"
                        secretKey "someSecret"
                    }
                }
            }
            configurations { compile }
            dependencies { compile 'org.group.name:projectA:1.2' }
            task retrieve(type: Sync) {
                from configurations.compile
                into 'libs'
            }
        """

        when:

        fails 'retrieve'
        then:
        failure.assertHasDescription("Could not resolve all dependencies for configuration ':compile'.")
            .assertHasCause('Credentials must be an instance of: org.gradle.api.artifacts.repositories.PasswordCredentials')
    }
}
