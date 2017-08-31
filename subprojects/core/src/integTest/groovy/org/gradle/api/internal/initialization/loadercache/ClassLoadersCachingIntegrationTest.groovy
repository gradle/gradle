/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.initialization.loadercache

import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.longlived.PersistentBuildProcessIntegrationTest

class ClassLoadersCachingIntegrationTest extends PersistentBuildProcessIntegrationTest {

    def cacheSizePerRun = []

    def setup() {
        file("cacheCheck.gradle") << """
            def cache = gradle.services.get(org.gradle.api.internal.initialization.loadercache.ClassLoaderCache)
            gradle.buildFinished {
                println "### cache size: " + cache.size()

                cache.assertInternalIntegrity()
            }
        """
        executer.beforeExecute {
            withArgument("-I").withArgument("cacheCheck.gradle")
        }
    }

    def getIsCachedCheck() {
        """
            class StaticState {
                static set = new java.util.concurrent.atomic.AtomicBoolean()
            }
            println project.path + " cached: " + StaticState.set.getAndSet(true)
        """
    }

    def addIsCachedCheck(String project = null) {
        file((project ? "$project/" : "") + "build.gradle") << isCachedCheck
    }

    private boolean isCached(String projectPath = ":") {
        assert output.contains("$projectPath cached:"): "no cache flag for project"
        output.contains("$projectPath cached: true")
    }

    private boolean isNotCached(String projectPath = ":") {
        assert output.contains("$projectPath cached:"): "no cache flag for project"
        output.contains("$projectPath cached: false")
    }

    private void assertCacheDidNotGrow() {
        assert cacheSizePerRun.size() > 1: "only one build has been run"
        assert cacheSizePerRun[-1] <= cacheSizePerRun[-2]
    }

    private void assertCacheSizeChange(int expectedCacheSizeChange) {
        assert cacheSizePerRun.size() > 1: "only one build has been run"
        assert cacheSizePerRun[-1] - cacheSizePerRun[-2] == expectedCacheSizeChange
    }

    ExecutionResult run(String... tasks) {
        def result = super.run(tasks)
        def m = output =~ /(?s).*### cache size: (\d+).*/
        m.matches()
        cacheSizePerRun << m.group(1).toInteger()
        result
    }

    def "classloader is cached"() {
        given:
        addIsCachedCheck()

        when:
        run()
        run()

        then:
        isCached()
        assertCacheDidNotGrow()
    }

    def "refreshes when buildscript changes"() {
        given:
        addIsCachedCheck()
        run()
        buildFile << """
            task newTask
        """

        expect:
        run "newTask" //knows new task
        isNotCached()
        assertCacheDidNotGrow()
    }

    def "refreshes when buildSrc changes"() {
        addIsCachedCheck()
        file("buildSrc/src/main/groovy/Foo.groovy") << "class Foo {}"

        when:
        run()
        run()

        then:
        isCached()
        assertCacheDidNotGrow()

        when:
        file("buildSrc/src/main/groovy/Foo.groovy").text = "class Foo { static int x = 5; }"
        run()

        then:
        isNotCached()
        assertCacheDidNotGrow()
    }

    def "refreshes when new build script plugin added"() {
        addIsCachedCheck()
        file("plugin.gradle") << "task foo"

        when:
        run()
        buildFile << "apply from: 'plugin.gradle'"
        run("foo")

        then:
        isNotCached()
        assertCacheSizeChange(1)
    }

    def "does not refresh main script loader when build script plugin changes"() {
        addIsCachedCheck()

        when:
        buildFile << "apply from: 'plugin.gradle'"
        file("plugin.gradle") << "task foo"
        run("foo")
        file("plugin.gradle").text = "task foobar"

        then:
        run("foobar") //new task is detected
        isCached()
    }

    def "caches subproject classloader"() {
        settingsFile << "include 'foo'"
        addIsCachedCheck()
        addIsCachedCheck "foo"

        when:
        run()
        run()

        then:
        isCached(":foo")
    }

    def "uses cached subproject classloader when parent changes"() {
        settingsFile << "include 'foo'"
        addIsCachedCheck()
        addIsCachedCheck "foo"

        when:
        run()
        buildFile << "task foo"
        run()

        then:
        isNotCached()
        isCached(":foo")
        assertCacheDidNotGrow()
    }

