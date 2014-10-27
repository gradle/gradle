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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf

//classloaders are cached in process so the test only makes sense if gradle invocations share the process
@IgnoreIf({ !GradleContextualExecuter.longLivingProcess })
class ClassLoadersCachingIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.requireIsolatedDaemons()
        executer.withClassLoaderCaching(true)
        buildFile << """
            class StaticState {
                static set = new HashSet()
            }
            allprojects {
                println project.path + " cached: " + !StaticState.set.add(project.path)
            }
        """
    }

    private boolean isCached(String projectPath = ":") { output.contains("$projectPath cached: true") }
    private boolean isNotCached(String projectPath = ":") { output.contains("$projectPath cached: false") }

    def "classloader is cached"() {
        when:
        run()
        run()

        then: cached
    }

    def "no caching when property is off"() {
        executer.withClassLoaderCaching(false)

        when:
        run()
        run()

        then: notCached
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

        then: notCached
    }

    def "does not refresh main script loader when build script plugin changes"() {
        when:
        buildFile << "apply from: 'plugin.gradle'"
        file("plugin.gradle") << "task foo"
        run("foo")
        file("plugin.gradle").text = "task bar"

        then:
        run("bar") //new task is detected
        cached //main script loader cached
    }

    def "caches subproject classloader"() {
        settingsFile << "include 'foo'"

        when: run()
        then: isNotCached(":foo")
        when: run()
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

        then: notCached
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

        then: isNotCached(":foo")
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
}