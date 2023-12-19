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

package org.gradle.api.shareddata

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class SharedDataUsageIntegrationTest extends AbstractIntegrationSpec {

    def 'can consume shared data defined in the same project and later mutate it'() {
        given:
        buildFile("""
            def sharedData = services.get($ProjectSharedData.name)
            sharedData.register(String, "one", provider { "one" })
            println sharedData.obtain(String, "one", sharedData.fromProject(project)).get()
            sharedData.register(String, "two", provider { "two" })
            println sharedData.obtain(String, "two", sharedData.fromProject(project)).get()
        """)

        when:
        run("help")

        then:
        outputContains("one\ntwo")
    }

    def 'can consume shared data from explicitly specified project'() {
        given:
        settingsFile(
            """
            include(":a")
            include(":b")
            """
        )
        groovyFile(file("a/build.gradle"), """
            def sharedData = services.get($ProjectSharedData.name)
            sharedData.register(String, "test", provider { "data from :a" })
        """)
        groovyFile(file("b/build.gradle"), """
            def sharedData = services.get($ProjectSharedData.name)
            println "obtained " + sharedData.obtain(String, "test", sharedData.fromProject(project(":a"))).get()
        """)

        when:
        run("help")

        then:
        outputContains("obtained data from :a")
    }

    def 'shared data queried #queryKind is a provider with a missing value'() {
        given:
        settingsFile(
            """
            include(":a")
            include(":b")
            """
        )
        groovyFile(file("a/build.gradle"), """
            sharedData.register(String, "test", provider { "data from :a" })
        """)
        groovyFile(file("b/build.gradle"), """
            assert sharedData.obtain(String, "test", sharedData.fromProject(":a")).isPresent() // sanity check
            assert !sharedData.obtain($queryArgs).isPresent()
        """)

        where:
        queryKind                     | queryArgs
        "with a missing key"          | "String, 'missingKey', sharedData.fromProject(':a')"
        "from a non-existent project" | "String, 'test', { org.gradle.util.Path.path(':c') }"
    }

    def 'can access shared data from all projects'() {
        given:
        settingsFile(
            """
            include(":a")
            include(":b")
            """
        )
        groovyFile(file("a/build.gradle"), """
            def sharedData = services.get($ProjectSharedData.name)
            sharedData.register(String, "test", provider { "data from :a" })
        """)
        groovyFile(file("b/build.gradle"), """
            def sharedData = services.get($ProjectSharedData.name)
            sharedData.register(String, "test", provider { "data from :b" })

            tasks.register('printData') {
                def data = sharedData.obtain(String, "test", sharedData.fromProjectsInCurrentBuild())
                doLast {
                    println(data.get().entrySet().join("\\n"))
                }
            }
        """)

        when:
        run(":b:printData")

        then:
        outputContains(":a=data from :a")
        outputContains(":b=data from :b")
    }

    def 'can access shared data from included builds'() {
        given:
        settingsFile(
            """
            includeBuild("x")
            """
        )
        groovyFile(file("x/settings.gradle"), """
            include(":y")
        """)
        groovyFile(file("x/build.gradle"), """
            def sharedData = services.get($ProjectSharedData.name)
            sharedData.register(String, "test", provider { ":x" })
        """)
        groovyFile(file("x/y/build.gradle"), """
            def sharedData = services.get($ProjectSharedData.name)
            sharedData.register(String, "test", provider { ":x:y" })
        """)

        groovyFile(file("build.gradle"), """
            def sharedData = services.get($ProjectSharedData.name)
            sharedData.register(String, "test", provider { ":" })
            tasks.register("printData") {
                def dataFromAllProjects = sharedData.obtain(String, "test", sharedData.fromAllProjects())
                def dataFromProjectsInCurrentBuild = sharedData.obtain(String, "test", sharedData.fromProjectsInCurrentBuild())
                def dataFromFilteredProjects = sharedData.obtain(String, "test", sharedData.fromAllProjectsMatchingIdentityPath { it == ":x:y" })
                doLast {
                    println("all " + dataFromAllProjects.get().entrySet().join(", ") + ";")
                    println("current " + dataFromProjectsInCurrentBuild.get().entrySet().join(", ") + ";")
                    println("filtered " + dataFromFilteredProjects.get().entrySet().join(", ") + ";")
                }
            }
        """)

        when:
        run(":printData")

        then:
        outputContains("all :=:, :x:y=:x:y, :x=:x;")
        outputContains("current :=:;")
        outputContains("filtered :x:y=:x:y;")
    }

    def 'can access shared data from dependency resolution results'() {
        given:
        settingsFile(
            """
            include(":a")
            include(":b")
            include(":c")
            includeBuild("x")
            """
        )
        groovyFile(file("x/build.gradle"), """
            plugins {
                id('java')
            }
            group = "com.example"
            def sharedData = services.get($ProjectSharedData.name)
            sharedData.register(String, "test", provider { "data from :x" })
        """)
        groovyFile(file("a/build.gradle"), """
            plugins {
                id('java')
            }
            dependencies {
                implementation("com.example:x:1.0")
            }
            def sharedData = services.get($ProjectSharedData.name)
            sharedData.register(String, "test", provider { "data from :a" })
        """)
        groovyFile(file("b/build.gradle"), """
            plugins {
                id('java')
            }
            dependencies {
                implementation(project(":a"))
            }
        """)
        groovyFile(file("c/build.gradle"), """
            plugins {
                id('java')
            }
            dependencies {
                implementation(project(":b"))
            }

            tasks.register("printData") {
                def sharedData = services.get($ProjectSharedData.name)
                def dataFromRuntimeClasspath = sharedData.obtain(String, "test", sharedData.fromResolutionResults(configurations.runtimeClasspath))
                doFirst {
                    println("result " + dataFromRuntimeClasspath.get().entrySet().join(", ") + ";")
                }
            }
        """)

        when:
        run(":c:printData")

        then:
        outputContains("result :a=data from :a, :x=data from :x;")
    }
}
