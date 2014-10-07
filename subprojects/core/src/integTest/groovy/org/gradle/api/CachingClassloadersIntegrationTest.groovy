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
import org.gradle.util.SetSystemProperties
import org.junit.Rule

class CachingClassloadersIntegrationTest extends AbstractIntegrationSpec {

    @Rule SetSystemProperties sysProp = new SetSystemProperties()

    def setup() {
        System.setProperty("org.gradle.caching.classloaders", "true")
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

        then:
        output.contains("build count: 2")
    }

    def "no caching when property is off"() {
        System.setProperty("org.gradle.caching.classloaders", "not true")

        when:
        run("counterInit")
        run("buildCount")

        then:
        output.contains("build count: 1")
    }
}
