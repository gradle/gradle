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

class CustomCoffeeScriptImplementationIntegrationTest extends AbstractIntegrationSpec {
    def customCoffeeScriptImplFileName

    def setup() {
        customCoffeeScriptImplFileName = 'coffeescript/coffee-script.min.js'
        file(customCoffeeScriptImplFileName) << getClass().getResource("/coffee-script.min.js").text

        file('app/assets/test.coffee') << testCoffeeScript()
        file('src/play/extra/test2.coffee') << testCoffeeScript()
        buildFile << """
            plugins {
                id 'play'
                id 'play-coffeescript'
            }

            repositories{
                jcenter()
            }
        """
    }

    def "can compile coffeescript with a custom implementation from file"() {
        buildFile << """
            model {
                components {
                    play {
                        sources {
                            extra(CoffeeScriptSourceSet)
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
        file('build/playBinary/src/compilePlayBinaryCoffeeScriptAssets/test.js').exists()
        file('build/playBinary/src/compilePlayBinaryExtra/test2.js').exists()
        file('build/playBinary/src/processPlayBinaryCoffeeScriptAssets/test.js').text == expectedJavaScript()
        file('build/playBinary/src/processPlayBinaryExtra/test2.js').text == expectedJavaScript()
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
                            extra(CoffeeScriptSourceSet)
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
        file('build/playBinary/src/compilePlayBinaryCoffeeScriptAssets/test.js').exists()
        file('build/playBinary/src/compilePlayBinaryExtra/test2.js').exists()
        file('build/playBinary/src/processPlayBinaryCoffeeScriptAssets/test.js').text == expectedJavaScript()
        file('build/playBinary/src/processPlayBinaryExtra/test2.js').text == expectedJavaScript()
    }

    String testCoffeeScript() {
        return 'alert "this is some coffeescript!"'
    }

    String expectedJavaScript() {
        return """(function() {
  alert("this is some coffeescript!");

}).call(this);
"""
    }
}
