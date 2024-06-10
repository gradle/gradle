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

package org.gradle.api.invocation

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.internal.ToBeImplemented
import org.gradle.internal.code.UserCodeApplicationContext

class GradleLifecycleIsolationIntegrationTest extends AbstractIntegrationSpec {

    def 'lifecycle actions are isolated per project and their order is preserved'() {
        given:
        settingsFile '''
            rootProject.name = 'root'
            include 'sub'

            def log = []
            gradle.lifecycle.beforeProject {
                log << "1: before $name $version"
                version = 'from action'
            }
            gradle.lifecycle.beforeProject {
                log << "2: before $name $version"
            }
            gradle.lifecycle.afterProject {
                log << "1: after $name $version"
            }
            gradle.lifecycle.afterProject {
                log << "2: after $name $version"
            }
            gradle.lifecycle.afterProject {
                println log
            }
        '''

        def script = '''
            println "$name with version $version"
            version = 'from script'
        '''
        buildFile script
        groovyFile 'sub/build.gradle', script

        when:
        succeeds 'help'

        then:
        outputContains 'root with version from action'
        outputContains 'sub with version from action'
        outputContains '[1: before root unspecified, 2: before root from action, 1: after root from script, 2: after root from script]'
        outputContains '[1: before sub unspecified, 2: before sub from action, 1: after sub from script, 2: after sub from script]'
    }

    def "lifecycle actions in Kotlin DSL allow using a function defined in a class"() {
        createDirs("a", "b")
        settingsKotlinFile << """
            rootProject.name = "root"
            include("a", "b")

            object Helper {
                fun printInfo(p: Project) {
                    println("project name = " + p.name)
                }
            }

            gradle.lifecycle.beforeProject {
                Helper.printInfo(project)
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("project name = root")
        outputContains("project name = a")
        outputContains("project name = b")
    }

    def "lifecycle actions in Groovy DSL allow using #functionType function defined in a class"() {
        createDirs("a", "b")
        settingsFile << """
            rootProject.name = "root"
            include("a", "b")

            class Helper {
                $modifier def printInfo(Project p) {
                    println("project name = " + p.name)
                }
            }

            gradle.lifecycle.beforeProject {
                ${owner}.printInfo(project)
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("project name = root")
        outputContains("project name = a")
        outputContains("project name = b")

        where:
        functionType | modifier  | owner
        "regular"    | ""        | "new Helper()"
        "static"     | "static " | "Helper"
    }

    @ToBeImplemented
    def "lifecycle actions in Kotlin DSL allow using top-level build script function"() {
        createDirs("a", "b")
        settingsKotlinFile << """
            rootProject.name = "root"
            include("a", "b")

            fun printInfo(p: Project) {
                println("project name = " + p.name)
            }

            gradle.lifecycle.beforeProject {
                printInfo(project)
            }
        """

        when:
        // TODO:isolated the test should succeed
        fails("help")

        then:
        failure.assertHasCause("Failed to isolate 'GradleLifecycle' action: cannot serialize Gradle script object references as these are not supported with the configuration cache.")

//        outputContains("project name = root")
//        outputContains("project name = a")
//        outputContains("project name = b")
    }

    @ToBeImplemented
    def "lifecycle actions in Groovy DSL allow using #functionType top-level build script function"() {
        createDirs("a", "b")
        settingsFile << """
            rootProject.name = "root"
            include("a", "b")

            $modifier def printInfo(Project p) {
                println("project name = " + p.name)
            }

            gradle.lifecycle.beforeProject {
                printInfo(project)
            }
        """

        when:
        // TODO:isolated the test should succeed
        fails("help")

        then:
        failure.assertHasCause("No signature of method: org.gradle.api.Project.printInfo() is applicable for argument types: () values: []")

//        outputContains("project name = root")
//        outputContains("project name = a")
//        outputContains("project name = b")

        where:
        functionType | modifier
        "regular"    | ""
        "static"     | "static "
    }

    def 'lifecycle actions preserve user code application context for scripts'() {
        given:
        settingsFile """
            gradle.lifecycle.beforeProject {
                println("before:" + $currentApplication)
            }

            gradle.lifecycle.afterProject {
                println("after:" + $currentApplication)
            }
        """

        when:
        succeeds 'help'

        then:
        outputContains "before:settings file 'settings.gradle'"
        outputContains "after:settings file 'settings.gradle'"
    }

    def 'lifecycle actions preserve user code application context for plugins'() {
        given:
        groovyFile "build-logic/build.gradle", '''
            plugins {
                id 'groovy-gradle-plugin'
            }
        '''
        groovyFile "build-logic/src/main/groovy/my-settings-plugin.settings.gradle", """
            gradle.lifecycle.beforeProject {
                println("before:" + $currentApplication)
            }

            gradle.lifecycle.afterProject {
                println("after:" + $currentApplication)
            }
        """
        settingsFile '''
            pluginManagement {
                includeBuild 'build-logic'
            }
            plugins {
                id 'my-settings-plugin'
            }
        '''

        when:
        succeeds 'help'

        then:
        outputContains "before:plugin 'my-settings-plugin'"
        outputContains "after:plugin 'my-settings-plugin'"
    }

    def 'lifecycle actions can be registered in the context of the Gradle runtime'() {
        given:
        settingsFile """
            ${userCodeApplicationContext}.gradleRuntime {
                gradle.lifecycle.beforeProject {
                    println("before:" + $currentApplication)
                }

                gradle.lifecycle.afterProject {
                    println("after:" + $currentApplication)
                }
            }
        """

        when:
        succeeds 'help'

        then:
        outputContains "before:null"
        outputContains "after:null"
    }

    def getCurrentApplication() {
        "${userCodeApplicationContext}.current()?.source?.displayName"
    }

    def getUserCodeApplicationContext() {
        "services.get($UserCodeApplicationContext.name)"
    }
}
