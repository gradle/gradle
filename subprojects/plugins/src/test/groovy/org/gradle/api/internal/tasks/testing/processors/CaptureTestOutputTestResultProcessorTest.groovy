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

package org.gradle.api.internal.tasks.testing.processors

import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.logging.StandardOutputRedirector
import spock.lang.Specification
import org.gradle.api.internal.tasks.testing.*

class CaptureTestOutputTestResultProcessorTest extends Specification {
    def TestResultProcessor target = Mock()
    def StandardOutputRedirector redirector = Mock()
    def processor = new CaptureTestOutputTestResultProcessor(target, redirector)

    def capturesStdOutputAndStdErrorWhileTestIsExecuting() {
        TestDescriptorInternal test = Mock()
        TestStartEvent startEvent = Mock()
        TestCompleteEvent completeEvent = Mock()
        String testId = 'id'
        _ * test.getId() >> testId
        def stdoutListener
        def stderrListener

        when:
        processor.started(test, startEvent)

        then:
        1 * target.started(test, startEvent)
        1 * redirector.redirectStandardOutputTo(!null) >> { args -> stdoutListener = args[0] }
        1 * redirector.redirectStandardErrorTo(!null) >> { args -> stderrListener = args[0] }
        1 * redirector.start()

        when:
        stdoutListener.onOutput('this is stdout')
        stderrListener.onOutput('this is stderr')

        then:
        1 * target.output(testId, { TestOutputEvent event ->
            event.destination == TestOutputEvent.Destination.StdOut && event.message == 'this is stdout'
        })
        1 * target.output(testId, { TestOutputEvent event ->
            event.destination == TestOutputEvent.Destination.StdErr && event.message == 'this is stderr' })

        when:
        processor.completed(testId, completeEvent)

        then:
        1 * redirector.stop()
        1 * target.completed(testId, completeEvent)
    }

    def "configures redirector for suite and test"() {
        //this test is not beautiful and it is also very strict.
        //it's to avoid tricky bugs related to not having correct test ids passed onto the 'output' events
        //it is covered in the integration tests but I decided to have hardcore mocking test for this algorithm as well :)
        given:
        def suite = new DefaultTestClassDescriptor("1", "DogTest");
        def test = new DefaultTestClassDescriptor("1.1", "shouldBark");

        when:
        processor.started(suite, Mock(TestStartEvent))

        then:
        1 * redirector.start()

        1 * redirector.redirectStandardErrorTo({ it.testId == '1' })
        1 * redirector.redirectStandardOutputTo({ it.testId == '1' })

        0 * redirector._

        when:
        processor.started(test, Mock(TestStartEvent))

        then:
        1 * redirector.redirectStandardErrorTo({ it.testId == '1.1' })
        1 * redirector.redirectStandardOutputTo({ it.testId == '1.1' })

        0 * redirector._

        when:
        processor.completed('1.1', Mock(TestCompleteEvent))

        then:
        1 * redirector.redirectStandardErrorTo({ it.testId == '1' })
        1 * redirector.redirectStandardOutputTo({ it.testId == '1' })

        0 * redirector._

        when:
        processor.completed('1', Mock(TestCompleteEvent))

        then:

        1 * redirector.stop()
        0 * redirector._
    }
}
