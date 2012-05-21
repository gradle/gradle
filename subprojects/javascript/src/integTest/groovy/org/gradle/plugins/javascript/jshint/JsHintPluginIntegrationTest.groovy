/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.plugins.javascript.jshint

import org.gradle.integtests.fixtures.WellBehavedPluginTest

import static org.gradle.plugins.javascript.base.JavaScriptBasePluginTestFixtures.addGradlePublicJsRepoScript

class JsHintPluginIntegrationTest extends WellBehavedPluginTest {

    def setup() {
        applyPlugin()
        addGradlePublicJsRepoScript(buildFile)
        buildFile << """
            repositories.mavenCentral()
        """
    }

    def "can analyse javascript"() {
        given:
        file("src/main/js/dir1/f1.js") << """
            "a" == null
        """

        when:
        buildFile << """
            task jsHint(type: ${JsHint.name}) {
                source fileTree("src/main/js")
            }
        """

        then:
        succeeds "jsHint"

        and:
        ":jsHint" in nonSkippedTasks
    }

}
