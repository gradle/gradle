/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.ZincScalaCompileFixture
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

class ScalaTestIntegrationTest extends AbstractIntegrationSpec {
    @Rule TestResources resources = new TestResources(temporaryFolder)
    @Rule public final ZincScalaCompileFixture zincScalaCompileFixture = new ZincScalaCompileFixture(executer, temporaryFolder)

    def executesTestsWithMultiLineDescriptions() {
        file("build.gradle") << """
apply plugin: 'scala'

${mavenCentralRepository()}

dependencies {
    implementation "org.scala-lang:scala-library:2.11.12"
    testImplementation "org.scalatest:scalatest_2.11:2.1.5"
    testImplementation "junit:junit:4.13"
}
        """

        when:
        file("src/test/scala/MultiLineNameTest.scala") << """
package org.gradle

import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class MultiLineSuite extends FunSuite {
    test("This test method name\\nspans many\\nlines") {
        assert(1 === 1)
    }
}
        """

        then:
        succeeds("test")

        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("org.gradle.MultiLineSuite")
	    result.testClass("org.gradle.MultiLineSuite").assertTestPassed("This test method name\nspans many\nlines")
    }
}
