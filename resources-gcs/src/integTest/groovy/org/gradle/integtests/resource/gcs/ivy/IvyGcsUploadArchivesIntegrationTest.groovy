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

import org.gradle.api.publish.ivy.AbstractIvyRemoteLegacyPublishIntegrationTest
import org.gradle.integtests.resource.gcs.fixtures.GcsServer
import org.gradle.test.fixtures.server.RepositoryServer
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

import static org.gradle.internal.resource.transport.gcp.gcs.GcsConnectionProperties.*

@Requires(TestPrecondition.NOT_WINDOWS)
class IvyGcsUploadArchivesIntegrationTest extends AbstractIvyRemoteLegacyPublishIntegrationTest {
    @Rule
    public GcsServer server = new GcsServer(temporaryFolder)

    @Override
    RepositoryServer getServer() {
        return server
    }

    def setup() {
        executer.withArgument("-D${GCS_ENDPOINT_PROPERTY}=${server.uri}")
        executer.withArgument("-D${GCS_SERVICE_PATH_PROPERTY}=/")
        executer.withArgument("-D${GCS_DISABLE_AUTH_PROPERTY}=true")
    }
}
