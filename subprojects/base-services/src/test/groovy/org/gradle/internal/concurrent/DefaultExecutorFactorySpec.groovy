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
package org.gradle.internal.concurrent

import spock.lang.Specification

class DefaultExecutorFactorySpec extends Specification {

    def factory = new DefaultExecutorFactory()

    def cleanup() {
        factory.stop()
    }

    //TODO if you're looking here, please consider moving more tests from DefaultExecutorFactoryTest

    def stopRethrowsFirstExecutionException() {
        given:
        def failure1 = new RuntimeException()
        def runnable1 = { throw failure1 }

        def failure2 = new RuntimeException()
        def runnable2 = { Thread.sleep(100); throw failure2 }

        when:
        def executor = factory.create('')
        executor.execute(runnable1)
        executor.execute(runnable2)
        executor.stop()

        then:
        def ex = thrown(RuntimeException)
        ex.is(failure1)
    }
}
