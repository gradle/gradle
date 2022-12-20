/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Rule

class ConfigurationCacheRemoteBuildScriptIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @Rule
    HttpServer server = new HttpServer()

    def "invalidates cache after change in remote build script"() {
        given:
        server.start()
        String scriptName = "remote-script.gradle"
        String scriptUrl = "${server.uri}/${scriptName}"
        File scriptFile = file("remote-script.gradle") << """
            println 'loaded remote script'
        """
        server.allowGetOrHead("/$scriptName", scriptFile)

        buildFile << """
            apply from: '$scriptUrl'
            task ok
        """

        when:
        configurationCacheRun 'ok'

        then:
        scriptFile << """
            println 'updated remote script'
        """

        configurationCacheRun 'ok'

        then:
        outputContains "Calculating task graph as configuration cache cannot be reused because cached external resource $scriptUrl has expired."
    }
}
