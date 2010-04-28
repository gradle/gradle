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

package org.gradle.api.internal.tasks.testing.junit

import spock.lang.Specification
import org.junit.Rule
import org.gradle.util.RedirectStdOutAndErr
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.internal.tasks.testing.TestOutputEvent
import org.gradle.api.internal.tasks.testing.TestCompleteEvent

class CaptureTestOutputTestResultProcessorTest extends Specification {
    @Rule public final RedirectStdOutAndErr outputs = new RedirectStdOutAndErr()
    private final TestResultProcessor target = Mock()
    private final CaptureTestOutputTestResultProcessor processor = new CaptureTestOutputTestResultProcessor(target)

    def capturesStdOutputAndStdErrorWhileTestIsExecuting() {
        TestDescriptorInternal test = Mock()
        TestStartEvent startEvent = Mock()
        TestCompleteEvent completeEvent = Mock()
        String testId = 'id'
        _ * test.getId() >> testId

        when:
        processor.started(test, startEvent)
        System.out.println('this is stdout')
        System.err.println('this is stderr')
        processor.completed(testId, completeEvent)

        then:
        1 * target.started(test, startEvent)
        1 * target.output(testId, { TestOutputEvent event ->
            event.destination == TestOutputEvent.Destination.StdOut && event.message == 'this is stdout\n' })
        1 * target.output(testId, { TestOutputEvent event ->
            event.destination == TestOutputEvent.Destination.StdErr && event.message == 'this is stderr\n' })
        1 * target.completed(testId, completeEvent)
        0 * target._
        System.out == outputs.stdOutPrintStream
        System.err == outputs.stdErrPrintStream
    }

    def flushesStdOutputAndStdError() {

    }
}
