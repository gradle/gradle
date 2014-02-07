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

package org.gradle.test.fixtures.bintray

import org.gradle.test.fixtures.server.http.HttpServer

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static org.gradle.plugin.resolve.internal.JCenterPluginMapper.*
import static org.gradle.test.fixtures.server.http.HttpServer.Utils.json

class BintrayApi {

    final HttpServer httpServer
    private final String path

    BintrayApi(HttpServer httpServer) {
        // The bintray client seems hardcoded to issue requests relative to the root
        // therefore we have to use the root as the path
        // Waiting for one of the following issues to be resolved so we can debug into it:
        // https://github.com/bintray/bintray-client-java/pull/2
        // https://github.com/bintray/bintray-client-java/issues/3
        this.path = ""
        this.httpServer = httpServer
    }

    String getAddress() {
        httpServer.address + path
    }

    void expectPackageSearch(String pluginId, FoundPackage... packages) {
        httpServer.expect("$path/search/attributes/$GRADLE_PLUGINS_ORG/$GRADLE_PLUGINS_REPO", ["POST"], new HttpServer.ActionSupport("search action") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                def requestBody = json(request)
                assert requestBody == json("[{\"${PLUGIN_ID_ATTRIBUTE_NAME}\":[\"$pluginId\"]}]")
                json(response, packages*.asStruct())
            }
        })
    }

    static class FoundPackage {
        List<String> systemIds
        String latestVersion

        FoundPackage(String latestVersion, String... systemIds) {
            this.systemIds = systemIds.toList()
            this.latestVersion = latestVersion
        }

        Map<String, Object> asStruct() {
            [
                    "name": "not-used",
                    "repo": "not-used",
                    "owner": "not-used",
                    "desc": "not-used",
                    "labels": [],
                    "attribute_names": ["not-used"],
                    "followers_count": 0,
                    "created": "2013-11-18T12:31:31.147Z",
                    "versions": [],
                    "latest_version": latestVersion,
                    "updated": "2013-11-18T14:39:20.023Z",
                    "rating_count": 0,
                    "system_ids": systemIds
            ]
        }
    }
}
