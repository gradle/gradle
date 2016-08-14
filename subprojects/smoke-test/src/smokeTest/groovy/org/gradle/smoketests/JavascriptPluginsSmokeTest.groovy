/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.testkit.runner.TaskOutcome

class JavascriptPluginsSmokeTest extends AbstractSmokeTest {

    def 'js plugin'() {
        given:
        buildFile << """
            plugins {
                id "com.eriwen.gradle.js" version "2.14.1"
            }

            javascript.source {
                dev {
                    js {
                        srcDir 'jsSrcDir'
                        include "*.js"
                        exclude "*.min.js"
                    }
                }
                prod {
                    js {
                        srcDir 'jsSrcDir'
                        include "*.min.js"
                    }
                }
            }

            combineJs {
                encoding = "UTF-8"
                source = javascript.source.dev.js.files
                dest = file("\${buildDir}/all.js")
            }

            minifyJs {
                source = combineJs
                dest = file("\${buildDir}/all-min.js")
                sourceMap = file("\${buildDir}/all.sourcemap.json")
                closure {
                    warningLevel = 'QUIET'
                }
            }
            """.stripIndent()

        file("jsSrcDir/app.js") << """
            console.log("Hello from Javascript");
        """

        when:
        def result = runner('tasks', 'minifyJs').build()

        then:
        result.task(':minifyJs').outcome == TaskOutcome.SUCCESS
        file("build/all-min.js").exists()
    }

    def 'css plugin'() {
        given:
        buildFile << """
            plugins {
                id "com.eriwen.gradle.css" version "2.14.0"
            }

            // Declare your sources
            css.source {
                dev {
                    css {
                        srcDir "app/styles"
                        include "*.css"
                        exclude "*.min.css"
                    }
                }
            }

            // Specify a collection of files to be combined, then minified and finally GZip compressed.
            combineCss {
                source = css.source.dev.css.files
                dest = "\${buildDir}/all.css"
            }

            minifyCss {
                source = combineCss
                dest = "\${buildDir}/all-min.css"
                yuicompressor { // Optional
                    lineBreakPos = -1
                }
            }

            gzipCss {
                source = minifyCss
                dest = "\${buildDir}/all.2.0.4.css"
            }
            """.stripIndent()

        file("app/styles/app.css") << """
            h2 {
                bg-color: red;
            }
        """

        when:
        def result = runner('tasks', 'minifyCss').build()

        then:
        result.task(':minifyCss').outcome == TaskOutcome.SUCCESS
        file("build/all-min.css").exists()
    }
}
