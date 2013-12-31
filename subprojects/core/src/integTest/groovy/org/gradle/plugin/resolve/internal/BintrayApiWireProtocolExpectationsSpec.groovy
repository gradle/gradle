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

package org.gradle.plugin.resolve.internal

import com.jfrog.bintray.client.api.model.Pkg
import com.jfrog.bintray.client.impl.BintrayClient
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Rule
import spock.lang.Specification

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static org.gradle.test.fixtures.server.http.HttpServer.Utils.json

/**
 * This tests the actual wire protocol between the Bintray client library and the server.
 *
 * It's purpose is to verify our understanding of the interaction to assert we are mocking it correctly in our tests.
 */
class BintrayApiWireProtocolExpectationsSpec extends Specification {

    private static final String GRADLE_PLUGINS_ORG = "gradle-plugins-development";
    private static final String GRADLE_PLUGINS_REPO = "gradle-plugins";
    private static final String PLUGIN_ID_ATTRIBUTE_NAME = "gradle-plugin-id";
    private static final String PLUGIN_ID = "gradle-test-plugin"

    @Rule HttpServer server = new HttpServer()

    def "make request not matching anything"() {
        given:
        expectSearchRequestAndReturn([])

        when:
        List<Pkg> results = doSearch()

        then:
        results.empty
    }

    def "make request matching one package"() {
        given:
        expectSearchRequestAndReturn(json("""[{
            "name":"testPlugin",
            "repo":"gradle-plugins",
            "owner":"gradle-plugins-development",
            "desc":null,
            "labels":[],
            "attribute_names": ["gradle-plugin-id"],
            "followers_count":0,
            "created": "2013-11-18T12:31:31.147Z",
            "versions":["1.0","2.0"],
            "latest_version":"2.0",
            "updated":"2013-11-18T14:39:20.023Z",
            "rating_count":0,
            "system_ids": ["com.bintray.gradle.test:test-plugin"]
        }]"""))

        when:
        List<Pkg> results = doSearch()

        then:
        results.size() == 1
        def pkg = results.first()
        pkg.name() == "testPlugin"
    }

    List<Pkg> doSearch() {
        BintrayClient.create(server.address, null, null).
                subject(GRADLE_PLUGINS_ORG).
                repository(GRADLE_PLUGINS_REPO).
                searchForPackage().
                byAttributeName(PLUGIN_ID_ATTRIBUTE_NAME).
                equals(PLUGIN_ID).
                search();
    }

    void expectSearchRequestAndReturn(jsonResult) {
        server.expect("/search/attributes/$GRADLE_PLUGINS_ORG/$GRADLE_PLUGINS_REPO", ["POST"], new HttpServer.ActionSupport("search action") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                def requestBody = json(request)
                assert requestBody == json("[{\"gradle-plugin-id\":[\"$PLUGIN_ID\"]}]")
                json(response, jsonResult)
            }
        })
    }
}
