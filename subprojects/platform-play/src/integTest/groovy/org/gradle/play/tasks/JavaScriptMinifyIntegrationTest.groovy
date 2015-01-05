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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.util.TextUtil

class JavaScriptMinifyIntegrationTest extends AbstractIntegrationSpec {
    def testJavaScript = """
            square = function(x) {
              return x * x;
            };
        """
    def expectedResult = "square=function(a){return a*a};\n"

    def setup() {
        buildFile << """
            plugins {
                id 'play-application'
                id 'play-javascript'
            }

            repositories{
                jcenter()
                maven{
                    name = "typesafe-maven-release"
                    url = "https://repo.typesafe.com/typesafe/maven-releases"
                }
            }
        """
    }

    def "non-play task minifies javascript files" () {
        buildFile << """
            model {
                tasks {
                    create('minifyJavaScript', JavaScriptMinify) {
                        source = fileTree("js")
                        destinationDir = new File(buildDir, "min")
                        closureCompilerNotation = "com.google.javascript:closure-compiler:v20141215"
                    }
                }
            }
        """
        file("js/test.js") << testJavaScript
        file("js/x/y/z.js") << testJavaScript

        when:
        succeeds "minifyJavaScript"

        then:
        file("build/min/test.min.js").exists()
        file("build/min/x/y/z.min.js").exists()

        and:
        matchesExpected("build/min/test.min.js")
        matchesExpected("build/min/x/y/z.min.js")
    }

    def "minifies default javascript source set as part of play application build"() {
        given:
        file("app/assets/test.js") << testJavaScript

        when:
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":minifyPlayBinaryJavaScriptAssets",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
        processed("test.min.js").exists()
        assetsJar.containsDescendants(
                "public/test.min.js"
        )

        and:
        matchesExpected(processed("test.min.js"))
    }

    def "does not re-minify when inputs and outputs are unchanged"() {
        given:
        file("app/assets/test.js") << testJavaScript
        succeeds "assemble"

        when:
        succeeds "assemble"

        then:
        skipped(":minifyPlayBinaryJavaScriptAssets",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
    }

    def "re-minifies when an output is removed" () {
        given:
        file("app/assets/test.js") << testJavaScript
        succeeds "assemble"

        // Detects missing output
        when:
        processed("test.min.js").delete()
        assetsJar.file.delete()
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":minifyPlayBinaryJavaScriptAssets",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
        processed("test.min.js").exists()
    }

    def "re-minifies when an input is changed" () {
        given:
        file("app/assets/test.js") << testJavaScript
        succeeds "assemble"

        // Detects changed input
        when:
        file("app/assets/test.js") << "alert('this is a change!');"
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":processPlayBinaryJavaScriptAssets",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
    }

    def "cleans removed source file on minify" () {
        given:
        file("app/assets/test1.js") << testJavaScript
        def source2 = file("app/assets/test2.js") << testJavaScript

        when:
        succeeds "assemble"

        then:
        processed("test1.min.js").exists()
        processed("test2.min.js").exists()
        assetsJar.containsDescendants(
                "public/test1.min.js",
                "public/test2.min.js"
        )

        when:
        source2.delete()
        succeeds "assemble"

        then:
        ! processed("test2.min.js").exists()
        assetsJar.countFiles("public/test2.min.js") == 0
    }

    def "minifies multiple javascript source sets as part of play application build" () {
        given:
        file("app/assets/test1.js") << testJavaScript
        file("extra/javascripts/test2.js") << testJavaScript
        file("src/play/anotherJavaScript/a/b/c/test3.js") << testJavaScript

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
                ":minifyPlayBinaryJavaScriptAssets",
                ":minifyPlayBinaryExtraJavaScript",
                ":minifyPlayBinaryAnotherJavaScript",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
        processed("test1.min.js").exists()
        processed("ExtraJavaScript", "javascripts/test2.min.js").exists()
        processed("AnotherJavaScript", "a/b/c/test3.min.js").exists()
        assetsJar.containsDescendants(
                "public/test1.min.js",
                "public/javascripts/test2.min.js",
                "public/a/b/c/test3.min.js"
        )

        and:
        matchesExpected(processed("test1.min.js"))
        matchesExpected(processed("ExtraJavaScript", "javascripts/test2.min.js"))
        matchesExpected(processed("AnotherJavaScript", "a/b/c/test3.min.js"))

        when:
        succeeds "assemble"

        then:
        skipped(":minifyPlayBinaryJavaScriptAssets",
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
        file("app/assets/javascripts/hello.js") << testJavaScript

        when:
        fails "assemble"

        then:
        processed("javascripts/hello.min.js").exists()
        failure.assertHasDescription("Execution failed for task ':minifyPlayBinaryJavaScriptAssets'.")
        failure.assertHasCause("Minification failed for the following files:\n\tjavascripts/test1.js\n\tjavascripts/test2.js")
    }

    boolean matchesExpected(fileName) {
        return TextUtil.normaliseLineSeparators(file(fileName).text) == TextUtil.normaliseLineSeparators(expectedResult)
    }

    JarTestFixture getAssetsJar() {
        jar("build/playBinary/lib/play-assets.jar")
    }

    JarTestFixture jar(String fileName) {
        new JarTestFixture(file(fileName))
    }

    File processed(String sourceSet = "JavaScriptAssets", String fileName) {
        file("build/playBinary/src/minifyPlayBinary${sourceSet}/${fileName}")
    }
}
