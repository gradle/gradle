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

package org.gradle.api.internal.tasks.testing.worker

import org.gradle.api.Action
import org.gradle.api.internal.tasks.testing.TestClassRunInfo
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory
import org.gradle.internal.Factory
import org.gradle.process.JavaForkOptions
import spock.lang.Specification
import spock.lang.Subject

class ForkingTestClassProcessorTest extends Specification {

    @Subject processor = Spy(ForkingTestClassProcessor, constructorArgs: [Mock(Factory), Mock(WorkerTestClassProcessorFactory), Mock(JavaForkOptions), [new File("classpath.jar")], Mock(Action)])

    def "starts worker process on first test"() {
        def test1 = Mock(TestClassRunInfo)
        def test2 = Mock(TestClassRunInfo)
        def remoteProcessor = Mock(RemoteTestClassProcessor)

        when:
        processor.processTestClass(test1)
        processor.processTestClass(test2)

        then:
        1 * processor.forkProcess() >> remoteProcessor
        1 * remoteProcessor.processTestClass(test1)
        1 * remoteProcessor.processTestClass(test2)
        0 * remoteProcessor._
    }
}
