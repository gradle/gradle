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

package org.gradle.api.publish.ivy

import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.server.http.RepositoryHttpServer
import org.junit.Rule

class IvyHttpsLegacyPublishIntegrationTest extends AbstractIvyRemoteLegacyPublishIntegrationTest {
    TestKeyStore keyStore
    @Rule RepositoryHttpServer server = new RepositoryHttpServer(temporaryFolder)

    def setup() {
        keyStore = TestKeyStore.init(file("keystore"))
        keyStore.enableSslWithServerCert(server)
    }

    @Override
    protected ExecutionResult run(String... tasks) {
        keyStore.configureServerCert(executer)
        executer.withStackTraceChecksDisabled() // Jetty logs stuff to console
        return super.run(tasks)
    }

    @Override
    protected ExecutionFailure fails(String... tasks) {
        keyStore.configureServerCert(executer)
        executer.withStackTraceChecksDisabled() // Jetty logs stuff to console
        return super.fails(tasks)
    }
}
