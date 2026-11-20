/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.resolve.typesafe

class TypeSafeProjectAccessorsSuggestionsIntegrationTest extends AbstractTypeSafeProjectAccessorsIntegrationTest {

    def setup() {
        createDirs("app", "app/child", "my-lib")
        settingsFile << """
            rootProject.name = 'test'
            include 'app', 'app:child', 'my-lib'
        """
    }

    def "suggests close accessors for misspelled top level accessor"() {
        buildFile << """
            println(projects.ap.path)
        """

        expect:
        fails("help")
        failureCauseContains("Project accessor 'ap' could not be found. Some candidates are: 'app'.")
    }

    def "suggests close accessors for misspelled nested accessor"() {
        buildFile << """
            println(projects.app.chld.path)
        """

        expect:
        fails("help")
        failureCauseContains("Project accessor 'app.chld' could not be found. Some candidates are: 'app.child'.")
    }

    def "suggests camel case accessor for misspelled kebab case project"() {
        buildFile << """
            println(projects.myLb.path)
        """

        expect:
        fails("help")
        failureCauseContains("Project accessor 'myLb' could not be found. Some candidates are: 'myLib'.")
    }

    def "projects extension is extension aware"() {
        buildFile << """
            assert projects instanceof ExtensionAware
            projects.ext.greeting = 'hello'
            println("greeting: " + projects.greeting)
            println("path: " + projects.app.path)
        """

        expect:
        succeeds("help")
        outputContains("greeting: hello")
        outputContains("path: :app")
    }

    def "Kotlin build script can use extra properties of projects extension"() {
        buildKotlinFile << """
            (projects as ExtensionAware).extra["greeting"] = "hello"
            println("greeting: " + (projects as ExtensionAware).extra["greeting"])
        """

        expect:
        succeeds("help")
        outputContains("greeting: hello")
    }

    def "Kotlin build script can use accessors"() {
        buildKotlinFile << """
            println("path: " + projects.app.child.path)
        """

        expect:
        succeeds("help")
        outputContains("path: :app:child")
    }

    def "closure configuring a project dependency still resolves properties of the build script"() {
        buildFile << """
            ext.appReason = 'needed by app'
            configurations.dependencyScope("deps")
            dependencies {
                deps(projects.app) {
                    because(appReason)
                }
            }
            println("reason: " + configurations.deps.dependencies.first().reason)
        """

        expect:
        succeeds("help")
        outputContains("reason: needed by app")
    }
}
