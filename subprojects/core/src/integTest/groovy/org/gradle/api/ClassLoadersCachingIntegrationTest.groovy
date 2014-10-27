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
            class BuildCounter {
                static int x = 0
            }
            BuildCounter.x++
            println "build count: " + BuildCounter.x
        """
    }

    private boolean getCached() { output.contains("build count: 2") }
    private boolean getNotCached() { output.contains("build count: 1") }

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

    def "refreshes classloader when buildscript changes"() {
        run()
        buildFile << """
            task newTask
        """

        expect:
        run "newTask" //knows new task
    }

    def "refreshes classloader when buildSrc changes"() {
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
}