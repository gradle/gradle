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

import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager
import org.gradle.cache.PersistentIndexedCache
import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.plugin.use.internal.DefaultPluginRequest
import org.gradle.plugin.use.internal.PluginRequest
import org.gradle.testfixtures.internal.InMemoryIndexedCache
import spock.lang.Specification

class CachingPluginResolutionServiceClientTest extends Specification {

    public static final String PORTAL_URL_1 = "http://foo"
    public static final PluginRequest REQUEST_1 = request("foo")
    public static final String PLUGIN_URL_1 = "$PORTAL_URL_1/foo/1"
    public static final PluginUseMetaData PLUGIN_METADATA_1 = new PluginUseMetaData("foo", "1", [foo: "bar"], "implType", false)
    public static final ErrorResponse ERROR_1 = new ErrorResponse("ERROR", "error")

    def delegate = Mock(PluginResolutionServiceClient)
    def cacheName = "cache"
    PersistentIndexedCache<CachingPluginResolutionServiceClient.Key, PluginResolutionServiceClient.Response<PluginUseMetaData>> cache = new InMemoryIndexedCache<>(new CachingPluginResolutionServiceClient.ResponseSerializer())
    def cacheLockingManager = Mock(CacheLockingManager) {
        createCache(cacheName, _, _) >> cache
        useCache(_, _) >> { String opName, org.gradle.internal.Factory<?> factory ->
            factory.create()
        }
    }

    def createClient(boolean invalidate = false) {
        new CachingPluginResolutionServiceClient(delegate, cacheName, cacheLockingManager, invalidate)
    }

    def "caches delegate success response"() {
        given:
        def response = new PluginResolutionServiceClient.SuccessResponse<PluginUseMetaData>(PLUGIN_METADATA_1, 200, PLUGIN_URL_1)
        1 * delegate.queryPluginMetadata(REQUEST_1, PORTAL_URL_1) >> response

        when:
        def client = createClient()

        then:
        client.queryPluginMetadata(REQUEST_1, PORTAL_URL_1).response == response.response
        client.queryPluginMetadata(REQUEST_1, PORTAL_URL_1).response == response.response
    }

    def "does not cache delegate error response"() {
        given:
        def response = new PluginResolutionServiceClient.ErrorResponseResponse(ERROR_1, 500, PLUGIN_URL_1)
        2 * delegate.queryPluginMetadata(REQUEST_1, PORTAL_URL_1) >> response

        when:
        def client = createClient()

        then:
        client.queryPluginMetadata(REQUEST_1, PORTAL_URL_1).response == response.response
        client.queryPluginMetadata(REQUEST_1, PORTAL_URL_1).response == response.response
    }

    def "invalidation causes request to be made"() {
        given:
        def response = new PluginResolutionServiceClient.SuccessResponse<PluginUseMetaData>(PLUGIN_METADATA_1, 200, PLUGIN_URL_1)
        2 * delegate.queryPluginMetadata(REQUEST_1, PORTAL_URL_1) >> response

        when:
        def client = createClient(true)

        then:
        client.queryPluginMetadata(REQUEST_1, PORTAL_URL_1).response == response.response
        client.queryPluginMetadata(REQUEST_1, PORTAL_URL_1).response == response.response
    }

    static PluginRequest request(String id, String version = "1") {
        new DefaultPluginRequest(id, version, 1, new StringScriptSource("test", "test"))
    }

}
