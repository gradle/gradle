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

import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/18897")
class ConfigurationCacheMissingScriptIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "picking up formerly-missing build scripts"() {
        given:
        settingsFile << """
            include 'a', 'b'
        """

        createDir('a') {
            file('build.gradle') << """
                task ok
        """
        }

        def b = createDir('b')

        and:
        configurationCacheRun 'ok'

        and:
        b.file('build.gradle') << """
                task ok
        """

        when:
        configurationCacheRun 'ok'

        then:
        outputContains("Calculating task graph as configuration cache cannot be reused because file 'b/build.gradle' has changed.")
        result.assertTasksExecuted(":a:ok", ":b:ok")
    }

    def "picking up formerly-missing settings script"() {
        given:
        file('build.gradle') << """
                task ok
        """
        createDir('a') {
            file('build.gradle') << """
                task ok
        """
        }

        createDir('b') {
            file('build.gradle') << """
                task ok
        """
        }

        and:
        configurationCacheRun 'ok'

        and:
        settingsFile << """
            include 'a', 'b'
        """

        when:
        configurationCacheRun 'ok'

        then:
        outputContains("Calculating task graph as configuration cache cannot be reused because file 'settings.gradle' has changed.")
        result.assertTasksExecuted(":ok", ":a:ok", ":b:ok")
    }

    def "picking up formerly-missing buildSrc/settings script"() {
        given:
        buildFile << """
                task ok
        """

        and:
        file("buildSrc/build.gradle") << """
            task foo
        """
        createDir("buildSrc/a") {
            file('build.gradle') << """
                task bar
        """
        }

        and:
        configurationCacheRun 'ok'

        and:
        file("buildSrc/settings.gradle") << """
            include 'a'
        """

        when:
        configurationCacheRun 'ok'

        then:
        outputContains("Calculating task graph as configuration cache cannot be reused because file 'buildSrc/settings.gradle' has changed.")
    }
}