    def "refreshes when buildscript classpath gets new dependency"() {
        addIsCachedCheck()
        createJarWithProperties("foo.jar")

        when:
        run()
        buildFile << """
            buildscript { dependencies { classpath files("foo.jar") } }
        """
        run()

        then:
        isNotCached()
        assertCacheSizeChange(2) // 1 new loader for first pass, 1 for second pass

        then:
        run()
        isCached()
        assertCacheDidNotGrow()
    }

    def "cache shrinks as buildscript disappears"() {
        addIsCachedCheck()
        createJarWithProperties("foo.jar")
        buildFile << """
            buildscript { dependencies { classpath files("foo.jar") } }

            task foo
        """

        when:
        run()
        buildScript isCachedCheck
        buildFile << "task foo"
        run()

        then:
        assertCacheSizeChange(-2)

        then:
        run()
        isCached()
        assertCacheSizeChange(0)

        then:
        buildFile.delete()
        run()
        assertCacheSizeChange(-1)
    }

    def "cache shrinks when script with buildscript block is removed"() {
        addIsCachedCheck()
        createJarWithProperties("foo.jar")
        buildFile << """
            buildscript { dependencies { classpath files("foo.jar") } }

            task foo
        """

        when:
        run()
        run()

        then:
        isCached()
        assertCacheSizeChange(0)

        then:
        buildFile.delete()
        run()
        assertCacheSizeChange(-3)
    }

    def "refreshes when root project buildscript classpath changes"() {
        settingsFile << "include 'foo'"
        addIsCachedCheck()
        addIsCachedCheck "foo"
        buildFile << """
            buildscript { dependencies { classpath files("lib") } }
        """
        createJarWithProperties("lib/foo.jar", [source: 1])

        when:
        run()
        run()

        then:
        isCached(":")
        isCached(":foo")

        when:
        sleep(1000)
        file("lib/foo.jar").delete()
        createJarWithProperties("lib/foo.jar", [target: 2])
        run()

        then:
        assertCacheDidNotGrow()
        isNotCached(":")
        isNotCached(":foo")

        when:
        run()

        then:
        assertCacheDidNotGrow()
        isCached(":")
        isCached(":foo")
    }

    def "refreshes when jar is removed from buildscript classpath"() {
        addIsCachedCheck()
        createJarWithProperties("foo.jar")
        buildFile << """
            buildscript { dependencies { classpath files("foo.jar") }}
        """

        when:
        run()
        assert file("foo.jar").delete()
        run()

        then:
        assertCacheDidNotGrow()
        notCached

        when:
        run()

        then:
        assertCacheDidNotGrow()
        isCached()
    }

    def "refreshes when dir is removed from buildscript classpath"() {
        addIsCachedCheck()
        createJarWithProperties("lib/foo.jar")
        buildFile << """
            buildscript { dependencies { classpath files("lib") }}
        """

        when:
        run()
        assert file("lib").deleteDir()
        run()

        then:
        assertCacheDidNotGrow()
        notCached

        when:
        run()

        then:
        assertCacheDidNotGrow()
        isCached()
    }

    def "refreshes when buildscript when jar dependency replaced with dir"() {
        addIsCachedCheck()
        createJarWithProperties("foo.jar")
        buildFile << """
            buildscript { dependencies { classpath files("foo.jar") }}
        """

        when:
        run()
        assert file("foo.jar").delete()
        assert file("foo.jar").mkdirs()
        assert file("foo.jar/someFile.txt").touch()

        run()

        then:
        notCached
        assertCacheDidNotGrow()
    }

    def "refreshes when buildscript when dir dependency replaced with jar"() {
        addIsCachedCheck()
        assert file("foo.jar").mkdirs()
        assert file("foo.jar/someFile.txt").touch()

        buildFile << """
            buildscript { dependencies { classpath files("foo.jar") }}
        """

        when:
        run()
        assert file("foo.jar").deleteDir()
        createJarWithProperties("foo.jar")
        run()

        then:
        notCached
        assertCacheDidNotGrow()
    }

