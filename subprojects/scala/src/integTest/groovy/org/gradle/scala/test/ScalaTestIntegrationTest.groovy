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
import org.gradle.integtests.fixtures.JUnitTestExecutionResult
import org.gradle.integtests.fixtures.TestResources

import org.junit.Rule

class ScalaTestIntegrationTest extends AbstractIntegrationSpec {
    @Rule TestResources resources
    
    def executesTestsWithMultiLineDescriptions() {
        file("build.gradle") << """
apply plugin: 'scala'

repositories {
    mavenCentral()
}

dependencies {
    scalaTools "org.scala-lang:scala-compiler:2.9.2"
    compile "org.scala-lang:scala-library:2.9.2"
    testCompile "org.scalatest:scalatest_2.9.2:1.8"
    testCompile "junit:junit:4.10"
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
        //the build should fail because the failing test has been executed
        succeeds("test")

        JUnitTestExecutionResult result = new JUnitTestExecutionResult(testDir)
        result.assertTestClassesExecuted("org.gradle.MultiLineSuite")
	    result.testClass("org.gradle.MultiLineSuite").assertTestPassed("This test method name\nspans many\nlines")
    }
}
