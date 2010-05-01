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

package org.gradle.api.testing.execution

import spock.lang.Specification
import org.gradle.api.testing.TestClassProcessorFactory
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.testing.fabric.TestClassRunInfo
import org.gradle.api.testing.TestClassProcessor

class MaxNParallelTestClassProcessorTest extends Specification {
    private final TestClassProcessorFactory factory = Mock()
    private final TestResultProcessor resultProcessor = Mock()
    private final MaxNParallelTestClassProcessor processor = new MaxNParallelTestClassProcessor(2, factory)

    def doesNothingWhenNoTestsProcessed() {
        when:
        processor.startProcessing(resultProcessor)
        processor.endProcessing()

        then:
        0 * factory.create()
    }
    
    def startsProcessorsOnDemandAndStopsAtEnd() {
        TestClassRunInfo test = Mock()
        TestClassProcessor processor1 = Mock()

        when:
        processor.startProcessing(resultProcessor)
        processor.processTestClass(test)

        then:
        1 * factory.create() >> processor1
        1 * processor1.startProcessing(!null)
        1 * processor1.processTestClass(test)

        when:
        processor.endProcessing()

        then:
        1 * processor1.endProcessing()
    }

    def startsMultipleProcessorsOnDemandAndStopsAtEnd() {
        TestClassRunInfo test = Mock()
        TestClassProcessor processor1 = Mock()
        TestClassProcessor processor2 = Mock()

        when:
        processor.startProcessing(resultProcessor)
        processor.processTestClass(test)

        then:
        1 * factory.create() >> processor1
        1 * processor1.startProcessing(!null)
        1 * processor1.processTestClass(test)

        when:
        processor.processTestClass(test)

        then:
        1 * factory.create() >> processor2
        1 * processor2.startProcessing(!null)
        1 * processor2.processTestClass(test)

        when:
        processor.endProcessing()

        then:
        1 * processor1.endProcessing()
        1 * processor2.endProcessing()
    }

    def roundRobinsTestClassesToProcessors() {
        TestClassRunInfo test = Mock()
        TestClassProcessor processor1 = Mock()
        TestClassProcessor processor2 = Mock()

        when:
        processor.startProcessing(resultProcessor)
        processor.processTestClass(test)

        then:
        1 * factory.create() >> processor1
        1 * processor1.startProcessing(!null)
        1 * processor1.processTestClass(test)

        when:
        processor.processTestClass(test)

        then:
        1 * factory.create() >> processor2
        1 * processor2.startProcessing(!null)
        1 * processor2.processTestClass(test)

        when:
        processor.processTestClass(test)

        then:
        1 * processor1.processTestClass(test)

        when:
        processor.processTestClass(test)

        then:
        1 * processor2.processTestClass(test)
    }
}
