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

package org.gradle.plugin.use.resolve.service.internal

import com.google.gson.Gson
import org.gradle.api.GradleException
import org.gradle.internal.resource.transport.http.HttpResourceAccessor
import org.gradle.internal.resource.transport.http.HttpResponseResource
import org.gradle.internal.resource.transport.http.SslContextFactory
import org.gradle.plugin.use.internal.DefaultPluginId
import org.gradle.plugin.management.internal.PluginRequestInternal
import org.gradle.util.GradleVersion
import spock.lang.Specification

class HttpPluginResolutionServiceClientTest extends Specification {
    public static final String PLUGIN_PORTAL_URL = "http://plugin.portal"
    private resourceAccessor = Mock(HttpResourceAccessor)
    private sslContextFactory = Mock(SslContextFactory)
    private client = new HttpPluginResolutionServiceClient(sslContextFactory, resourceAccessor)
    private request = Stub(PluginRequestInternal) {
        getId() >> DefaultPluginId.of("foo")
    }

    def "returns plugin metadata for successful query"() {
        given:
        def metaData = new PluginUseMetaData("foo", "bar", [gav: "foo:bar:baz", repo: "http://repo.com"], PluginUseMetaData.M2_JAR, true)

        when:
        stubResponse(200, toJson(metaData))

        then:
        client.queryPluginMetadata(PLUGIN_PORTAL_URL, true, request).response == metaData
    }

    def "returns client status successful query"() {
        given:
        def status = new ClientStatus("message")

        when:
        stubResponse(200, toJson(status))

        then:
        client.queryClientStatus(PLUGIN_PORTAL_URL, true, null).response == status
    }

    def "returns error response for unsuccessful plugin query"() {
        def errorResponse = new ErrorResponse("INTERNAL_SERVER_ERROR", "Not feeling well today")

        when:
        stubResponse(500, toJson(errorResponse))
        def response = client.queryPluginMetadata(PLUGIN_PORTAL_URL, true, request)

        then:
        response.error
        with(response.errorResponse) {
            errorCode == errorResponse.errorCode
            message == errorResponse.message
        }
        response.statusCode == 500
    }

    def "returns error response for unsuccessful status query"() {
        def errorResponse = new ErrorResponse("INTERNAL_SERVER_ERROR", "Not feeling well today")

        when:
        stubResponse(500, toJson(errorResponse))
        def response = client.queryClientStatus(PLUGIN_PORTAL_URL, true, null)

        then:
        response.error
        with(response.errorResponse) {
            errorCode == errorResponse.errorCode
            message == errorResponse.message
        }
        response.statusCode == 500
    }

    def "only exactly 200 means success"() {
        when:
        stubResponse(201, "{}")
        client.queryPluginMetadata(PLUGIN_PORTAL_URL, true, request)

        then:
        def e = thrown(GradleException)
        e.message.contains "unexpected HTTP response status 201"
    }

    def "outside of 4xx - 5xx is unhanlded"() {
        when:
        stubResponse(650, "{}")
        client.queryPluginMetadata(PLUGIN_PORTAL_URL, true, request)

        then:
        def e = thrown(GradleException)
        e.message.contains "unexpected HTTP response status 650"
    }

    def "id and version are properly encoded"() {
        given:
        def customRequest = Stub(PluginRequestInternal) {
            getId() >> new DefaultPluginId("foo/bar")
            getVersion() >> "1/0"
        }

        when:
        client.queryPluginMetadata(PLUGIN_PORTAL_URL, true, customRequest)

        then:
        1 * resourceAccessor.getRawResource(new URI("$PLUGIN_PORTAL_URL/${GradleVersion.current().getVersion()}/plugin/use/foo%2Fbar/1%2F0"), false) >> Stub(HttpResponseResource) {
            getStatusCode() >> 500
            getContentType() >> "application/json"
            openStream() >> new ByteArrayInputStream("{errorCode: 'FOO', message: 'BAR'}".getBytes("utf8"))
        }
        0 * resourceAccessor.getRawResource(_, false)
    }

    private void stubResponse(int statusCode, String jsonResponse = null) {
        interaction {
            resourceAccessor.getRawResource(_, false) >> Stub(HttpResponseResource) {
                getStatusCode() >> statusCode
                if (jsonResponse != null) {
                    getContentType() >> "application/json"
                    openStream() >> new ByteArrayInputStream(jsonResponse.getBytes("utf8"))
                }
            }
        }
    }

    private String toJson(Object object) {
        new Gson().toJson(object)
    }
}
