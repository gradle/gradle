/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.resource.s3.ivy

import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.resolve.ivy.AbstractIvyRemoteRepoResolveIntegrationTest
import org.gradle.integtests.resource.s3.fixtures.S3Server
import org.gradle.test.fixtures.server.RepositoryServer
import org.junit.Rule

class IvyS3RepoResolveIntegrationTest extends AbstractIvyRemoteRepoResolveIntegrationTest {

    @Rule
    final S3Server server = new S3Server(temporaryFolder)

    @Override
    RepositoryServer getServer() {
        return server
    }

    protected ExecutionResult succeeds(String... tasks) {
        executer.withArgument("-Dorg.gradle.s3.endpoint=${server.uri}")
        executer.withArgument("-Dorg.gradle.s3.maxErrorRetry=0")
        result = executer.withTasks(*tasks).run()
    }

    def "cannot add invalid authentication types for s3 repo"() {
        given:
        def remoteIvyRepo = server.getRemoteIvyRepo()
        def module = remoteIvyRepo.module('org.group.name', 'projectA', '1.2')
        module.publish()

        and:
        buildFile << """
            repositories {
                ivy {
                    url "${remoteIvyRepo.uri}"
                    authentication {
                        auth(BasicAuthentication)
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

        expect:
        fails 'retrieve'
        and:
        failure.assertHasCause("Authentication scheme 'auth'(BasicAuthentication) is not supported by protocol 's3'")
    }
}