    def "reuse classloader when init script changed"() {
        addIsCachedCheck()

        when:
        run()
        file("init.gradle") << "println 'init x'"
        run("-I", "init.gradle")

        then:
        isCached()
        assertCacheSizeChange(1)

        when:
        file("init.gradle") << "println 'init y'"
        run("-I", "init.gradle")

        then:
        isCached()
        output.contains "init y"
        assertCacheDidNotGrow()
    }

    def "reuse classloader when settings script changed"() {
        addIsCachedCheck()

        when:
        run()
        settingsFile << "println 'settings x'"
        run()

        then:
        isCached()
        assertCacheSizeChange(1)

        when:
        settingsFile << "println 'settings y'"
        run()

        then:
        isCached()
        output.contains "settings y"
        assertCacheDidNotGrow()

        when:
        assert settingsFile.delete()
        run()

        then:
        isCached()
        !output.contains("settings y")
        assertCacheSizeChange(-1)
    }

    def "cache growth is linear as projects are added"() {
        when:
        settingsFile << "System.getProperty('projects')?.split(':')?.each { include \"\$it\" }"
        addIsCachedCheck()
        addIsCachedCheck "a"
        addIsCachedCheck "b"

        then:
        run("tasks")
        run("tasks")
        assertCacheDidNotGrow()

        and:
        args("-Dprojects=a")
        run("tasks")
        isCached()
        isNotCached("a")
        assertCacheSizeChange(1)
        args("-Dprojects=a")
        run("tasks")
        isCached()
        isCached("a")
        assertCacheDidNotGrow()

        and:
        args("-Dprojects=a:b")
        run("tasks")
        isCached()
        isCached("a")
        isNotCached("b")
        assertCacheSizeChange(1)
        args("-Dprojects=a:b")
        run("tasks")
        isCached()
        isCached("a")
        isCached("b")
        assertCacheDidNotGrow()

        then:
        args("-Dprojects=a:b")
        file("b/build.gradle") << "\ntask c"
        run("tasks")
        assertCacheDidNotGrow()
        isCached()
        isCached(":a")
        isNotCached(":b")

        then:
        args("-Dprojects=")
        run("tasks")
        assertCacheSizeChange(0) // we don't reclaim loaders for “orphaned” build scripts
        isCached()
    }

    def "changing non root buildsript classpath does affect child projects"() {
        when:
        settingsFile << "include 'a', 'a:a'"
        addIsCachedCheck()
        addIsCachedCheck("a")
        addIsCachedCheck("a/a")

        // Add this to make the hierarchy unique, avoiding interference with the numbers from previous builds
        file("build.gradle") << """
            buildscript {
                dependencies { classpath files("thing.jar") }
            }
        """

        run()
        run()

        then:
        isCached("a")
        isCached("a:a")
        assertCacheDidNotGrow()

        when:
        file("a/build.gradle") << """
            buildscript {
                dependencies { classpath files("thing.jar") }
            }
        """
        run()

        then:
        assertCacheSizeChange(2)
        isNotCached("a")
        isNotCached("a:a")

        when:
        run()

        then:
        assertCacheDidNotGrow()
        isCached("a")
        isCached("a:a")

        when:
        file("a/a/build.gradle") << """
            buildscript {
                dependencies { classpath files("thing.jar") }
            }
        """
        run()

        then:
        assertCacheSizeChange(2) // can't just reuse, because the parent is different
        isCached("a")
        isNotCached("a:a")

        when:
        file("a/build.gradle").text = getIsCachedCheck() // remove the middle buildscript
        run()

        then:
        assertCacheSizeChange(-2) //
        isNotCached("a")
        isNotCached("a:a")

        when:
        file("a/a/build.gradle").text = getIsCachedCheck() // remove the leaf buildscript
        run()

        then:
        assertCacheSizeChange(-2)
        isCached("a")
        isCached("a:a") // cached in cross-build cache

        when:
        file("a/a/build.gradle").text = getIsCachedCheck() + '// add some random chars'
        run()

        then:
        assertCacheDidNotGrow()
        isCached("a")
        isNotCached("a:a") // cached in cross-build cache

    }
}
