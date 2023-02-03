/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.resolve.http

import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.junit.Rule

class HttpsToHttpsRedirectResolveIntegrationTest extends AbstractRedirectResolveIntegrationTest {

    @Rule TestResources resources = new TestResources(temporaryFolder)
    TestKeyStore keyStore

    @Override
    String getFrontServerBaseUrl() {
        "https://localhost:${server.sslPort}"
    }

    @Override
    boolean defaultAllowInsecureProtocol() {
        return false
    }

    void beforeServerStart() {
        keyStore = TestKeyStore.init(resources.dir)
        keyStore.enableSslWithServerCert(server)
        keyStore.enableSslWithServerCert(backingServer)
        keyStore.configureServerCert(executer)
    }
}
