/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl.tapi


class ConfigurationCacheToolingApiInvocationValidationIntegrationTest extends AbstractConfigurationCacheToolingApiIntegrationTest {

    def "reports configuration cache problems in build script when fetching custom tooling model"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << ""
        buildFile << """
            plugins.apply(my.MyPlugin)
            gradle.buildFinished {
                println("build finished")
            }
        """

        when:
        withConfigurationCacheForModels()
        fetchModelFails()

        then:
        fixture.assertStateStoredAndDiscarded {
            projectConfigured = 2
            problem("Build file 'build.gradle': line 3: registration of listener on 'Gradle.buildFinished' is unsupported")
        }

        when:
        withConfigurationCacheForModels()
        fetchModelFails()

        then:
        fixture.assertStateStoredAndDiscarded {
            projectConfigured = 2
            problem("Build file 'build.gradle': line 3: registration of listener on 'Gradle.buildFinished' is unsupported")
        }
    }

    def "reports configuration cache problems in model builder while fetching tooling model"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc("""
            project.gradle.buildFinished {
                println("build finished")
            }
        """)
        settingsFile << ""
        buildFile << """
            plugins.apply(my.MyPlugin)
        """

        when:
        withConfigurationCacheForModels()
        fetchModelFails()

        then:
        fixture.assertStateStoredAndDiscarded {
            projectConfigured = 2
            problem("Plugin class 'my.MyPlugin': registration of listener on 'Gradle.buildFinished' is unsupported")
        }

        when:
        withConfigurationCacheForModels()
        fetchModelFails()

        then:
        fixture.assertStateStoredAndDiscarded {
            projectConfigured = 2
            problem("Plugin class 'my.MyPlugin': registration of listener on 'Gradle.buildFinished' is unsupported")
        }
    }
}
