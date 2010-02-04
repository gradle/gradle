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
package org.gradle.api.testing.execution.ant

import org.gradle.api.testing.fabric.TestClassRunInfo
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*

@RunWith(JMock.class)
class AbstractBatchTestClassProcessorTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final Runnable executeAction = context.mock(Runnable.class)
    private final AbstractBatchTestClassProcessor processor = new TestBatchTestClassProcessor(executeAction: executeAction)

    @Test
    public void executesAntTaskAtTheEndOfProcessing() {
        processor.processTestClass(testClass('Test1'))
        processor.processTestClass(testClass('a.Test2'))

        context.checking {
            one(executeAction).run()
            will {
                assertThat(processor.getTestClassFileNames(), equalTo(['Test1.class', 'a/Test2.class'] as Set))
            }
        }

        processor.endProcessing()
    }

    @Test
    public void doesNothingWhenNoTestsDetected() {
        processor.endProcessing()
    }

    TestClassRunInfo testClass(String className) {
        {-> className} as TestClassRunInfo
    }
}

class TestBatchTestClassProcessor extends AbstractBatchTestClassProcessor {
    private Runnable executeAction

    protected void executeTests() {
        executeAction.run();
    }
}
