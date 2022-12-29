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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.longlived.PersistentBuildProcessIntegrationTest

class ClassLoadersCachingIntegrationTest extends PersistentBuildProcessIntegrationTest {

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

    @ToBeFixedForConfigurationCache(because = "test relies on static state")
    def "classloader is cached"() {
        given:
        addIsCachedCheck()

        when:
        run()
        run()

        then:
        isCached()
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
    }

    @ToBeFixedForConfigurationCache(because = "test relies on static state")
    def "refreshes when buildSrc changes"() {
        addIsCachedCheck()
        file("buildSrc/src/main/groovy/Foo.groovy") << "class Foo {}"

        when:
        run()
        run()

        then:
        isCached()

        when:
        file("buildSrc/src/main/groovy/Foo.groovy").text = "class Foo { static int x = 5; }"
        run()

        then:
        isNotCached()
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

    @ToBeFixedForConfigurationCache(because = "test relies on static state")
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
    }

    @ToBeFixedForConfigurationCache(because = "test relies on static state")
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

        then:
        run()
        isCached()
    }

    @ToBeFixedForConfigurationCache(because = "test relies on static state")
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
        isNotCached(":")
        isNotCached(":foo")

        when:
        run()

        then:
        isCached(":")
        isCached(":foo")
    }

    @ToBeFixedForConfigurationCache(because = "test relies on static state")
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
        notCached

        when:
        run()

        then:
        isCached()
    }

    @ToBeFixedForConfigurationCache(because = "test relies on static state")
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
        notCached

        when:
        run()

        then:
        isCached()
    }

    @ToBeFixedForConfigurationCache(because = "test relies on static state")
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
    }

    @ToBeFixedForConfigurationCache(because = "test relies on static state")
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
    }

    def "reuse classloader when init script changed"() {
        addIsCachedCheck()

        when:
        run()
        file("init.gradle") << "println 'init x'"
        run("-I", "init.gradle")

        then:
        isCached()

        when:
        file("init.gradle") << "println 'init y'"
        run("-I", "init.gradle")

        then:
        isCached()
        output.contains "init y"
    }

    def "reuse classloader when settings script changed"() {
        addIsCachedCheck()

        when:
        run()
        settingsFile << "println 'settings x'"
        run()

        then:
        isCached()

        when:
        settingsFile << "println 'settings y'"
        run()

        then:
        isCached()
        output.contains "settings y"

        when:
        assert settingsFile.delete()
        run()

        then:
        isCached()
        !output.contains("settings y")
    }

    @ToBeFixedForConfigurationCache(because = "test relies on static state")
    def "changing non root buildsript classpath does affect child projects"() {
        when:
        settingsFile << "include 'a', 'a:a'"
        createJarWithProperties("thing.jar")
        createJarWithProperties("a/thing.jar")
        createJarWithProperties("a/a/thing.jar")
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

        when:
        file("a/build.gradle") << """
            buildscript {
                dependencies { classpath files("thing.jar") }
            }
        """
        run()

        then:
        isNotCached("a")
        isNotCached("a:a")

        when:
        run()

        then:
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
        isCached("a")
        isNotCached("a:a")

        when:
        file("a/build.gradle").text = getIsCachedCheck() // remove the middle buildscript
        run()

        then:
        isNotCached("a")
        isNotCached("a:a")

        when:
        file("a/a/build.gradle").text = getIsCachedCheck() // remove the leaf buildscript
        run()

        then:
        isCached("a")
        isCached("a:a") // cached in cross-build cache

        when:
        file("a/a/build.gradle").text = getIsCachedCheck() + '// add some random chars'
        run()

        then:
        isCached("a")
        isNotCached("a:a")
    }

    @ToBeFixedForConfigurationCache(because = "test relies on static state")
    def "reuses classloader when included build settings has no classpath but root build does"() {
        given:
        createJarWithProperties("thing.jar", [source: 1])
        settingsFile << """
            buildscript {
                dependencies { classpath files("thing.jar") }
            }
            includeBuild "include"
        """

        and:
        file("include/settings.gradle") << """
            // no export path changes
        """
        addIsCachedCheck()

        when:
        run()
        run()

        then:
        isCached()
    }

}
