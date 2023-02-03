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

package org.gradle.integtests.resource.gcs.ivy

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.resolve.ivy.AbstractIvyRemoteRepoResolveIntegrationTest
import org.gradle.integtests.resource.gcs.fixtures.GcsServer
import org.gradle.test.fixtures.server.RepositoryServer
import org.junit.Rule

import static org.gradle.internal.resource.transport.gcp.gcs.GcsConnectionProperties.GCS_DISABLE_AUTH_PROPERTY
import static org.gradle.internal.resource.transport.gcp.gcs.GcsConnectionProperties.GCS_ENDPOINT_PROPERTY
import static org.gradle.internal.resource.transport.gcp.gcs.GcsConnectionProperties.GCS_SERVICE_PATH_PROPERTY

class IvyGcsRepoResolveIntegrationTest extends AbstractIvyRemoteRepoResolveIntegrationTest {

    @Rule
    GcsServer server = new GcsServer(temporaryFolder)

    @Override
    RepositoryServer getServer() {
        return server
    }

    protected ExecutionResult succeeds(String... tasks) {
        executer.withArgument("-D${GCS_ENDPOINT_PROPERTY}=${server.uri}")
        executer.withArgument("-D${GCS_SERVICE_PATH_PROPERTY}=/")
        executer.withArgument("-D${GCS_DISABLE_AUTH_PROPERTY}=true")
        result = executer.withTasks(*tasks).run()
    }

    @ToBeFixedForConfigurationCache(skip = ToBeFixedForConfigurationCache.Skip.FAILS_TO_CLEANUP)
    def "cannot add invalid authentication types for gcs repo"() {
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
        failure.assertHasCause("Authentication scheme 'auth'(BasicAuthentication) is not supported by protocol 'gcs'")
    }
}
