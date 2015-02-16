/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.operations
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import org.gradle.api.Action
import org.gradle.api.GradleException
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.Executors

class DefaultOperationQueueTest extends Specification {

    class Success implements Runnable {
        void run() {
            // do nothing
        }

        public String toString() { return "Success" }
    }

    class Failure implements Runnable {
        void run() {
            throw new GradleException("always fails")
        }

        public String toString() { return "Failure" }
    }

    class SimpleWorker implements Action<Runnable> {
        public void execute(Runnable run) {
            run.run();
        }
    }

    // Tests assume a single worker thread
    ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1))
    OperationQueue operationQueue = new DefaultOperationQueue(executor, new SimpleWorker())

    @Unroll
    def "executes all #runs operations"() {
        given:
        def success = Mock(Runnable)

        when:
        runs.times { operationQueue.add(success) }

        and:
        operationQueue.waitForCompletion()

        then:
        runs * success.run()

        where:
        runs | _
        0    | _
        1    | _
        5    | _
    }

    @Ignore("We want to keep going for now, but in the future we'll want to cancel early")
    def "execution stops once failure occurs"() {
        given:
        def operationBefore = Mock(Runnable)
        def failure = new Failure()
        def operationAfter = Mock(Runnable)

        when:
        operationQueue.add(operationBefore)
        operationQueue.add(failure)
        operationQueue.add(operationAfter)

        and:
        operationQueue.waitForCompletion()

        then:
        1 * operationBefore.run()
        thrown GradleException
        0 * operationAfter.run()
    }

    def "cannot use operation queue once it has completed"() {
        given:
        operationQueue.waitForCompletion()

        when:
        operationQueue.add(Mock(Runnable))

        then:
        thrown IllegalStateException
    }

    @Unroll
    def "failures propagate to caller regardless of when it failed (#firstOperation, #secondOperation, #thirdOperation)"() {
        given:
        operationQueue.add(firstOperation)
        operationQueue.add(secondOperation)
        operationQueue.add(thirdOperation)

        when:
        operationQueue.waitForCompletion()

        then:
        thrown MultipleBuildOperationFailures

        where:
        firstOperation | secondOperation | thirdOperation
        new Success() | new Success() | new Failure()
        new Success() | new Failure() | new Success()
        new Failure() | new Success() | new Success()
    }

    def "all failures reported in order"() {
        given:
        operationQueue.add(Stub(Runnable) {
            run() >> { throw new RuntimeException("first") }
        })
        operationQueue.add(Stub(Runnable) {
            run() >> { throw new RuntimeException("second") }
        })
        operationQueue.add(Stub(Runnable) {
            run() >> { throw new RuntimeException("third") }
        })

        when:
        // assumes we don't fail early
        operationQueue.waitForCompletion()

        then:
        MultipleBuildOperationFailures e = thrown()
        e.getCauses()*.message == [ 'first', 'second', 'third' ]
    }
}
