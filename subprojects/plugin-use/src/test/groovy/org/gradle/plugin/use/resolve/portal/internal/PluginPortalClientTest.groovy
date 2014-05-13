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

    def "returns metadata "() {
        given:
        def data = new PluginUseMetaData(id: "foo", version: "bar", implementation: [gav: "foo:bar:baz", repo: "http://repo.com"], implementationType: PluginUseMetaData.M2_JAR)

        when:
        stubResponse(200, toString(data))

        then:
        client.queryPluginMetadata(request, "http://plugin.portal") == data
    }

    def "plugin metadata query barks if portal returns a status code other than 200"() {
        when:
        stubResponse(404)
        client.queryPluginMetadata(request, "http://plugin.portal")

        then:
        def e = thrown(GradleException)
        e.message == "Plugin portal returned HTTP status code: 404"
    }

    private void stubResponse(int statusCode, String json = null) {
        interaction {
            resourceAccessor.getResource(_) >> Stub(HttpResponseResource) {
                getStatusCode() >> statusCode
                if (json != null) {
                    withContent(_) >> { Transformer<PluginUseMetaData, InputStream> action ->
                        action.transform(new ByteArrayInputStream(json.getBytes("utf8")))
                    }
                }
            }
        }
    }

    String toString(PluginUseMetaData pluginUseMetaData) {
        new Gson().toJson(pluginUseMetaData)
    }
}