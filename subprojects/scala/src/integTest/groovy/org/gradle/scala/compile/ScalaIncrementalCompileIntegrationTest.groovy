/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.scala.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import spock.lang.Issue

class ScalaIncrementalCompileIntegrationTest extends AbstractIntegrationSpec {
    @Issue("gradle/gradle#8421")
    @ToBeFixedForConfigurationCache
    def "incremental compiler detects change in package"() {
        settingsFile << """
            include 'lib'
        """
        [buildFile, file('lib/build.gradle')].each {
            it << """plugins {
    id 'scala'
}

    ${mavenCentralRepository()}

dependencies {
   implementation 'org.scala-lang:scala-library:2.11.12'
}
"""
        }

        buildFile << """
            dependencies {
                implementation project(":lib")
            }
        """

        file("src/main/scala/Hello.scala") << """import pkg1.Other

            class Hello extends Other
        """

        file("lib/src/main/scala/pkg1/Other.scala") << """
            package pkg1

            class Other
        """

        when:
        run ':build'

        then:
        executedAndNotSkipped(":lib:compileScala", ":compileScala")

        when:
        file("lib/src/main/scala/pkg1/Other.scala").delete()
        file("lib/src/main/scala/pkg2/Other.scala") << """
            package pkg2

            class Other
        """
        fails ':build'

        then:
        executedAndNotSkipped(":lib:compileScala", ":compileScala")
        failure.assertHasCause("Compilation failed")
    }
}
