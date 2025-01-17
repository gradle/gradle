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

package org.gradle.internal.cc.impl.isolated

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
        withIsolatedProjects()
        fetchModelFails()

        then:
        fixture.assertModelStoredAndDiscarded {
            projectConfigured(":buildSrc")
            modelsCreated(":")
            problem("Build file 'build.gradle': line 3: Project ':' cannot access 'Project.plugins' functionality on subprojects via 'allprojects'", 2)
        }

        when:
        withIsolatedProjects()
        fetchModelFails()

        then:
        fixture.assertModelStoredAndDiscarded {
            projectConfigured(":buildSrc")
            modelsCreated(":")
            problem("Build file 'build.gradle': line 3: Project ':' cannot access 'Project.plugins' functionality on subprojects via 'allprojects'", 2)
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
        withIsolatedProjects()
        fetchModelFails()

        then:
        fixture.assertModelStoredAndDiscarded {
            projectConfigured(":buildSrc")
            modelsCreated(":")
            problem("Plugin class 'my.MyPlugin': Project ':' cannot access 'Project.extensions' functionality on subprojects", 2)
        }

        when:
        withIsolatedProjects()
        fetchModelFails()

        then:
        fixture.assertModelStoredAndDiscarded {
            projectConfigured(":buildSrc")
            modelsCreated(":")
            problem("Plugin class 'my.MyPlugin': Project ':' cannot access 'Project.extensions' functionality on subprojects", 2)
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
        withIsolatedProjects()
        fetchModelFails()

        then:
        fixture.assertModelStoredAndDiscarded {
            projectConfigured(":buildSrc")
            modelsCreated(":")
            problem("Build file 'build.gradle': line 3: registration of listener on 'Gradle.buildFinished' is unsupported")
        }

        when:
        withIsolatedProjects()
        fetchModelFails()

        then:
        fixture.assertModelStoredAndDiscarded {
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
        withIsolatedProjects()
        fetchModelFails()

        then:
        fixture.assertModelStoredAndDiscarded {
            projectConfigured(":buildSrc")
            modelsCreated(":")
            problem("Plugin class 'my.MyPlugin': registration of listener on 'Gradle.buildFinished' is unsupported")
        }

        when:
        withIsolatedProjects()
        fetchModelFails()

        then:
        fixture.assertModelStoredAndDiscarded {
            projectConfigured(":buildSrc")
            modelsCreated(":")
            problem("Plugin class 'my.MyPlugin': registration of listener on 'Gradle.buildFinished' is unsupported")
        }
    }
}
