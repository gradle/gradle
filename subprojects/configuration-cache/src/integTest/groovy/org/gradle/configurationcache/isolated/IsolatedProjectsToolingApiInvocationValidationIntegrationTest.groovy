/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache.isolated

class IsolatedProjectsToolingApiInvocationValidationIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {
    def "reports cross project access from build script when fetching custom tooling model"() {
        given:
        settingsFile << """
            include('a')
            include('b')
        """
        withSomeToolingModelBuilderPluginInBuildSrc()
        buildFile << """
            allprojects {
                plugins.apply('java-library')
            }
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI)
        fetchModelFails()

        then:
        fixture.assertStateStoredAndDiscarded {
            projectConfigured(":buildSrc")
            modelsCreated(":")
            problem("Build file 'build.gradle': line 3: Cannot access project ':a' from project ':'")
            problem("Build file 'build.gradle': line 3: Cannot access project ':b' from project ':'")
        }

        when:
        executer.withArguments(ENABLE_CLI)
        fetchModelFails()

        then:
        fixture.assertStateStoredAndDiscarded {
            projectConfigured(":buildSrc")
            modelsCreated(":")
            problem("Build file 'build.gradle': line 3: Cannot access project ':a' from project ':'")
            problem("Build file 'build.gradle': line 3: Cannot access project ':b' from project ':'")
        }
    }

    def "reports cross project access from model builder while fetching custom tooling model"() {
        given:
        settingsFile << """
            include('a')
            include('b')
        """
        withSomeToolingModelBuilderPluginInBuildSrc("""
            project.subprojects.each { it.extensions }
        """)
        buildFile << """
            plugins.apply(my.MyPlugin)
        """

        when:
        executer.withArguments(ENABLE_CLI)
        fetchModelFails()

        then:
        fixture.assertStateStoredAndDiscarded {
            projectConfigured(":buildSrc")
            modelsCreated(":")
            problem("Plugin class 'my.MyPlugin': Cannot access project ':a' from project ':'")
            problem("Plugin class 'my.MyPlugin': Cannot access project ':b' from project ':'")
        }

        when:
        executer.withArguments(ENABLE_CLI)
        fetchModelFails()

        then:
        fixture.assertStateStoredAndDiscarded {
            projectConfigured(":buildSrc")
            modelsCreated(":")
            problem("Plugin class 'my.MyPlugin': Cannot access project ':a' from project ':'")
            problem("Plugin class 'my.MyPlugin': Cannot access project ':b' from project ':'")
        }
    }

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
        executer.withArguments(ENABLE_CLI)
        fetchModelFails()

        then:
        fixture.assertStateStoredAndDiscarded {
            projectConfigured(":buildSrc")
            modelsCreated(":")
            problem("Build file 'build.gradle': line 3: registration of listener on 'Gradle.buildFinished' is unsupported")
        }

        when:
        executer.withArguments(ENABLE_CLI)
        fetchModelFails()

        then:
        fixture.assertStateStoredAndDiscarded {
            projectConfigured(":buildSrc")
            modelsCreated(":")
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
        executer.withArguments(ENABLE_CLI)
        fetchModelFails()

        then:
        fixture.assertStateStoredAndDiscarded {
            projectConfigured(":buildSrc")
            modelsCreated(":")
            problem("Plugin class 'my.MyPlugin': registration of listener on 'Gradle.buildFinished' is unsupported")
        }

        when:
        executer.withArguments(ENABLE_CLI)
        fetchModelFails()

        then:
        fixture.assertStateStoredAndDiscarded {
            projectConfigured(":buildSrc")
            modelsCreated(":")
            problem("Plugin class 'my.MyPlugin': registration of listener on 'Gradle.buildFinished' is unsupported")
        }
    }
}
