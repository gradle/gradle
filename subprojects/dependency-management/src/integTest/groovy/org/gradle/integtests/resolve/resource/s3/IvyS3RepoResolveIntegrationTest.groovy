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

package org.gradle.integtests.resolve.resource.s3
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.resolve.ivy.AbstractIvyRemoteRepoResolveIntegrationTest
import org.gradle.test.fixtures.server.RepositoryServer
import org.gradle.test.fixtures.server.s3.S3StubServer
import org.gradle.test.fixtures.server.s3.S3StubSupport
import org.junit.Rule

class IvyS3RepoResolveIntegrationTest extends AbstractIvyRemoteRepoResolveIntegrationTest {

    @Rule
    final S3StubServer server = new S3StubServer(this)
    final S3StubSupport s3StubSupport = new S3StubSupport(server)

    @Override
    RepositoryServer getServer() {
        return server
    }

    protected ExecutionResult succeeds(String... tasks) {
        executer.withArgument("-Dorg.gradle.s3.endpoint=${s3StubSupport.endpoint.toString()}")
        result = executer.withTasks(*tasks).run()
    }
}
