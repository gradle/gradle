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
            task counterInit << { BuildCounter.x = 1 }
            task buildCount << { println "build count: " + BuildCounter.x }
        """
    }

    def "classloader is cached"() {
        when:
        run("counterInit")
        run("buildCount")

        then: output.contains("build count: 2")
    }

    def "no caching when property is off"() {
        executer.withClassLoaderCaching(false)

        when:
        run("counterInit")
        run("buildCount")

        then: output.contains("build count: 1")
    }

    def "refreshes classloader when buildscript changes"() {
        run()
        buildFile << """
            task newTask
        """

        expect:
        run "newTask" //knows new task
    }
}
