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

import static org.gradle.plugin.use.resolve.service.internal.DeprecationListeningPluginResolutionServiceClient.toMessage

class DeprecationListeningPluginResolutionServiceClientTest extends Specification {

    public static final String PORTAL_URL_1 = "http://foo"
    public static final PluginRequestInternal REQUEST_1 = request("foo")
    public static final String PLUGIN_URL_1 = "$PORTAL_URL_1/foo/1"
    public static final PluginUseMetaData PLUGIN_METADATA_1 = new PluginUseMetaData("foo", "1", [foo: "bar"], "implType", true)
    public static final ClientStatus CLIENT_STATUS_1 = new ClientStatus("One")
    public static final ClientStatus CLIENT_STATUS_2 = new ClientStatus("Two")
    public static final ErrorResponse ERROR_1 = new ErrorResponse("ERROR", "error")

    def msgs = []

    def delegate = Mock(PluginResolutionServiceClient)
    def client = new DeprecationListeningPluginResolutionServiceClient(delegate, { msgs << it })

    def "no client status"() {
        given:
        1 * delegate.queryPluginMetadata(PORTAL_URL_1, false, REQUEST_1) >> new PluginResolutionServiceClient.SuccessResponse<PluginUseMetaData>(PLUGIN_METADATA_1, 200, PLUGIN_URL_1, null)

        when:
        client.queryPluginMetadata(PORTAL_URL_1, false, REQUEST_1)

        then:
        msgs.isEmpty()
    }

    def "fetches client status"() {
        def checksum = "foo"
        given:
        1 * delegate.queryPluginMetadata(PORTAL_URL_1, false, REQUEST_1) >> new PluginResolutionServiceClient.SuccessResponse<PluginUseMetaData>(PLUGIN_METADATA_1, 200, PLUGIN_URL_1, checksum)
        1 * delegate.queryClientStatus(PORTAL_URL_1, false, checksum) >> new PluginResolutionServiceClient.SuccessResponse<ClientStatus>(CLIENT_STATUS_1, 200, PLUGIN_URL_1, checksum)

        when:
        client.queryPluginMetadata(PORTAL_URL_1, false, REQUEST_1)

        then:
        msgs.size() == 1
        msgs[0] == toMessage(CLIENT_STATUS_1.deprecationMessage, PLUGIN_URL_1)
    }

    def "ignores error response"() {
        given:
        def checksum = "foo"
        1 * delegate.queryPluginMetadata(PORTAL_URL_1, false, REQUEST_1) >> new PluginResolutionServiceClient.SuccessResponse<PluginUseMetaData>(PLUGIN_METADATA_1, 200, PLUGIN_URL_1, checksum)
        1 * delegate.queryClientStatus(PORTAL_URL_1, false, checksum) >> new PluginResolutionServiceClient.ErrorResponseResponse<PluginUseMetaData>(ERROR_1, 200, PLUGIN_URL_1, checksum)

        when:
        client.queryPluginMetadata(PORTAL_URL_1, false, REQUEST_1)

        then:
        noExceptionThrown()
        msgs.isEmpty()
    }

    def "ignores exception"() {
        given:
        def checksum = "foo"
        1 * delegate.queryPluginMetadata(PORTAL_URL_1, false, REQUEST_1) >> new PluginResolutionServiceClient.SuccessResponse<PluginUseMetaData>(PLUGIN_METADATA_1, 200, PLUGIN_URL_1, checksum)
        1 * delegate.queryClientStatus(PORTAL_URL_1, false, checksum) >> { throw new Exception("!!!") }

        when:
        client.queryPluginMetadata(PORTAL_URL_1, false, REQUEST_1)

        then:
        noExceptionThrown()
        msgs.isEmpty()
    }

    static PluginRequestInternal request(String id, String version = null, String script = null) {
        new DefaultPluginRequest(new StringScriptSource("test", "test").displayName, 1, id, version, script, true)
    }
}
