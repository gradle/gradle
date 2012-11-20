/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.scala.test

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Ignore

class ScalaTestIntegrationTest extends AbstractIntegrationSpec {

    @Rule TestResources resources = new TestResources()

    def executesTestsWithMultiLineDescriptions() {
        file("build.gradle") << """
            apply plugin: 'scala'

            repositories {
                mavenCentral()
            }

            dependencies {
                scalaTools 'org.scala-lang:scala-compiler:2.8.1'
                scalaTools 'org.scala-lang:scala-library:2.8.1'
                compile 'org.scala-lang:scala-library:2.8.1'
                testCompile 'org.scalatest:scalatest_2.8.1:1.8'
            }
        """

        when:
        file("src/test/scala/MultiLineNameTest.scala") << """
import org.scalatest.FunSuite

class MultiLineSuite extends FunSuite {
    test("This test \n spans many \n lines") {
        assert(1 === 2)
    }
}"""

        then:
        //the build should fail because the failing test has been executed
        runAndFail("test").assertHasDescription("Execution failed for task ':testScala'.")
    }
}
