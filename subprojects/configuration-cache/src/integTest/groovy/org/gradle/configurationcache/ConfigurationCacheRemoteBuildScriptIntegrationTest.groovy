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
import spock.lang.Issue

class ConfigurationCacheRemoteBuildScriptIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @Rule
    HttpServer server = new HttpServer()

    def scriptUrl
    def scriptFile
    def configurationCache
    def scriptName = "remote-script.gradle"

    def setup() {
        server.start()

        scriptUrl = "${server.uri}/${scriptName}"
        scriptFile = file("remote-script.gradle") << """
            println 'loaded remote script'
        """
        server.expectGet("/$scriptName", scriptFile)

        buildFile << """
            apply from: '$scriptUrl'
            task ok
        """

        configurationCache = newConfigurationCacheFixture()
    }

    @Issue("https://github.com/gradle/gradle/issues/23273")
    def "invalidates cache if remote script was changed"() {
        when:
        configurationCacheRun 'ok'

        then:
        configurationCache.assertStateStored()

        and:
        server.expectHead("/$scriptName", scriptFile)
        server.expectGet("/$scriptName", scriptFile)

        when:
        scriptFile << """
            print 'update remote script'
        """

        and:
        configurationCacheRun 'ok'

        then:
        output.contains("Calculating task graph as configuration cache cannot be reused because remote script $scriptUrl has changed.")
        configurationCache.assertStateStored()
    }

    def "reuse cache if remote script is up to date"() {
        when:
        configurationCacheRun 'ok'

        then:
        configurationCache.assertStateStored()

        and:
        server.expectHead("/$scriptName", scriptFile)
        configurationCacheRun 'ok'

        then:
        configurationCache.assertStateLoaded()
    }

    def "reuse cache for offline build"() {
        when:
        configurationCacheRun 'ok'

        then:
        configurationCache.assertStateStored()

        and:
        executer.withArgument("--offline")
        configurationCacheRun 'ok'

        when:
        scriptFile << """
            print 'update remote script'
        """

        then:
        configurationCache.assertStateStored()

        and:
        executer.withArgument("--offline")
        configurationCacheRun 'ok'

        then:
        configurationCache.assertStateLoaded()
    }
}
