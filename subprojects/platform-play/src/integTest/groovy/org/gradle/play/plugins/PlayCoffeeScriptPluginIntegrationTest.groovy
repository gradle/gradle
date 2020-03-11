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
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.util.TextUtil
import org.junit.Rule

class PlayCoffeeScriptPluginIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    def setup() {
        executer.expectDeprecationWarnings(12)
        buildFile << """
            plugins {
                id 'play'
                id 'play-coffeescript'
            }

            model {
                components {
                    play {
                        sources {
                            otherCoffeeScript(CoffeeScriptSourceSet)
                        }
                    }
                }
            }
        """
    }

    @ToBeFixedForInstantExecution(because = ":components")
    def "coffeescript source set appears in component listing"() {
        when:
        succeeds "components"

        then:
        normalizedOutput.contains("""
    CoffeeScript source 'play:coffeeScript'
        srcDir: app/assets
        includes: **/*.coffee
    CoffeeScript source 'play:otherCoffeeScript'
        srcDir: src/play/otherCoffeeScript
""")
    }

    def "creates and configures compile task when source exists"() {
        buildFile << """
            task checkTasks {
                doLast {
                    assert tasks.withType(CoffeeScriptCompile).size() == 2
                    tasks.withType(CoffeeScriptCompile)*.name as Set == ["compileCoffeeScriptPlayBinary", "compileOtherCoffeeScriptPlayBinary"] as Set
                }
            }
        """

        when:
        file("app/assets/test.coffee") << "test"
        file("src/play/otherCoffeeScript/other.coffee") << "test"

        then:
        succeeds "checkTasks"
    }

    def "does not create compile task when source does not exist"() {
        buildFile << """
            task checkTasks {
                doLast {
                    assert tasks.withType(CoffeeScriptCompile).size() == 0
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
