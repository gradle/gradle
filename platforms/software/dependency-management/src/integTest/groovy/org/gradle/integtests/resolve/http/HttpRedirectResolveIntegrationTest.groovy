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


class HttpRedirectResolveIntegrationTest extends AbstractRedirectResolveIntegrationTest {
    @Override
    String getFrontServerBaseUrl() {
        "http://localhost:${server.port}"
    }

    @Override
    boolean defaultAllowInsecureProtocol() {
        return true
    }

    void beforeServerStart() {
        // No-op
    }

    def "fails to resolves module artifacts via HTTP redirect"() {
        given:
        buildFile << configurationWithIvyDependencyAndExpectedArtifact('group:projectA:1.0', 'projectA-1.0.jar', false)

        when:
        server.forbidGetRedirected('/repo/group/projectA/1.0/ivy-1.0.xml', "${backingServer.uri}/redirected/group/projectA/1.0/ivy-1.0.xml")
        backingServer.forbidGet('/redirected/group/projectA/1.0/ivy-1.0.xml', module.ivyFile)

        then:
        fails('listJars')
    }
}
