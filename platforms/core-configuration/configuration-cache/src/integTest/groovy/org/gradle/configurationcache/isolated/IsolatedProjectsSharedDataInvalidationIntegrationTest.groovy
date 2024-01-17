/*
 * Copyright 2023 the original author or authors.
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

import spock.lang.Ignore

@Ignore("wip")
class IsolatedProjectsSharedDataInvalidationIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {
    def "invalidates a shared data consumer project when the data is used in model building"() {
        given:
        abProjectsUsingSharedData("project.sharedData.obtain(String, '$sharedDataIdentifier', project.sharedData.fromProject(project.project(':a')))")

        when:
        executer.withArguments(ENABLE_CLI)
        def models = runBuildAction(new FetchCustomModelForEachProject())

        then:
        models.collect { it.message } == ["test :a", "test :b"]
        fixture.assertStateStored {
            projectsConfigured(":buildSrc", ":", ":a", ":b")
            buildModelCreated()
            modelsCreated(":a", ":b")
        }

        when:
        file("a/in.txt") << " changed!"
        executer.withArguments(ENABLE_CLI)
        def newModels = runBuildAction(new FetchCustomModelForEachProject())

        then:
        newModels.collect { it.message } == ["test :a changed!", "test :b changed!"]
        fixture.assertStateUpdated {
            fileChanged("a/in.txt")
            projectsConfigured(":buildSrc", ":", ":a", ":b")
            modelsCreated(":a", ":b")
            modelsReused(":buildSrc", ":")
        }
    }

    def "invalidates model in a shared data consumer project when shared data is used in configuration logic"() {
        given: 'a consumer project that gets the shared data at configuration time, while model builders do not access it'
        abProjectsUsingSharedData("project.provider { \"test\" }") // no shared data usage in model building!
        groovyFile(file("b/build.gradle"), """
            println('obtained shared data: ' + sharedData.obtain(String, "$sharedDataIdentifier", sharedData.fromProject(project(':a'))).get())
        """)

        when:
        executer.withArguments(ENABLE_CLI)
        runBuildAction(new FetchCustomModelForEachProject())

        then:
        fixture.assertStateStored {
            projectsConfigured(":buildSrc", ":", ":a", ":b")
            outputContains('obtained shared data: test :a')
            buildModelCreated()
            modelsCreated(":a", ":b")
        }

        when: 'the shared data provider gets invalidated'
        file("a/in.txt") << " changed!"
        executer.withArguments(ENABLE_CLI)
        runBuildAction(new FetchCustomModelForEachProject())

        then:
        fixture.assertStateUpdated {
            fileChanged("a/in.txt")
            projectsConfigured(":buildSrc", ":", ":a", ":b")
            modelsCreated(":a", ":b")
            outputContains('obtained shared data: test :a changed!')
            modelsReused(":buildSrc", ":")
        }
    }

    def "does not invalidate projects whose shared data did not change"() {
        given:
        abcProjectsUsingSharedData("""
            project.sharedData.obtain(String, '$sharedDataIdentifier', project.sharedData.fromProject(project.project(':a'))).zip(
                project.sharedData.obtain(String, '$sharedDataIdentifier', project.sharedData.fromProject(project.project(':b'))),
                { a, b -> a + ' ' + b }
            )
        """)

        when:
        executer.withArguments(ENABLE_CLI)
        def models = runBuildAction(new FetchCustomModelForProjectsByPath([":c"]))

        then:
        models.collect { it.message } == ["test :a test :b"]
        fixture.assertStateStored {
            projectsConfigured(":buildSrc", ":", ":a", ":b", ":c")
            buildModelCreated()
            modelsCreated(":c")
        }

        when:
        file("a/in.txt") << " changed!"
        executer.withArguments(ENABLE_CLI)
        def newModels = runBuildAction(new FetchCustomModelForProjectsByPath([":c"]))

        then:
        newModels.collect { it.message } == ["test :a changed! test :b"]
        fixture.assertStateUpdated {
            fileChanged("a/in.txt")
            projectsConfigured(":buildSrc", ":", ":a", ":c")
            modelsCreated(":a", 0)
            modelsCreated(":c", 1)
            modelsReused(":", ":b")
        }
    }

    private void abProjectsUsingSharedData(String sharedDataUsageExpression) {
        withSharedDataFromFileTransformedIntoModel(sharedDataUsageExpression)
        settingsFile("""
            include(":a")
            include(":b")
        """)
        groovyFile(file("a/build.gradle"), "plugins.apply(my.MyPlugin)")
        groovyFile(file("b/build.gradle"), "plugins.apply(my.MyPlugin)")
        file("a/in.txt") << "test :a"
        file("b/in.txt") << "test :b"
    }

    private void abcProjectsUsingSharedData(String sharedDataUsageExpression) {
        withSharedDataFromFileTransformedIntoModel(sharedDataUsageExpression)
        settingsFile("""
            include(":a")
            include(":b")
            include(":c")
        """)
        groovyFile(file("a/build.gradle"), "plugins.apply(my.MyPlugin)")
        groovyFile(file("b/build.gradle"), "plugins.apply(my.MyPlugin)")
        groovyFile(file("c/build.gradle"), "plugins.apply(my.MyPlugin)")
        file("a/in.txt") << "test :a"
        file("b/in.txt") << "test :b"
        file("c/in.txt") << "test :c"
    }

    /**
     * Register shared data that is read from an `in.txt` file in each subproject.
     * Also register a model builder in each subproject that produces a model from the {@code produceModelValueExpression} expression
     */
    private void withSharedDataFromFileTransformedIntoModel(String produceModelValueExpression) {
        withMyModelBuilderAndPluginImplementationInBuildSrc(
            """
            def sharedDataProvider = $produceModelValueExpression
            return new MyModel(sharedDataProvider.getOrElse(null))
            """.toString(),
            """
            def inFile = project.file('in.txt')
            if (inFile.exists()) {
                project.sharedData.register(String, '$sharedDataIdentifier', project.provider { inFile.text })
            }
            """.toString()
        )
    }

    private final String sharedDataIdentifier = "com.example.data"
}
