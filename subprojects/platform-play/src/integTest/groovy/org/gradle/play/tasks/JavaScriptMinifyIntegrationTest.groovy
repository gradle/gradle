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

package org.gradle.play.tasks

import org.hamcrest.Matchers
import static org.gradle.play.integtest.fixtures.Repositories.*

class JavaScriptMinifyIntegrationTest extends AbstractJavaScriptMinifyIntegrationTest {
    @Override
    String getDefaultSourceSet() {
        return "JavaScript"
    }

    def setup() {
        buildFile << """
            plugins {
                id 'play-application'
                id 'play-javascript'
            }

            ${PLAY_REPOSITORES}
        """
    }

    def "minifies default javascript source set as part of play application build"() {
        given:
        withJavaScriptSource("app/assets/test.js")

        when:
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":minifyPlayBinaryJavaScript",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
        minified("test.min.js").exists()
        copied("test.js").exists()
        assetsJar.containsDescendants(
                "public/test.min.js",
                "public/test.js"
        )

        and:
        matchesExpected("test.min.js")
    }

    def "does not re-minify when inputs and outputs are unchanged"() {
        given:
        withJavaScriptSource("app/assets/test.js")
        succeeds "assemble"

        when:
        succeeds "assemble"

        then:
        skipped(":minifyPlayBinaryJavaScript",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
    }

    def "re-minifies when an output is removed" () {
        given:
        withJavaScriptSource("app/assets/test.js")
        succeeds "assemble"

        // Detects missing output
        when:
        minified("test.min.js").delete()
        assetsJar.file.delete()
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":minifyPlayBinaryJavaScript",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
        minified("test.min.js").exists()
    }

    def "re-minifies when an input is changed" () {
        given:
        withJavaScriptSource("app/assets/test.js")
        succeeds "assemble"

        // Detects changed input
        when:
        file("app/assets/test.js") << "alert('this is a change!');"
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":minifyPlayBinaryJavaScript",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
    }

    def "cleans removed source file on minify" () {
        given:
        withJavaScriptSource("app/assets/test1.js")
        def source2 = withJavaScriptSource("app/assets/test2.js")

        when:
        succeeds "assemble"

        then:
        minified("test1.min.js").exists()
        minified("test2.min.js").exists()
        copied("test1.js").exists()
        copied("test2.js").exists()
        assetsJar.containsDescendants(
                "public/test1.min.js",
                "public/test2.min.js",
                "public/test1.js",
                "public/test2.js"
        )

        when:
        source2.delete()
        succeeds "assemble"

        then:
        ! minified("test2.min.js").exists()
        ! copied("test2.js").exists()
        assetsJar.countFiles("public/test2.min.js") == 0
        assetsJar.countFiles("public/test2.js") == 0
    }

    def "minifies multiple javascript source sets as part of play application build" () {
        given:
        withJavaScriptSource("app/assets/test1.js")
        withJavaScriptSource("extra/javascripts/test2.js")
        withJavaScriptSource("src/play/anotherJavaScript/a/b/c/test3.js")

        buildFile << """
            model {
                components {
                    play {
                        sources {
                            extraJavaScript(JavaScriptSourceSet) {
                                source.srcDir "extra"
                            }
                            anotherJavaScript(JavaScriptSourceSet)
                        }
                    }
                }
            }
        """

        when:
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":minifyPlayBinaryJavaScript",
                ":minifyPlayBinaryExtraJavaScript",
                ":minifyPlayBinaryAnotherJavaScript",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
        matchesExpected("test1.min.js")
        matchesExpected("ExtraJavaScript", "javascripts/test2.min.js")
        matchesExpected("AnotherJavaScript", "a/b/c/test3.min.js")
        matchesExpectedRaw(minified("test1.js"))
        matchesExpectedRaw(minified("ExtraJavaScript", "javascripts/test2.js"))
        matchesExpectedRaw(minified("AnotherJavaScript", "a/b/c/test3.js"))
        assetsJar.containsDescendants(
                "public/test1.min.js",
                "public/javascripts/test2.min.js",
                "public/a/b/c/test3.min.js",
                "public/test1.js",
                "public/javascripts/test2.js",
                "public/a/b/c/test3.js"
        )

        when:
        succeeds "assemble"

        then:
        skipped(":minifyPlayBinaryJavaScript",
                ":minifyPlayBinaryExtraJavaScript",
                ":minifyPlayBinaryAnotherJavaScript",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
    }

    def "produces sensible error on minify failure"() {
        given:
        file("app/assets/javascripts/test1.js") << "BAD SOURCE"
        file("app/assets/javascripts/test2.js") << "BAD SOURCE"
        withJavaScriptSource("app/assets/javascripts/hello.js")

        when:
        fails "assemble"

        then:
        minified("javascripts/hello.min.js").exists()
        copied("javascripts/hello.js").exists()
        failure.assertHasDescription("Execution failed for task ':minifyPlayBinaryJavaScript'.")

        String slash = File.separator
        failure.assertThatCause(Matchers.allOf([
                Matchers.startsWith("Minification failed with the following errors:"),
                Matchers.containsString("app${slash}assets${slash}javascripts${slash}test1.js line 1 : 4"),
                Matchers.containsString("app${slash}assets${slash}javascripts${slash}test2.js line 1 : 4")
        ]))
    }
}
