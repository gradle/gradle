/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.plugin.management.internal.DefaultPluginRequest
import org.gradle.plugin.management.internal.PluginRequestInternal
import spock.lang.Specification

class InMemoryCachingPluginResolutionServiceClientTest extends Specification {

    public static final String PORTAL_URL_1 = "http://foo"
    public static final PluginRequestInternal REQUEST_1 = request("foo")
    public static final String PLUGIN_URL_1 = "$PORTAL_URL_1/foo/1"
    public static final PluginUseMetaData PLUGIN_METADATA_1 = new PluginUseMetaData("foo", "1", [foo: "bar"], "implType", true)
    public static final ClientStatus CLIENT_STATUS_1 = new ClientStatus("One")
    public static final ClientStatus CLIENT_STATUS_2 = new ClientStatus("Two")
    public static final ErrorResponse ERROR_1 = new ErrorResponse("ERROR", "error")

    def delegate = Mock(PluginResolutionServiceClient)

    def createClient() {
        new InMemoryCachingPluginResolutionServiceClient(delegate)
    }

    def "caches delegate success response"() {
        given:
        def response = new PluginResolutionServiceClient.SuccessResponse<PluginUseMetaData>(PLUGIN_METADATA_1, 200, PLUGIN_URL_1, null)
        def client = createClient()

        when:
        client.queryPluginMetadata(PORTAL_URL_1, false, REQUEST_1).response == response.response
        client.queryPluginMetadata(PORTAL_URL_1, false, REQUEST_1).response == response.response

        then:
        1 * delegate.queryPluginMetadata(PORTAL_URL_1, false, REQUEST_1) >> response

        when:
        client.queryPluginMetadata(PORTAL_URL_1, true, REQUEST_1).response == response.response
        client.queryPluginMetadata(PORTAL_URL_1, true, REQUEST_1).response == response.response

        then:
        0 * delegate._
    }

    def "does not cache delegate error response"() {
        given:
        def response = new PluginResolutionServiceClient.ErrorResponseResponse(ERROR_1, 500, PLUGIN_URL_1, null)
        def client = createClient()

        when:
        client.queryPluginMetadata(PORTAL_URL_1, false, REQUEST_1).response == response.response
        client.queryPluginMetadata(PORTAL_URL_1, false, REQUEST_1).response == response.response

        then:
        2 * delegate.queryPluginMetadata(PORTAL_URL_1, false, REQUEST_1) >> response

        when:
        client.queryPluginMetadata(PORTAL_URL_1, true, REQUEST_1).response == response.response
        client.queryPluginMetadata(PORTAL_URL_1, true, REQUEST_1).response == response.response

        then:
        2 * delegate.queryPluginMetadata(PORTAL_URL_1, true, REQUEST_1) >> response
    }

    def "caches client status response"() {
        given:
        def response = new PluginResolutionServiceClient.SuccessResponse<ClientStatus>(CLIENT_STATUS_1, 200, PORTAL_URL_1, "1")
        def changedResponse = new PluginResolutionServiceClient.SuccessResponse<ClientStatus>(CLIENT_STATUS_2, 200, PORTAL_URL_1, "2")

        def client = createClient()

        when:
        client.queryClientStatus(PORTAL_URL_1, false, null).response == response.response
        client.queryClientStatus(PORTAL_URL_1, false, null).response == response.response

        then:
        2 * delegate.queryClientStatus(PORTAL_URL_1, false, null) >> response

        when:
        client.queryClientStatus(PORTAL_URL_1, false, "1").response == response.response

        then:
        0 * delegate._

        when:
        client.queryClientStatus(PORTAL_URL_1, false, "not 1").response == response.response

        then:
        1 * delegate.queryClientStatus(PORTAL_URL_1, false, "not 1") >> response

        when:
        client.queryClientStatus(PORTAL_URL_1, false, "1").response == response.response

        then:
        0 * delegate._

        when:
        client.queryClientStatus(PORTAL_URL_1, true, "1").response == changedResponse.response

        then:
        0 * delegate._

        when:
        client.queryClientStatus(PORTAL_URL_1, false, "2").response == changedResponse.response

        then:
        1 * delegate.queryClientStatus(PORTAL_URL_1, false, "2") >> changedResponse

        when:
        client.queryClientStatus(PORTAL_URL_1, false, null).response == response

        then:
        1 * delegate.queryClientStatus(PORTAL_URL_1, false, null) >> response
    }

    static PluginRequestInternal request(String id, String version = "1", String script = null) {
        new DefaultPluginRequest(new StringScriptSource("test", "test").displayName, 1, id, version, script, true)
    }

}
