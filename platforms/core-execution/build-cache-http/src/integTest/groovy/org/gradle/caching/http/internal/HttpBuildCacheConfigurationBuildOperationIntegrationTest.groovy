/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.http.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.test.fixtures.server.http.HttpBuildCacheServer
import org.junit.Rule

class HttpBuildCacheConfigurationBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    private static final SOME_CREDENTIALS =
        """
            remote.credentials {
                username = "user"
                password = "pass"
            }
        """

    private static final NO_CREDENTIALS = ''

    private static final INCOMPLETE_CREDENTIALS =
        """
            remote.credentials {
                username = "user"
            }
        """

    def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    @Rule
    HttpBuildCacheServer httpBuildCacheServer = new HttpBuildCacheServer(testDirectoryProvider)

    def "remote build cache configuration is exposed"() {
        given:
        httpBuildCacheServer.start()
        def url = "${httpBuildCacheServer.uri}/"
        settingsFile << """
            buildCache {
                local {
                    enabled = false
                }
                remote(org.gradle.caching.http.HttpBuildCache) {
                    enabled = true
                    url = "$url"
                    push = $push
                    $credentials
                }
            }
        """
        executer.withBuildCacheEnabled()

        when:
        succeeds("help")

        then:
        def result = result()

        result.remoteEnabled
        !result.localEnabled

        result.remote.className == 'org.gradle.caching.http.HttpBuildCache'
        result.remote.config.url == url

        if (authenticated) {
            result.remote.config.authenticated == "true"
        } else {
            result.remote.config.authenticated == null
        }

        result.remote.config.useExpectContinue == "false"

        result.remote.type == 'HTTP'
        result.remote.push == push


        where:
        authenticated | credentials            | push
        'true'        | SOME_CREDENTIALS       | true
        'false'       | NO_CREDENTIALS         | false
        'false'       | INCOMPLETE_CREDENTIALS | false
    }

    def "remote build cache configuration is exposed when basic auth is encoded on the url"() {
        given:
        httpBuildCacheServer.start()
        def safeUri = httpBuildCacheServer.uri
        def basicAuthUri = new URI(safeUri.getScheme(), 'user:pwd', safeUri.getHost(), safeUri.getPort(), safeUri.getPath(), safeUri.getQuery(), safeUri.getFragment())
        settingsFile << """
            buildCache {
                remote(org.gradle.caching.http.HttpBuildCache) {
                    enabled = true
                    url = "${basicAuthUri}/"
                }
            }
        """
        executer.withBuildCacheEnabled()

        when:
        succeeds("help")

        then:
        def config = result().remote.config
        config.url == safeUri.toString() + '/'
        config.authenticated == "true"
    }

    def "--offline wins over DSL configuration when exposing remote enabled configuration"() {
        given:
        httpBuildCacheServer.start()
        def url = "${httpBuildCacheServer.uri}/"
        settingsFile << """
            buildCache {
                remote(org.gradle.caching.http.HttpBuildCache) {
                    enabled = true
                    url = "$url/"
                }
            }
        """
        executer.withBuildCacheEnabled().withArgument('--offline')

        when:
        succeeds("help")

        then:
        !result().remoteEnabled
    }

    def "remote build cache configuration details is not exposed when disabled"() {
        given:
        httpBuildCacheServer.start()
        def url = "${httpBuildCacheServer.uri}/"
        settingsFile << """
            buildCache {
                remote(org.gradle.caching.http.HttpBuildCache) {
                    enabled = false
                    url = "$url/"
                }
            }
        """
        executer.withBuildCacheEnabled()

        when:
        succeeds("help")

        then:
        def result = result()
        !result.remoteEnabled
        result.remote == null
    }

    def "remote build cache configuration details is not exposed when not defined"() {
        given:
        settingsFile << """
            buildCache {
                local {
                    enabled = false
                    directory = 'directory'
                    push = false
                }
            }
        """
        executer.withBuildCacheEnabled()

        when:
        succeeds("help")

        then:
        def result = result()
        !result.remoteEnabled
        result.remote == null
    }

    def "captures useExpectContinue"() {
        given:
        httpBuildCacheServer.start()
        def url = "${httpBuildCacheServer.uri}/"

        settingsFile << """
            buildCache {
                remote(org.gradle.caching.http.HttpBuildCache) {
                    enabled = true
                    url = "$url"
                    useExpectContinue = true
                }
            }
        """
        executer.withBuildCacheEnabled()

        when:
        succeeds("help")

        then:
        result().remote.config.useExpectContinue == "true"
    }


    Map<String, ?> result() {
        buildOperations.result("Finalize build cache configuration")
    }

}
