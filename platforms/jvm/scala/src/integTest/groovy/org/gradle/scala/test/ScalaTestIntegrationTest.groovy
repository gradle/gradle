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

import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult.TestFramework
import org.gradle.api.tasks.testing.TestResult
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule

class ScalaTestIntegrationTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {
    @Rule TestResources resources = new TestResources(temporaryFolder)

    @Override
    TestFramework getTestFramework() {
        return TestFramework.SCALA_TEST
    }

    @Requires(value = UnitTestPreconditions.Jdk23OrEarlier, reason = "2.11.12 is required for ScalaTest 2.x, which is not compatible with running on JDK 24.")
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

        // New lines are stripped when reporting results
        def results = resultsFor()
        results.testPath('org.gradle.MultiLineSuite', 'This test method name spans many lines').onlyRoot()
            .assertHasResult(TestResult.ResultType.SUCCESS)
    }
}
