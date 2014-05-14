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

package org.gradle.plugin.use.resolve.portal.internal

import com.google.gson.Gson
import org.gradle.api.GradleException
import org.gradle.api.Transformer
import org.gradle.internal.resource.transport.http.HttpResourceAccessor
import org.gradle.internal.resource.transport.http.HttpResponseResource
import org.gradle.plugin.internal.PluginId
import org.gradle.plugin.use.internal.PluginRequest
import spock.lang.Specification

class PluginPortalClientTest extends Specification {
    private resourceAccessor = Mock(HttpResourceAccessor)
    private client = new PluginPortalClient(resourceAccessor)
    private request = Stub(PluginRequest) {
        getId() >> PluginId.of("foo")
    }

    def "returns plugin metadata for successful query"() {
        given:
        def metaData = new PluginUseMetaData(id: "foo", version: "bar", implementation: [gav: "foo:bar:baz", repo: "http://repo.com"], implementationType: PluginUseMetaData.M2_JAR)

        when:
        stubResponse(200, toJson(metaData))

        then:
        client.queryPluginMetadata(request, "http://plugin.portal") == metaData
    }

    def "returns error response for unsuccessful query"() {
        def errorResponse = new ErrorResponse("INTERNAL_SERVER_ERROR", "Not feeling well today")

        when:
        stubResponse(500, toJson(errorResponse))
        client.queryPluginMetadata(request, "http://plugin.portal")

        then:
        def e = thrown(GradleException)
        e.message == "Plugin portal returned HTTP 500 with message 'Not feeling well today'."
    }

    private void stubResponse(int statusCode, String jsonResponse = null) {
        interaction {
            resourceAccessor.getRawResource(_) >> Stub(HttpResponseResource) {
                getStatusCode() >> statusCode
                if (jsonResponse != null) {
                    withContent(_) >> { Transformer<PluginUseMetaData, InputStream> action ->
                        action.transform(new ByteArrayInputStream(jsonResponse.getBytes("utf8")))
                    }
                }
            }
        }
    }

    private String toJson(Object object) {
        new Gson().toJson(object)
    }
}