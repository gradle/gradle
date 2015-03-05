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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.Ignore
import spock.lang.IgnoreIf

//classloaders are cached in process so the test only makes sense if gradle invocations share the process
@IgnoreIf({ !GradleContextualExecuter.longLivingProcess })
class ClassLoadersCachingIntegrationTest extends AbstractIntegrationSpec {

    def cacheSizePerRun = []

    def setup() {
        executer.requireIsolatedDaemons()
        buildFile << """
            class StaticState {
                static set = new HashSet()
            }
            allprojects {
                println project.path + " cached: " + !StaticState.set.add(project.path)
            }
            def cache = project.services.get(org.gradle.api.internal.initialization.loadercache.ClassLoaderCache)
            gradle.buildFinished { println "### cache size: " + cache.size }
        """
    }

    private boolean isCached(String projectPath = ":") {
        assertCacheSize()
        output.contains("$projectPath cached: true")
    }

    private boolean isNotCached(String projectPath = ":", boolean checkCacheSize = true) {
        if (checkCacheSize) {
            assertCacheSize()
        }
        output.contains("$projectPath cached: false")
    }

    private void assertCacheSize() {
        assert cacheSizePerRun.size() > 1
        assert cacheSizePerRun[-1] == cacheSizePerRun[-2]
    }

    ExecutionResult run(String... tasks) {
        def result = super.run(tasks)
        def m = output =~ /(?s).*### cache size: (\d+).*/
        m.matches()
        cacheSizePerRun << m.group(1)
        result
    }

    def "classloader is cached"() {
        when:
        run()
        run()

        then: cached
    }

    def "refreshes when buildscript changes"() {
        run()
        buildFile << """
            task newTask
        """

        expect:
        run "newTask" //knows new task
    }

    def "refreshes when buildSrc changes"() {
        file("buildSrc/src/main/groovy/Foo.groovy") << "class Foo {}"

        when:
        run()
        run()

        then: cached

        when:
        file("buildSrc/src/main/groovy/Foo.groovy").text = "class Foo { static int x = 5; }"
        run()

        then: notCached
    }

    def "refreshes when new build script plugin added"() {
        file("plugin.gradle") << "task foo"

        when:
        run()
        buildFile << "apply from: 'plugin.gradle'"
        run("foo")

        then: isNotCached(":", false) //not asserting cache size because
        //new classloader was added due to addition of script plugin
    }

    def "does not refresh main script loader when build script plugin changes"() {
        when:
        buildFile << "apply from: 'plugin.gradle'"
        file("plugin.gradle") << "task foo"
        run("foo")
        file("plugin.gradle").text = "task foobar"

        then:
        run("foobar") //new task is detected
        cached //main script loader cached
    }

    def "caches subproject classloader"() {
        settingsFile << "include 'foo'"

        when:
        run()
        run()

        then: isCached(":foo")
    }

    def "refreshes subproject classloader when parent changes"() {
        settingsFile << "include 'foo'"

        when:
        run()
        buildFile << "task foo"
        run()

        then: isNotCached(":foo")
    }

    def "refreshes when buildscript classpath gets new dependency"() {
        file("foo.jar") << "foo"

        when:
        run()
        buildFile << """
            buildscript { dependencies { classpath files("foo.jar") }}
        """
        run()

        then: isNotCached(":", false) //not asserting cache size
        //because new cl was added to cache by the addition of buildscript {} clause
    }

    def "refreshes when parent project buildscript classpath changes"() {
        settingsFile << "include 'foo'"
        file("foo.jar") << "foo"

        when:
        run()
        buildFile << """
            buildscript { dependencies { classpath files("lib") }}
        """
        run()

        then: isNotCached(":foo", false) //not asserting cache size, new cl was added for buildscript {}
    }

    def "refreshes when buildscript classpath changes dependency"() {
        file("foo.jar") << "yyy"
        buildFile << """
            buildscript { dependencies { classpath files("foo.jar") }}
        """

        when:
        run()
        file("foo.jar") << "xxx"
        run()

        then: notCached
    }

    def "refreshes when buildscript classpath dependency is removed"() {
        file("foo.jar") << "yyy"
        buildFile << """
            buildscript { dependencies { classpath files("foo.jar") }}
        """

        when:
        run()
        assert file("foo.jar").delete()
        run()

        then: notCached
    }

    def "refreshes when buildscript classpath dir dependency is changed"() {
        file("lib/foo.jar") << "xxx"
        buildFile << """
            buildscript { dependencies { classpath fileTree("lib") }}
        """

        when:
        run()
        file("lib/bar.jar") << "xxx"
        run()

        then: notCached
    }

    def "refreshes when buildscript when jar dependency replaced with dir"() {
        file("foo.jar") << "xxx"
        buildFile << """
            buildscript { dependencies { classpath files("foo.jar") }}
        """

        when:
        run()
        assert file("foo.jar").delete()
        assert file("foo.jar").mkdirs()
        run()

        then: notCached
    }

    def "refreshes when buildscript when dir dependency replaced with jar"() {
        assert file("foo.jar").mkdirs()
        buildFile << """
            buildscript { dependencies { classpath files("foo.jar") }}
        """

        when:
        run()
        assert file("foo.jar").delete()
        file("foo.jar") << "xxx"
        run()

        then: notCached
    }

    def "reuse classloader when init script changed"() {
        file("init.gradle") << "println 'init x'"

        when:
        run("-I", "init.gradle")
        file("init.gradle") << "println 'init y'"
        run("-I", "init.gradle")

        then:
        cached
        output.contains "init y"
    }

    def "reuse classloader when settings script changed"() {
        file("settings.gradle") << "println 'settings x'"

        when:
        run()
        file("settings.gradle") << "println 'settings y'"
        run()

        then:
        cached
        output.contains "settings y"
    }

    @Ignore
    //I see that any change to the build script (including adding an empty line)
    //causes the some of the compiled *.class to be different on a binary level
    def "change that does not impact bytecode  classloader when settings script changed"() {
        when:
        run()
        buildFile << "//comment"
        run()

        then: cached
    }
}