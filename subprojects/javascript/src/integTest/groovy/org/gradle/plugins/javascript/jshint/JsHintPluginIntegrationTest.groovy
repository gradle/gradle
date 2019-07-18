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

import groovy.json.JsonSlurper
import org.gradle.integtests.fixtures.WellBehavedPluginTest

import static org.gradle.plugins.javascript.base.JavaScriptBasePluginTestFixtures.addGradlePublicJsRepoScript

class JsHintPluginIntegrationTest extends WellBehavedPluginTest {
    @Override
    String getPluginName() {
        return "jshint"
    }

    def setup() {
        applyPlugin()
        addGradlePublicJsRepoScript(buildFile)
        buildFile << """
            ${mavenCentralRepository()}
        """
    }

    def taskForFileTree(String path = "src/main/js", File file = buildFile) {
        file << """
            task jsHint(type: ${JsHint.name}) {
                source fileTree("$path")
            }
        """
    }

    def "can analyse bad javascript"() {
        given:
        file("src/main/js/dir1/f1.js") << """
            "a" == null
        """
        file("src/main/js/dir2/f2.js") << """
            "b" == null
        """

        when:
        taskForFileTree()

        then:
        fails "jsHint"

        and:
        failureHasCause "JsHint detected errors"

        and:
        output.contains "2:17 > Use '===' to compare with 'null'."

        and:
        File jsonReport = file("build/reports/jsHint/report.json")
        jsonReport.exists()

        and: // it's valid json
        def json = new JsonSlurper().parseText(jsonReport.text)
        json[file("src/main/js/dir1/f1.js").absolutePath] instanceof Map
    }

    def "can analyse good javascript"() {
        given:
        file("src/main/js/dir1/f1.js") << """
            var a = "a" === null;
        """
        file("src/main/js/dir2/f2.js") << """
            var b = "b" === null;
        """

        when:
        taskForFileTree()

        then:
        succeeds "jsHint"

        and:
        executedAndNotSkipped(":jsHint")

        and:
        File jsonReport = file("build/reports/jsHint/report.json")
        jsonReport.exists()

        and: // it's valid json
        def json = new JsonSlurper().parseText(jsonReport.text)
        json[file("src/main/js/dir1/f1.js").absolutePath] instanceof Map

        when:
        run "jsHint"

        then:
        skipped(":jsHint")
    }

}
