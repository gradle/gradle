/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.processors

import org.gradle.api.internal.tasks.testing.DefaultTestClassRunInfo
import org.gradle.api.internal.tasks.testing.TestClassProcessor
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import spock.lang.Specification

class RunSortedBySizeTestClassProcessorTest extends Specification {
    TestClassProcessor delegate = Mock()
    TestResultProcessor testResultProcessor = Mock()
    RunSortedBySizeTestClassProcessor processor

    def 'test classes should be processed by size'() {
        given:
        processor = new RunSortedBySizeTestClassProcessor(['Class3': 2L, 'Class2': 8L, 'Class1': 12L ] as Map, delegate)

        when:
        processor.startProcessing(testResultProcessor)
        ['Class3', 'Class2', 'Class1'].each { processor.processTestClass(new DefaultTestClassRunInfo(it)) }
        processor.stop()

        then:
        1 * delegate.startProcessing(testResultProcessor)
        then:
        1 * delegate.processTestClass(new DefaultTestClassRunInfo('Class1'))
        then:
        1 * delegate.processTestClass(new DefaultTestClassRunInfo('Class2'))
        then:
        1 * delegate.processTestClass(new DefaultTestClassRunInfo('Class3'))
        then:
        1 * delegate.stop()
    }

    def 'test classes of same size should be processed by incoming'() {
        given:
        processor = new RunSortedBySizeTestClassProcessor(['Class3': 2L, 'Class2': 2L, 'Class1': 12L ] as Map, delegate)

        when:
        processor.startProcessing(testResultProcessor)
        ['Class3', 'Class2', 'Class1'].each { processor.processTestClass(new DefaultTestClassRunInfo(it)) }
        processor.stop()

        then:
        1 * delegate.startProcessing(testResultProcessor)
        then:
        1 * delegate.processTestClass(new DefaultTestClassRunInfo('Class1'))
        then:
        1 * delegate.processTestClass(new DefaultTestClassRunInfo('Class3'))
        then:
        1 * delegate.processTestClass(new DefaultTestClassRunInfo('Class2'))
        then:
        1 * delegate.stop()
    }

    def 'test classes without size information should be added at the end'() {
        given:
        processor = new RunSortedBySizeTestClassProcessor(['Class1': 8L, 'Class2': 2L ] as Map, delegate)

        when:
        processor.startProcessing(testResultProcessor)
        ['ClassWithoutSize1', 'Class2', 'ClassWithoutSize2','Class1'].each { processor.processTestClass(new DefaultTestClassRunInfo(it)) }
        processor.stop()

        then:
        1 * delegate.startProcessing(testResultProcessor)
        then:
        1 * delegate.processTestClass(new DefaultTestClassRunInfo('Class1'))
        then:
        1 * delegate.processTestClass(new DefaultTestClassRunInfo('Class2'))
        then:
        1 * delegate.processTestClass(new DefaultTestClassRunInfo('ClassWithoutSize1'))
        then:
        1 * delegate.processTestClass(new DefaultTestClassRunInfo('ClassWithoutSize2'))
        then:
        1 * delegate.stop()
    }

    def 'processing order should not change without size information'() {
        given:
        processor = new RunSortedBySizeTestClassProcessor([:] as Map, delegate)

        when:
        processor.startProcessing(testResultProcessor)
        ['Class1', 'Class2', 'Class3', 'Class4'].each { processor.processTestClass(new DefaultTestClassRunInfo(it)) }
        processor.stop()

        then:
        1 * delegate.startProcessing(testResultProcessor)
        then:
        1 * delegate.processTestClass(new DefaultTestClassRunInfo('Class1'))
        then:
        1 * delegate.processTestClass(new DefaultTestClassRunInfo('Class2'))
        then:
        1 * delegate.processTestClass(new DefaultTestClassRunInfo('Class3'))
        then:
        1 * delegate.processTestClass(new DefaultTestClassRunInfo('Class4'))
        then:
        1 * delegate.stop()
    }
}
