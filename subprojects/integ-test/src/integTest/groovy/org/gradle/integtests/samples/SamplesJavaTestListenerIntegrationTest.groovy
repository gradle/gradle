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

package org.gradle.integtests.samples

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.file.TestFile
import org.hamcrest.CoreMatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SamplesJavaTestListenerIntegrationTest extends  AbstractIntegrationTest {

    @Rule public final Sample sample = new Sample(testDirectoryProvider, 'java/testListener')

    @Before
    void setup() {
        executer.withRepositoryMirrors()
    }

    @Test
    void runsBuildAndShowsFailedTests() {
        TestFile javaprojectDir = sample.dir

        // Build and test projects
        executer.inDirectory(javaprojectDir).withTasks('clean', 'build').runWithFailure().assertTestsFailed()

        // Check tests have run
        def result = new DefaultTestExecutionResult(javaprojectDir)
        result.assertTestClassesExecuted('org.gradle.DoNothingTest')
        result.testClass('org.gradle.DoNothingTest').
                assertTestFailed('doNothingButFail', CoreMatchers.equalTo('java.lang.AssertionError: I always fail')).
                assertTestFailed('doNothingButError', CoreMatchers.equalTo('java.lang.RuntimeException: I always throw exceptions')).
                assertTestPassed('doNothing')
    }
}
