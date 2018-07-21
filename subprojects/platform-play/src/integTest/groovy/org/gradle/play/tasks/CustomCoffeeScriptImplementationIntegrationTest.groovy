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

import spock.lang.Ignore

@Ignore('contains javascript repository')
class CustomCoffeeScriptImplementationIntegrationTest extends AbstractCoffeeScriptCompileIntegrationTest {
    def customCoffeeScriptImplFileName

    def setup() {
        customCoffeeScriptImplFileName = 'coffeescript/coffee-script.min.js'
        file(customCoffeeScriptImplFileName) << getClass().getResource("/coffee-script.min.js").text

        withCoffeeScriptSource('app/assets/test.coffee')
        withCoffeeScriptSource('src/play/extraCoffeeScript/test2.coffee')
        buildFile << """
            plugins {
                id 'play'
                id 'play-coffeescript'
            }

            repositories{
                ${jcenterRepository()}
                maven {
                    name = "gradle-js"
                    url = "https://repo.gradle.org/gradle/javascript-public"
                }
            }
        """
    }

    def "can compile coffeescript with a custom implementation from file"() {
        buildFile << """
            model {
                components {
                    play {
                        sources {
                            extraCoffeeScript(CoffeeScriptSourceSet)
                        }
                        binaries.all {
                            tasks.withType(PlayCoffeeScriptCompile) {
                                coffeeScriptJs = files("${customCoffeeScriptImplFileName}")
                            }
                        }
                    }
                }
            }
        """

        when:
        succeeds "assemble"

        then:
        executedAndNotSkipped ":compilePlayBinaryPlayCoffeeScript", ":compilePlayBinaryPlayExtraCoffeeScript"
        hasProcessedCoffeeScript("test")
        hasProcessedCoffeeScript("extraCoffeeScript", "test2")
    }

    def "can compile coffeescript with a custom implementation from configuration"() {
        buildFile << """
            configurations {
                coffeeScript
            }

            dependencies {
                coffeeScript files("${customCoffeeScriptImplFileName}")
            }

            model {
                components {
                    play {
                        sources {
                            extraCoffeeScript(CoffeeScriptSourceSet)
                        }
                        binaries.all {
                            tasks.withType(PlayCoffeeScriptCompile) {
                                coffeeScriptJs = configurations.coffeeScript
                            }
                        }
                    }
                }
            }
        """

        when:
        succeeds "assemble"

        then:
        executedAndNotSkipped ":compilePlayBinaryPlayCoffeeScript", ":compilePlayBinaryPlayExtraCoffeeScript"
        hasProcessedCoffeeScript("test")
        hasProcessedCoffeeScript("extraCoffeeScript", "test2")
    }
}
