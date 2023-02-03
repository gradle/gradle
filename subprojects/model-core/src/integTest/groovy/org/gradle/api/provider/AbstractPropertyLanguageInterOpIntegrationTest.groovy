/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.provider

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.provider.AbstractLanguageInterOpIntegrationTest

abstract class AbstractPropertyLanguageInterOpIntegrationTest extends AbstractLanguageInterOpIntegrationTest {

    abstract void pluginSetsValues()

    abstract void pluginSetsCalculatedValuesUsingCallable()

    abstract void pluginSetsCalculatedValuesUsingMappedProvider()

    abstract void pluginDefinesTask()

    def "can define property and set value from language plugin"() {
        pluginSetsValues()

        buildFile << """
            apply plugin: SomePlugin
        """
        when:
        run("someTask")

        then:
        outputContains("flag = true")
        outputContains("message = some value")
        outputContains("number = 1.23")
        outputContains("list = [1, 2]")
        outputContains("set = [1, 2]")
        outputContains("map = {1=true, 2=false}")
    }

    def "can define property and set calculated value using function from language plugin"() {
        pluginSetsCalculatedValuesUsingCallable()

        buildFile << """
            apply plugin: SomePlugin
        """
        when:
        run("someTask")

        then:
        outputContains("flag = true")
        outputContains("message = some value")
        outputContains("number = 1.23")
        outputContains("list = [1, 2]")
        outputContains("set = [1, 2]")
        outputContains("map = {1=true, 2=false}")
    }

    def "can define property and set calculated value using mapped provider from language plugin"() {
        pluginSetsCalculatedValuesUsingMappedProvider()

        buildFile << """
            apply plugin: SomePlugin
        """
        when:
        run("someTask")

        then:
        outputContains("flag = true")
        outputContains("message = some value")
        outputContains("number = 1.23")
        outputContains("list = [1, 2]")
        outputContains("set = [1, 2]")
        outputContains("map = {1=true, 2=false}")
    }

    def "attaches diagnostic information to property"() {
        pluginDefinesTask()

        buildFile << """
            apply plugin: SomePlugin

            println "flag = " + tasks.someTask.flag
        """

        when:
        run()

        then:
        outputContains("flag = task ':someTask' property 'flag'")
    }

    def "can define property in language plugin and set value from Groovy DSL"() {
        pluginDefinesTask()

        buildFile << """
            apply plugin: SomePlugin
            tasks.someTask {
                flag = true
                message = "some value"
                number = 1.23d
                list = [1, 2]
                set = [1, 2]
                map = [1: true, 2: false]
            }
        """
        when:
        run("someTask")

        then:
        outputContains("flag = true")
        outputContains("message = some value")
        outputContains("number = 1.23")
        outputContains("list = [1, 2]")
        outputContains("set = [1, 2]")
        outputContains("map = {1=true, 2=false}")

        when:
        buildFile << """
            tasks.someTask {
                flag = provider { false }
                message = provider { "some new value" }
                list = provider { [3] }
                set = provider { [3] }
                map = provider { [3: true] }
            }
        """
        run("someTask")

        then:
        outputContains("flag = false")
        outputContains("message = some new value")
        outputContains("list = [3]")
        outputContains("set = [3]")
        outputContains("map = {3=true}")
    }

    def "can define property in language plugin and set value from Kotlin DSL"() {
        pluginDefinesTask()

        file("build.gradle.kts") << """
            plugins.apply(SomePlugin::class.java)
            tasks.withType(SomeTask::class.java).named("someTask").configure {
                flag.set(true)
                message.set("some value")
                number.set(1.23)
                list.set(listOf(1, 2))
                set.set(listOf(1, 2))
                map.set(mapOf(1 to true, 2 to false))
            }
        """

        when:
        run("someTask")

        then:
        outputContains("flag = true")
        outputContains("message = some value")
        outputContains("number = 1.23")
        outputContains("list = [1, 2]")
        outputContains("set = [1, 2]")
        outputContains("map = {1=true, 2=false}")

        when:
        file("build.gradle.kts") << """
            tasks.withType(SomeTask::class.java).named("someTask").configure {
                flag.set(provider { false })
                message.set(provider { "some new value" })
                number.set(provider { 4.56 })
                list.set(provider { listOf(3) })
                set.set(provider { listOf(3) })
                map.set(provider { mapOf(3 to true) })
            }
        """
        run("someTask")

        then:
        outputContains("flag = false")
        outputContains("message = some new value")
        outputContains("number = 4.56")
        outputContains("list = [3]")
        outputContains("set = [3]")
        outputContains("map = {3=true}")
    }

