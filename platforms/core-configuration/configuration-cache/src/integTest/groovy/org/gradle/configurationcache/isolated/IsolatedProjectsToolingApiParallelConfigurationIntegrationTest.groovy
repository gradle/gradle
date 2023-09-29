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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

class IsolatedProjectsToolingApiParallelConfigurationIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.start()
    }

    def "projects are configured and models created in parallel when project scoped model is queried concurrently"() {
        withSomeToolingModelBuilderPluginInBuildSrc("""
            ${server.callFromBuildUsingExpression("'model-' + project.name")}
        """)
        settingsFile << """
            include("a")
            include("b")
            rootProject.name = "root"
        """
        apply(testDirectory)
        apply(file("a"))
        apply(file("b"))

        // Prebuild buildSrc
        server.expect("configure-root")
        server.expect("configure-a")
        server.expect("configure-b")
        run()

        given:
        server.expect("configure-root")
        server.expectConcurrent("model-root", "configure-a", "configure-b")
        server.expectConcurrent("model-a", "model-b")

        when:
        executer.withArguments(ENABLE_CLI)
        def model = runBuildAction(new FetchCustomModelForEachProjectInParallel())

        then:
        model.size() == 3
        model[0].message == "It works from project :"
        model[1].message == "It works from project :a"
        model[2].message == "It works from project :b"

        and:
        fixture.assertStateStored {
            projectConfigured(":buildSrc")
            buildModelCreated()
            modelsCreated(":", ":a", ":b")
        }

        when:
        executer.withArguments(ENABLE_CLI)
        def model2 = runBuildAction(new FetchCustomModelForEachProjectInParallel())

        then:
        model2.size() == 3
        model2[0].message == "It works from project :"
        model2[1].message == "It works from project :a"
        model2[2].message == "It works from project :b"

        and:
        fixture.assertStateLoaded()

        when:
        file("a/build.gradle") << """
            myExtension.message = 'this is project a'
        """
        file("b/build.gradle") << """
            myExtension.message = 'this is project b'
        """

        server.expect("configure-root")
        server.expectConcurrent("configure-a", "configure-b")
        server.expectConcurrent("model-a", "model-b")

        executer.withArguments(ENABLE_CLI)
        def model3 = runBuildAction(new FetchCustomModelForEachProjectInParallel())

        then:
        model3.size() == 3
        model3[0].message == "It works from project :"
        model3[1].message == "this is project a"
        model3[2].message == "this is project b"

        and:
        fixture.assertStateUpdated {
            fileChanged("a/build.gradle")
            fileChanged("b/build.gradle")
            projectConfigured(":buildSrc")
            projectConfigured(":")
            modelsCreated(":a", ":b")
            modelsReused(":", ":buildSrc")
        }

        when:
        executer.withArguments(ENABLE_CLI)
        def model4 = runBuildAction(new FetchCustomModelForEachProjectInParallel())

        then:
        model4.size() == 3
        model4[0].message == "It works from project :"
        model4[1].message == "this is project a"
        model4[2].message == "this is project b"

        and:
        fixture.assertStateLoaded()
    }

    def "projects are configured in parallel when projects use plugins from included build and project scoped model is queried concurrently"() {
        withSomeToolingModelBuilderPluginInChildBuild("plugins", """
            ${server.callFromBuildUsingExpression("'model-' + project.name")}
        """)
        settingsFile << """
            includeBuild("plugins")
            include("a")
            include("b")
            rootProject.name = "root"
        """
        // don't apply to root, as this is configured prior to the other projects, so the plugins are not resolved/built in parallel
        apply(file("a"))
        apply(file("b"))

        // Prebuild plugins
        server.expect("configure-a")
        server.expect("configure-b")
        run()

        given:
        server.expectConcurrent("configure-a", "configure-b")
        server.expectConcurrent("model-a", "model-b")

        when:
        executer.withArguments(ENABLE_CLI)
        def model = runBuildAction(new FetchCustomModelForEachProjectInParallel())

        then:
        model.size() == 3
        model[0] == null
        model[1].message == "It works from project :a"
        model[2].message == "It works from project :b"

        and:
        fixture.assertStateStored {
            projectsConfigured(":plugins", ":", ":a", ":b")
            buildModelCreated()
            modelsCreated(":a", ":b")
        }
    }

    /**
     * Test two projects (:a and :b) depending on plugins from the same two included builds (:plugin-1 and :plugin-2)
     * which in turn depend on a plugin from another included build (:plugin-0).
     **/
    def "projects are configured in parallel when projects use plugins from included builds and project scoped model is queried concurrently"() {
        given:
        addConventionPluginImplementation('plugin-0', 'Plugin0')
        addPluginBuildScript('plugin-0', 'my.plugin-0', 'my.Plugin0')
        file('plugin-0/settings.gradle') << '''
        '''

        and:
        addConventionPluginImplementation('plugin-1', 'Plugin1')
        file('plugin-1/settings.gradle') << '''
            includeBuild("../plugin-0")
        '''
        file("plugin-1/build.gradle") << '''
            plugins {
                id("groovy-gradle-plugin")
                id("my.plugin-0")
            }
            gradlePlugin {
                plugins {
                    test {
                        id = "my.plugin-1"
                        implementationClass = "my.Plugin1"
                    }
                }
            }
        '''

        and:
        addConventionPluginImplementation('plugin-2', 'Plugin2')
        file('plugin-2/settings.gradle') << '''
            includeBuild("../plugin-0")
        '''
        file("plugin-2/build.gradle") << '''
            plugins {
                id("groovy-gradle-plugin")
                id("my.plugin-0")
            }
            gradlePlugin {
                plugins {
                    test {
                        id = "my.plugin-2"
                        implementationClass = "my.Plugin2"
                    }
                }
            }
        '''

        and:
        withSomeToolingModelBuilderPluginInChildBuild("plugins", """
            ${server.callFromBuildUsingExpression("'model-' + project.name")}
        """)
        settingsFile '''
            includeBuild("plugin-1")
            includeBuild("plugin-2")
            includeBuild("plugins")
            include("a")
            include("b")
            rootProject.name = "root"
        '''

        def buildScriptA = file('a/build.gradle')
        groovyFile buildScriptA, '''
            plugins {
                id("my.plugin-1")
                id("my.plugin-2")
                id("my.plugin")
            }
        '''
        configuring(buildScriptA)

        def buildScriptB = file('b/build.gradle')
        groovyFile buildScriptB, '''
            plugins {
                id("my.plugin")
                id("my.plugin-2")
                id("my.plugin-1")
            }
        '''
        configuring(buildScriptB)

        and:
        server.expectConcurrent("configure-a", "configure-b")
        server.expectConcurrent("model-a", "model-b")

        when:
        executer.withArguments(ENABLE_CLI)
        def model = runBuildAction(new FetchCustomModelForEachProjectInParallel())

        then:
        model.size() == 3
        model[0] == null
        model[1].message == "It works from project :a"
        model[2].message == "It works from project :b"

        and:
        fixture.assertStateStored {
            projectsConfigured(':plugin-0', ':plugin-1', ':plugin-2', ':plugins', ':', ':a', ':b')
            buildModelCreated()
            modelsCreated(':a', ':b')
        }
    }

    private void addConventionPluginImplementation(String targetBuildName, String className) {
        file("$targetBuildName/src/main/groovy/my/${className}.groovy") << """
            package my

            abstract class $className implements $Plugin.name<$Project.name> {
                void apply($Project.name project) {
                    println "$targetBuildName:${'$'}project.name"
                }
            }
        """.stripIndent()
    }

    TestFile apply(TestFile dir) {
        def buildFile = dir.file("build.gradle")
        buildFile << """
            plugins {
                id("my.plugin")
            }
        """
        configuring(buildFile)
        return buildFile
    }

    TestFile configuring(TestFile buildFile) {
        buildFile << """
            ${server.callFromBuildUsingExpression("'configure-' + project.name")}
        """
    }
}
