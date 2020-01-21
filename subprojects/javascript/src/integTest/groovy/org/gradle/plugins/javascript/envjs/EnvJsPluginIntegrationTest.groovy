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

package org.gradle.plugins.javascript.envjs

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.WellBehavedPluginTest
import org.gradle.plugins.javascript.envjs.browser.BrowserEvaluate

import static org.gradle.plugins.javascript.base.JavaScriptBasePluginTestFixtures.addGoogleRepoScript
import static org.gradle.plugins.javascript.base.JavaScriptBasePluginTestFixtures.addGradlePublicJsRepoScript

class EnvJsPluginIntegrationTest extends WellBehavedPluginTest {
    @Override
    String getPluginName() {
        return "envjs"
    }

    def setup() {
        applyPlugin()
        addGradlePublicJsRepoScript(buildFile)
        buildFile << """
            ${mavenCentralRepository()}
        """
        executer.expectDocumentedDeprecationWarning("The org.gradle.envjs plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_5.html#deprecated_plugins")
        executer.expectDocumentedDeprecationWarning("The org.gradle.rhino plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_5.html#deprecated_plugins")
        executer.expectDocumentedDeprecationWarning("The org.gradle.javascript-base plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_5.html#deprecated_plugins")
    }

    def "can download envjs by default"() {
        given:
        buildFile << """
            task resolve(type: Copy) {
                from javaScript.envJs.js
                into "deps"
            }
        """

        when:
        run "resolve"

        then:
        def js = file("deps/envjs.rhino-1.2.js")
        js.exists()
        js.text.contains("Envjs = function")
    }

    @ToBeFixedForInstantExecution
    def "can evaluate content"() {
        given:
        file("input/index.html") << """
            <html>
                <head>
                    <script src="\${jqueryFileName}" type="text/javascript"></script>
                    <script type="text/javascript">
                        \\\$(function() {
                            \\\$("body").text("Added!");
                        });
                    </script>
                </head>
            </html>
        """

        addGoogleRepoScript(buildFile)

        buildFile << """
            configurations {
                jquery
            }
            dependencies {
                jquery "jquery:jquery.min:1.7.2@js"
            }

            task gatherContent(type: Copy) {
                into "content"
                from configurations.jquery
                from "input", {
                    expand jqueryFileName: "\${->configurations.jquery.singleFile.name}"
                }
            }

            task evaluate(type: ${BrowserEvaluate.name}) {
                content gatherContent
                resource "index.html"
                result "result.html"
            }
        """

        when:
        succeeds "evaluate"

        then:
        file("result.html").text.contains("<body>Added!</body>")
    }
}
