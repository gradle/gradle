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

import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.WellBehavedPluginTest
import org.gradle.util.TextUtil
import org.junit.Rule

class PlayCoffeeScriptPluginIntegrationTest extends WellBehavedPluginTest {
    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    @Override
    String getPluginName() {
        return 'play-coffeescript'
    }

    def setup() {
        settingsFile << """
            rootProject.name = 'play-app'
        """
        buildFile << """
            plugins {
                id 'play-coffeescript'
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

    def "coffeescript source set appears in component listing"() {
        when:
        succeeds "components"

        then:
        output.contains(TextUtil.toPlatformLineSeparators("""
    CoffeeScript source 'play:coffeeScriptSources'
        app
"""))
        output.contains(TextUtil.toPlatformLineSeparators("""
    JavaScript source 'play:coffeeScriptGenerated'
"""))
    }

    def "creates and configures compile task when source exists"() {
        buildFile << """
            task checkTasks {
                doLast {
                    def coffeeScriptCompileTasks = tasks.withType(CoffeeScriptCompile).matching { it.name == "compilePlayBinaryPlayCoffeeScriptSources" }
                    assert coffeeScriptCompileTasks.size() == 1

                    def javaScriptProcessTasks = tasks.withType(Copy).matching { it.name == "processPlayBinaryPlayCoffeeScriptGenerated" }
                    assert javaScriptProcessTasks.size() == 1
                }
            }
        """

        when:
        file("app/test.coffee") << "test"

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
}
