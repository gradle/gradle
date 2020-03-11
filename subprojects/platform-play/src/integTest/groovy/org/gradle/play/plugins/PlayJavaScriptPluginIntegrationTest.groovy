/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.plugins
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.util.TextUtil

class PlayJavaScriptPluginIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.noDeprecationChecks()
        buildFile << """
            plugins {
                id 'play-application'
                id 'play-javascript'
            }

            model {
                components {
                    play {
                        sources {
                            otherJavaScript(JavaScriptSourceSet)
                        }
                    }
                }
            }
        """
    }

    @ToBeFixedForInstantExecution(because = ":components")
    def "javascript source sets appear in component listing"() {
        when:
        succeeds "components"

        then:
        normalizedOutput.contains("""
    JavaScript source 'play:javaScript'
        srcDir: app/assets
        includes: **/*.js
    JavaScript source 'play:otherJavaScript'
        srcDir: src/play/otherJavaScript
""")
    }

    def "creates and configures minify task when source exists"() {
        buildFile << """
            task checkTasks {
                doLast {
                    tasks.withType(JavaScriptMinify)*.name as Set == ["minifyPlayBinaryJavaScript", "minifyPlayBinaryOtherJavaScript"] as Set
                }
            }
        """

        when:
        file("app/assets/test.js") << "test"
        file("src/play/otherJavaScript/other.js") << "test"

        then:
        succeeds "checkTasks"
    }

    def "does not create minify task when source does not exist"() {
        buildFile << """
            task checkTasks {
                doLast {
                    assert tasks.withType(JavaScriptMinify).size() == 0
                }
            }
        """

        expect:
        succeeds "checkTasks"
    }

    private String getNormalizedOutput() {
        return TextUtil.normaliseFileSeparators(output)
    }
}
