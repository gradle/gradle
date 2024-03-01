/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.testing.junit.platform

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.junit.Rule

class JUnitPlatformSampleIntegrationTest extends AbstractSampleIntegrationTest {
    @Rule
    public final Sample sample = new Sample(testDirectoryProvider)

    @UsesSample('testing/junitplatform-jupiter/groovy')
    def 'jupiter sample test'() {
        given:
        super.sample sample

        when:
        succeeds 'test'

        then:
        new DefaultTestExecutionResult(sample.dir).testClassByHtml('org.gradle.junitplatform.JupiterTest').assertTestCount(5, 0, 0)
            .assertTestPassed('ok')
            .assertTestPassed('repeated()[1]', 'repetition 1 of 2')
            .assertTestPassed('repeated()[2]', 'repetition 2 of 2')
            .assertTestPassed('test1(TestInfo)', 'TEST 1')
            .assertTestSkipped('disabled')
    }

    @UsesSample('testing/junitplatform-mix/groovy')
    def 'mix JUnit3/4/5'() {
        given:
        super.sample sample

        when:
        succeeds('test')

        then:
        new DefaultTestExecutionResult(sample.dir)
            .testClass('org.gradle.junitplatform.JUnit3Test').assertTestCount(1, 0, 0)
        new DefaultTestExecutionResult(sample.dir)
            .testClass('org.gradle.junitplatform.JUnit4Test').assertTestCount(1, 0, 0)
        new DefaultTestExecutionResult(sample.dir)
            .testClass('org.gradle.junitplatform.JupiterTest').assertTestCount(1, 0, 0)
    }

    @UsesSample('testing/junitplatform-engine/groovy')
    def 'engine sample test'() {
        given:
        super.sample sample

        when:
        succeeds('test')

        then:
        new DefaultTestExecutionResult(sample.dir)
            .assertTestClassesExecuted('org.gradle.junitplatform.JUnit4Test')
            .testClass('org.gradle.junitplatform.JUnit4Test').assertTestCount(1, 0, 0)
    }

    @UsesSample('testing/junitplatform-tagging/groovy')
    def 'tagging sample test'() {
        given:
        super.sample sample

        when:
        succeeds('test')

        then:
        new DefaultTestExecutionResult(sample.dir).testClass('org.gradle.junitplatform.TagTest').assertTestCount(1, 0, 0)
            .assertTestPassed('fastTest()')
    }
}
