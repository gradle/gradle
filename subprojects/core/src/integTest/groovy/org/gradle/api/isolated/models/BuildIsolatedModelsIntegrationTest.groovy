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

package org.gradle.api.isolated.models


import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class BuildIsolatedModelsIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        // Required, because the Gradle API jar is computed once a day,
        // and the new API might not be visible for tests that require compilation
        // against that API, e.g. the cases like a plugin defined in an included build
        executer.requireOwnGradleUserHomeDir()
    }

    def "can consume build-provided model from setting plugin in a project plugin"() {
        buildFile file("build-logic/build.gradle"), """
            plugins {
                id 'groovy-gradle-plugin'
            }

            gradlePlugin {
                plugins {
                    mySettingsPlugin {
                        id = 'my-settings-plugin'
                        implementationClass = 'my.MySettingsPlugin'
                    }
                    myProjectPlugin {
                        id = 'my-project-plugin'
                        implementationClass = 'my.MyProjectPlugin'
                    }
                }
            }
        """

        groovyFile "build-logic/src/main/groovy/my/MyModel.groovy", """
            package my
            class MyModel {
                String value
                MyModel(value) { this.value = value }
            }
        """

        groovyFile "build-logic/src/main/groovy/my/MySettingsPlugin.groovy", """
            package my
            import org.gradle.api.Plugin
            import org.gradle.api.initialization.Settings
            import org.gradle.api.isolated.models.IsolatedModelRouter
            import javax.inject.Inject

            abstract class MySettingsPlugin implements Plugin<Settings> {
                @Inject
                abstract IsolatedModelRouter getIsolatedModels()

                void apply(Settings s) {
                    isolatedModels.register("myValue", MyModel, s.providers.provider {
                        println("Computing myValue")
                        return new MyModel("hey")
                    })
                }
            }
        """

        groovyFile "build-logic/src/main/groovy/my/MyProjectPlugin.groovy", """
            package my
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.isolated.models.IsolatedModelRouter
            import javax.inject.Inject

            abstract class MyProjectPlugin implements Plugin<Project> {
                @Inject
                abstract IsolatedModelRouter getIsolatedModels()

                void apply(Project project) {
                    def valueProvider = isolatedModels.fromBuild("myValue", MyModel)
                    MyModel computedValue = valueProvider.get()
                    println("Project '" + project.path + "' got value '" + computedValue.value + "'")
                }
            }
        """

        settingsFile """
            pluginManagement {
                includeBuild("build-logic")
            }
            plugins {
                id("my-settings-plugin")
            }
        """

        buildFile """
            plugins {
                id("my-project-plugin")
            }

            tasks.register("something")
        """

        when:
        run "something"

        then:
        outputContains("Computing myValue")
        outputContains("Project ':' got value 'hey'")
    }

    def "can consume build-provided model of shared type from setting script in a build script"() {
        settingsFile """
            IsolatedModelRouter isolatedModels = settings.services.get(IsolatedModelRouter)
            isolatedModels.register("myValue", String, providers.provider {
                println("Computing myValue")
                "hey"
            })
        """

        buildFile """
            IsolatedModelRouter isolatedModels = project.services.get(IsolatedModelRouter)
            def modelProvider = isolatedModels.fromBuild("myValue", String)
            def computedValue = modelProvider.get()
            println("Project '" + project.path + "' got value '" + computedValue + "'")

            tasks.register("something")
        """

        when:
        run "something"

        then:
        outputContains("Computing myValue")
        outputContains("Project ':' got value 'hey'")
    }

    def "model provider is finalized lazily"() {
        settingsFile """
            settings.ext.foo = ["a"]
            def fooList = foo

            IsolatedModelRouter isolatedModels = settings.services.get(IsolatedModelRouter)
            isolatedModels.register("myValue", String, providers.provider {
                fooList.add("c")
                fooList.toString()
            })

            foo.add("b")
            println("(before model) settings.foo = \$foo")

            def myValueProvider = isolatedModels.fromBuild("myValue", String)
            def myValue = myValueProvider.get()
            println("settings.myValue = \${myValue}")

            println("(after model) settings.foo = \$foo")
        """

        when:
        run "help"

        then:
        outputContains("(before model) settings.foo = [a, b]")
        outputContains("settings.myValue = [a, b, c]")
        outputContains("(after model) settings.foo = [a, b, c]")
    }

    def "build-scope isolated model provider is evaluated only if model is realized"() {
        settingsFile """
            IsolatedModelRouter isolatedModels = settings.services.get(IsolatedModelRouter)

            isolatedModels.register("someKey", String, providers.provider {
                println("Producing model for someKey")
                "someValue"
            })

            if (Boolean.getBoolean("realizeModel")) {
                println("Realizing model for someKey")
                def myValueProvider = isolatedModels.fromBuild("someKey", String)
                def value = myValueProvider.get()
                println("model[someKey] = \${value}")
            }
        """

        when:
        run "help"
        then:
        outputDoesNotContain("Realizing model for someKey")

        when:
        run "help", "-DrealizeModel=true"
        then:
        outputContains("Realizing model for someKey")
        outputContains("model[someKey] = someValue")
    }

    def "build-scoped model is realized only once"() {
        settingsFile """
            include(":a")
            IsolatedModelRouter isolatedModels = settings.services.get(IsolatedModelRouter)
            isolatedModels.register("someKey", String, providers.provider {
                println("Computing model for someKey")
                "someValue"
            })
        """

        buildFile """
            IsolatedModelRouter isolatedModels = project.services.get(IsolatedModelRouter)
            def model = isolatedModels.fromBuild("someKey", String).get()
            println("project '\$path' model[someKey] = \$model")
        """

        buildFile "a/build.gradle", """
            IsolatedModelRouter isolatedModels = project.services.get(IsolatedModelRouter)
            def model = isolatedModels.fromBuild("someKey", String).get()
            println("project '\$path' model[someKey] = \$model")
        """

        when:
        run "help"

        then:
        output.count("Computing model for someKey") == 1
        outputContains("project ':' model[someKey] = someValue")
        outputContains("project ':a' model[someKey] = someValue")
    }

    def "build-scoped model state is isolated per consuming project"() {
        settingsFile """
            rootProject.name = "root"
            include(":a")

            def sharedList = ["settings"]

            IsolatedModelRouter isolatedModels = settings.services.get(IsolatedModelRouter)
            isolatedModels.register("someKey", List<String>, providers.provider {
                println("Computing model for someKey")
                sharedList
            })
        """

        buildFile "buildSrc/build.gradle", """
            plugins { id 'groovy-gradle-plugin' }
        """

        buildFile "buildSrc/src/main/groovy/my-plugin.gradle", """
            def model1 = isolated.models.fromBuild("someKey", List<String>).get()
            model1 << project.name
            println("project '\$path' model[someKey][v1] = \$model1")

            def model2 = isolated.models.fromBuild("someKey", List<String>).get()
            model2 << project.name
            println("project '\$path' model[someKey][v2] = \$model2")
        """

        buildFile """
            plugins { id 'my-plugin' }
        """

        buildFile "a/build.gradle", """
            plugins { id 'my-plugin' }
        """

        when:
        run "help"

        then:
        output.count("Computing model for someKey") == 1
        outputContains("project ':' model[someKey][v1] = [settings, root]")
        outputContains("project ':' model[someKey][v2] = [settings, root, root]")
        outputContains("project ':a' model[someKey][v1] = [settings, a]")
        outputContains("project ':a' model[someKey][v2] = [settings, a, a]")
    }

}
