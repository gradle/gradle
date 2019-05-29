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

import org.gradle.test.fixtures.file.TestFile
import org.hamcrest.CoreMatchers
import static org.gradle.play.integtest.fixtures.Repositories.*

class JavaScriptMinifyIntegrationTest extends AbstractJavaScriptMinifyIntegrationTest {

    TestFile getProcessedJavaScriptDir(String sourceSet) {
        file("build/src/play/binary/minifyPlayBinaryPlay${sourceSet != null ? sourceSet.capitalize() : "JavaScript"}")
    }

    void hasProcessedJavaScript(String fileName) {
        hasProcessedJavaScript(null, fileName)
    }

    void hasProcessedJavaScript(String sourceSet, String fileName) {
        hasExpectedJavaScript(processedJavaScript(sourceSet, "${fileName}.js" ))
        hasMinifiedJavaScript(processedJavaScript(sourceSet, "${fileName}.min.js" ))
    }

    def setup() {
        buildFile << """
            plugins {
                id 'play-application'
                id 'play-javascript'
            }

            ${PLAY_REPOSITORIES}
        """
    }

    def "minifies default javascript source set as part of play application build"() {
        given:
        withJavaScriptSource("app/assets/test.js")

        when:
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":minifyPlayBinaryPlayJavaScript",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
        assetsJar.containsDescendants(
                "public/test.min.js",
                "public/test.js"
        )

        and:
        hasProcessedJavaScript("test")
    }

    def "does not re-minify when inputs and outputs are unchanged"() {
        given:
        withJavaScriptSource("app/assets/test.js")
        succeeds "assemble"

        when:
        executer.noDeprecationChecks()
        succeeds "assemble"

        then:
        skipped(":minifyPlayBinaryPlayJavaScript",
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
        executer.noDeprecationChecks()
        processedJavaScript("test.min.js").delete()
        assetsJar.file.delete()
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":minifyPlayBinaryPlayJavaScript",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
        hasProcessedJavaScript("test")
    }

    def "re-minifies when an input is changed" () {
        given:
        withJavaScriptSource("app/assets/test.js")
        succeeds "assemble"

        // Detects changed input
        when:
        executer.noDeprecationChecks()
        file("app/assets/test.js") << "alert('this is a change!');"
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":minifyPlayBinaryPlayJavaScript",
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
        hasProcessedJavaScript("test1")
        hasProcessedJavaScript("test2")
        assetsJar.containsDescendants(
                "public/test1.min.js",
                "public/test2.min.js",
                "public/test1.js",
                "public/test2.js"
        )

        when:
        executer.noDeprecationChecks()
        source2.delete()
        succeeds "assemble"

        then:
        ! processedJavaScript("test2.min.js").exists()
        ! processedJavaScript("test2.js").exists()
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
                ":minifyPlayBinaryPlayJavaScript",
                ":minifyPlayBinaryPlayExtraJavaScript",
                ":minifyPlayBinaryPlayAnotherJavaScript",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
        hasProcessedJavaScript("test1")
        hasProcessedJavaScript("ExtraJavaScript", "javascripts/test2")
        hasProcessedJavaScript("AnotherJavaScript", "a/b/c/test3")
        assetsJar.containsDescendants(
                "public/test1.min.js",
                "public/javascripts/test2.min.js",
                "public/a/b/c/test3.min.js",
                "public/test1.js",
                "public/javascripts/test2.js",
                "public/a/b/c/test3.js"
        )

        when:
        executer.noDeprecationChecks()
        succeeds "assemble"

        then:
        skipped(":minifyPlayBinaryPlayJavaScript",
                ":minifyPlayBinaryPlayExtraJavaScript",
                ":minifyPlayBinaryPlayAnotherJavaScript",
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
        hasProcessedJavaScript("javascripts/hello")
        failure.assertHasDescription("Execution failed for task ':minifyPlayBinaryPlayJavaScript'.")

        String slash = File.separator
        failure.assertThatCause(CoreMatchers.allOf([
            CoreMatchers.startsWith("Minification failed with the following errors:"),
            CoreMatchers.containsString("app${slash}assets${slash}javascripts${slash}test1.js line 1 : 4"),
            CoreMatchers.containsString("app${slash}assets${slash}javascripts${slash}test2.js line 1 : 4")
        ]))
    }
}
