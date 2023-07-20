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

package org.gradle.integtests.tooling.fixture

import org.gradle.test.fixtures.file.TestFile

abstract class Snippets {

    /**
     * Defines the 'myTestTask' test task in the build script with a custom AbstractTestTask implementation in buildSrc.
     */
    static void customTestTask(TestFile buildScript, TestFile buildSrcDir) {
        buildSrcDir.file('src/main/groovy/CustomTestExecuter.groovy') << '''
            import org.gradle.api.internal.tasks.testing.*
            import org.gradle.api.tasks.testing.TestResult
            import org.gradle.internal.operations.OperationIdentifier

            class CustomTestExecuter implements TestExecuter<CustomTestExecutionSpec> {

                @Override
                void execute(CustomTestExecutionSpec customTestExecutionSpec, TestResultProcessor testResultProcessor) {
                    OperationIdentifier rootId = new OperationIdentifier(40L)
                    DefaultTestSuiteDescriptor rootDescr = new DefaultTestSuiteDescriptor(rootId, "MyCustomTestRoot")
                    testResultProcessor.started(rootDescr, new TestStartEvent(System.currentTimeMillis()))

                    OperationIdentifier testId = new OperationIdentifier(42L)
                    DefaultTestDescriptor testDescr = new DefaultTestDescriptor(testId, "org.my.MyClass", "MyCustomTest", null, "org.my.MyClass descriptor")
                    testResultProcessor.started(testDescr, new TestStartEvent(System.currentTimeMillis()))
                    testResultProcessor.completed(testId, new TestCompleteEvent(System.currentTimeMillis(), TestResult.ResultType.SUCCESS))
                    testResultProcessor.completed(rootId, new TestCompleteEvent(System.currentTimeMillis()))
                }

                @Override
                void stopNow() {}
            }
        '''
        buildSrcDir.file('src/main/groovy/CustomTestExecutionSpec.groovy') << '''
            import org.gradle.api.internal.tasks.testing.TestExecutionSpec

            class CustomTestExecutionSpec implements TestExecutionSpec {
            }
        '''
        buildSrcDir.file('src/main/groovy/CustomTestTask.groovy') << '''
            import org.gradle.api.internal.tasks.testing.TestExecuter
            import org.gradle.api.internal.tasks.testing.TestExecutionSpec
            import org.gradle.api.tasks.testing.AbstractTestTask

            class CustomTestTask extends AbstractTestTask {
                CustomTestTask() {
                    binaryResultsDirectory.set(new File(getProject().buildDir, "CustomTestTask"))
                    reports.html.required.set(false)
                    reports.junitXml.required.set(false)
                }

                @Override
                protected TestExecuter<? extends TestExecutionSpec> createTestExecuter() {
                    return new CustomTestExecuter()
                }

                @Override
                protected TestExecutionSpec createTestExecutionSpec() {
                    return new CustomTestExecutionSpec()
                }
            }
        '''
        buildScript << 'tasks.register("myTestTask", CustomTestTask)'
    }
}
