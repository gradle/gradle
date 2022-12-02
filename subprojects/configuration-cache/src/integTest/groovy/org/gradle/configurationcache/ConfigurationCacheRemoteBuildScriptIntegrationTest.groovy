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

    def "report a problem if remote script was applied"() {
        given:
        server.start()
        String scriptName = "remote-script.gradle"
        String scriptUrl = "${server.uri}/${scriptName}"
        File scriptFile = file("remote-script.gradle") << """
            println 'loaded remote script'
        """
        server.expectGet("/$scriptName", scriptFile)

        buildFile << """
            apply from: '$scriptUrl'

            task ok
        """

        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun(WARN_PROBLEMS_CLI_OPT, 'ok')

        then:
        configurationCache.assertStateStored()
        problems.assertResultHasProblems(result) {
            problemsWithStackTraceCount = 0
            totalProblemsCount = 1
            withProblem("Build file 'build.gradle': Changes to 'script '$scriptUrl'' won't be tracked by the configuration cache.")
        }
    }
}
