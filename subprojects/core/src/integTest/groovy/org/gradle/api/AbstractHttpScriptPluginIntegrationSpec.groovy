/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.keystore.TestKeyStore
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.matchers.UserAgentMatcher
import org.gradle.util.GradleVersion

class AbstractHttpScriptPluginIntegrationSpec extends AbstractIntegrationSpec {
    @org.junit.Rule
    HttpServer server = new HttpServer()
    @org.junit.Rule
    TestResources resources = new TestResources(temporaryFolder)

    def setup() {
        settingsFile << "rootProject.name = 'project'"
        server.expectUserAgent(UserAgentMatcher.matchesNameAndVersion("Gradle", GradleVersion.current().getVersion()))
        server.start()
        executer.requireOwnGradleUserHomeDir()
    }

    protected void applyTrustStore() {
        def keyStore = TestKeyStore.init(resources.dir)
        keyStore.enableSslWithServerCert(server)
        keyStore.configureServerCert(executer)
    }

}
