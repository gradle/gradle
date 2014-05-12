/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugin.resolve.portal.internal

import org.gradle.api.GradleException
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.api.internal.externalresource.transport.ExternalResourceRepository
import org.gradle.api.internal.externalresource.transport.http.HttpResponseResource
import org.gradle.plugin.resolve.internal.PluginRequest
import spock.lang.Specification

class PluginPortalClientTest extends Specification {
    private transportFactory = Mock(RepositoryTransportFactory)
    private client = new PluginPortalClient(transportFactory)
    private request = Stub(PluginRequest)

    def "plugin metadata query uses correct url scheme"() {
        when:
        client.queryPluginMetadata(request, "http://plugin.portal")

        then:
        1 * transportFactory.createTransport("http", _, _) >> stubTransport(200)

        when:
        client.queryPluginMetadata(request, "https://plugin.portal")

        then:
        1 * transportFactory.createTransport("https", _, _) >> stubTransport(200)
    }

    def "plugin metadata query barks if portal returns a status code other than 200"() {
        when:
        client.queryPluginMetadata(request, "http://plugin.portal")

        then:
        1 * transportFactory.createTransport(*_) >> stubTransport(404)
        def e = thrown(GradleException)
        e.message == "Plugin portal returned HTTP status code: 404"
    }

    private stubTransport(int statusCode) {
        Stub(RepositoryTransport) {
            getRepository() >> Stub(ExternalResourceRepository) {
                getResource(_) >> Stub(HttpResponseResource) {
                    getStatusCode() >> statusCode
                    withContent(_) >> Stub(PluginUseMetaData)
                }
            }
        }
    }
}