    def "can define property in language plugin and set value from Java plugin"() {
        pluginDefinesTask()

        file("buildSrc/settings.gradle.kts") << """
            include("other")
        """
        file("buildSrc/build.gradle.kts") << """
            dependencies {
                implementation(project(":other"))
            }
        """
        def otherDir = file("buildSrc/other")
        otherDir.file("build.gradle") << """
            plugins {
                id("java-library")
            }
            dependencies {
                api gradleApi()
                implementation project(":plugin")
            }
        """

        otherDir.file("src/main/java/SomeOtherPlugin.java") << """
            import ${Project.name};
            import ${Plugin.name};
            import ${Arrays.name};
            import ${Map.name};
            import ${LinkedHashMap.name};

            public class SomeOtherPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getTasks().withType(SomeTask.class).configureEach(t -> {
                        t.getFlag().set(false);
                        t.getMessage().set("some other value");
                        t.getNumber().set(1.23);
                        t.getList().set(Arrays.asList(1, 2));
                        t.getSet().set(Arrays.asList(1, 2));
                        Map<Integer, Boolean> map = new LinkedHashMap<>();
                        map.put(1, true);
                        map.put(2, false);
                        t.getMap().set(map);
                    });
                }
            }
        """

        buildFile << """
            apply plugin: SomeOtherPlugin
            apply plugin: SomePlugin
        """

        when:
        run("someTask")

        then:
        outputContains("flag = false")
        outputContains("message = some other value")
        outputContains("number = 1.23")
        outputContains("list = [1, 2]")
        outputContains("set = [1, 2]")
        outputContains("map = {1=true, 2=false}")
    }

    def "can define property in language plugin and set value from Kotlin plugin"() {
        pluginDefinesTask()

        file("buildSrc/settings.gradle.kts") << """
            include("other")
        """
        file("buildSrc/build.gradle.kts") << """
            dependencies {
                implementation(project(":other"))
            }
        """

        def otherDir = file("buildSrc/other")
        usesKotlin(file(otherDir))
        // This is because the Kotlin compiler is run in-process (to avoid issues with the Kotlin compiler daemon) and also keeps jars open
        executer.requireDaemon().requireIsolatedDaemons()
        otherDir.file("build.gradle.kts") << """
            dependencies {
                implementation(project(":plugin"))
            }
        """

        otherDir.file("src/main/kotlin/SomeOtherPlugin.kt") << """
            import ${Project.name}
            import ${Plugin.name}

            class SomeOtherPlugin: Plugin<Project> {
                override fun apply(project: Project) {
                    project.tasks.withType(SomeTask::class.java).configureEach {
                        flag.set(false)
                        message.set("some other value")
                        number.set(1.23)
                        list.set(listOf(1, 2))
                        set.set(listOf(1, 2))
                        map.set(mapOf(1 to true, 2 to false))
                    }
                }
            }
        """

        buildFile << """
            apply plugin: SomeOtherPlugin
            apply plugin: SomePlugin
        """

        when:
        // Due to exception logged by Kotlin plugin
        executer.withStackTraceChecksDisabled()
        run("someTask")

        then:
        outputContains("flag = false")
        outputContains("message = some other value")
        outputContains("number = 1.23")
        outputContains("list = [1, 2]")
        outputContains("set = [1, 2]")
        outputContains("map = {1=true, 2=false}")
    }
}
