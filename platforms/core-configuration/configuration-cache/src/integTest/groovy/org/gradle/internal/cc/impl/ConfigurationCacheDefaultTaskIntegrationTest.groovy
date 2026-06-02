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

package org.gradle.internal.cc.impl

class ConfigurationCacheDefaultTaskIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "running build twice with no task arguments hits the configuration cache"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
            tasks.register("echo") {
                doLast { println("echo executed") }
            }
            defaultTasks "echo"
        """)

        when:
        configurationCacheRun()

        then:
        configurationCache.assertStateStored()
        outputContains("echo executed")

        when:
        configurationCacheRun()

        then:
        configurationCache.assertStateLoaded()
        outputContains("echo executed")
    }

    def "running build with no arguments then explicit default task name hits the configuration cache"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
            tasks.register("echo") {
                doLast { println("echo executed") }
            }
            defaultTasks "echo"
        """)

        when:
        configurationCacheRun()

        then:
        configurationCache.assertStateStored()
        outputContains("echo executed")

        when:
        configurationCacheRun("echo")

        then:
        configurationCache.assertStateLoaded()
        outputContains("echo executed")
    }

    def "running build with explicit default task name then no arguments hits the configuration cache"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
            tasks.register("echo") {
                doLast { println("echo executed") }
            }
            defaultTasks "echo"
        """)

        when:
        configurationCacheRun("echo")

        then:
        configurationCache.assertStateStored()
        outputContains("echo executed")

        when:
        configurationCacheRun()

        then:
        configurationCache.assertStateLoaded()
        outputContains("echo executed")
    }

    def "running unrelated explicit task after no-args does not hit the configuration cache"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
            tasks.register("echo") {
                doLast { println("echo executed") }
            }
            tasks.register("build") {
                doLast { println("build executed") }
            }
            defaultTasks "echo"
        """)

        when:
        configurationCacheRun()

        then:
        configurationCache.assertStateStored()
        outputContains("echo executed")

        when:
        configurationCacheRun("build")

        then:
        configurationCache.assertStateStored()
        outputContains("build executed")
    }

    def "running no-args after unrelated explicit task does not hit the configuration cache"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
            tasks.register("echo") {
                doLast { println("echo executed") }
            }
            tasks.register("build") {
                doLast { println("build executed") }
            }
            defaultTasks "echo"
        """)

        when:
        configurationCacheRun("build")

        then:
        configurationCache.assertStateStored()
        outputContains("build executed")

        when:
        configurationCacheRun()

        then:
        configurationCache.assertStateStored()
        outputContains("echo executed")
    }

    def "running multiple default tasks then by explicit names hits the configuration cache"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
            tasks.register("first") {
                doLast { println("first executed") }
            }
            tasks.register("second") {
                doLast { println("second executed") }
            }
            defaultTasks "first", "second"
        """)

        when:
        configurationCacheRun()

        then:
        configurationCache.assertStateStored()
        outputContains("first executed")
        outputContains("second executed")

        when:
        configurationCacheRun("first", "second")

        then:
        configurationCache.assertStateLoaded()
        outputContains("first executed")
        outputContains("second executed")
    }

    def "explicit subset of default tasks does not hit the configuration cache"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
            tasks.register("first") {
                doLast { println("first executed") }
            }
            tasks.register("second") {
                doLast { println("second executed") }
            }
            defaultTasks "first", "second"
        """)

        when:
        configurationCacheRun()

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("first")

        then:
        configurationCache.assertStateStored()
        outputContains("first executed")
    }

    def "explicit default tasks in reordered order does not hit the configuration cache"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
            tasks.register("first") {
                doLast { println("first executed") }
            }
            tasks.register("second") {
                doLast { println("second executed") }
            }
            defaultTasks "first", "second"
        """)

        when:
        configurationCacheRun()

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("second", "first")

        then:
        configurationCache.assertStateStored()
    }

    def "alias-shared cache entry is invalidated when a build input changes"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
            tasks.register("echo") {
                doLast { println("ci=" + System.getProperty("ci", "false")) }
            }
            // Make the system property a build-logic input by reading at config time
            println("config-time ci=" + providers.systemProperty("ci").getOrElse("false"))
            defaultTasks "echo"
        """)

        when:
        configurationCacheRun("-Dci=true")

        then:
        configurationCache.assertStateStored()
        outputContains("config-time ci=true")
        outputContains("ci=true")

        when:
        configurationCacheRun("-Dci=false", "echo")

        then:
        configurationCache.assertStateStored()
        outputContains("config-time ci=false")
        outputContains("ci=false")
    }

    def "no-args and explicit help do not alias when no defaultTasks are declared"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile("// no default tasks")

        when:
        configurationCacheRun()

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("help")

        then:
        configurationCache.assertStateStored()
    }

    def "aliasing respects the excluded tasks list"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
            tasks.register("echo") {
                doLast { println("echo executed") }
            }
            tasks.register("other") {
                doLast { println("other executed") }
            }
            defaultTasks "echo"
        """)

        when:
        configurationCacheRun("-x", "other")

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("echo", "-x", "other")

        then:
        configurationCache.assertStateLoaded()
        outputContains("echo executed")
    }

    def "default tasks on a subproject with -p alias correctly"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        createDirs("sub")
        settingsFile("include 'sub'")
        file("sub/build.gradle") << """
            tasks.register("subEcho") {
                doLast { println("subEcho executed") }
            }
            defaultTasks "subEcho"
        """

        when:
        configurationCacheRun("-p", "sub")

        then:
        configurationCache.assertStateStored()
        outputContains("subEcho executed")

        when:
        configurationCacheRun("-p", "sub", "subEcho")

        then:
        configurationCache.assertStateLoaded()
        outputContains("subEcho executed")
    }

    def "qualified task path does not alias to unqualified default task name"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
            tasks.register("echo") {
                doLast { println("echo executed") }
            }
            defaultTasks "echo"
        """)

        when:
        configurationCacheRun()

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun(":echo")

        then:
        configurationCache.assertStateStored()
        outputContains("echo executed")
    }
}
