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
import org.gradle.api.GradleException
import spock.lang.Specification
import spock.lang.Unroll

class DefaultOperationQueueTest extends Specification {

    abstract static class TestBuildOperation implements BuildOperation, Runnable {
        public String getDescription() { return toString() }
        public String toString() { return getClass().simpleName }
    }

    static class Success extends TestBuildOperation {
        void run() {
            // do nothing
        }
    }

    static class Failure extends TestBuildOperation {
        void run() {
            throw new BuildOperationFailure(this, "always fails")
        }
    }

    static class SimpleWorker implements OperationWorker<TestBuildOperation> {
        public void execute(TestBuildOperation run) {
            run.run();
        }

        String getDisplayName() {
            return getClass().simpleName
        }
    }

    // Tests use calling thread for execution
    ListeningExecutorService executor = MoreExecutors.listeningDecorator(MoreExecutors.sameThreadExecutor())
    OperationQueue operationQueue = new DefaultOperationQueue(executor, new SimpleWorker())

    @Unroll
    def "executes all #runs operations"() {
        given:
        def success = Mock(TestBuildOperation)

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
    
    def "cannot use operation queue once it has completed"() {
        given:
        operationQueue.waitForCompletion()

        when:
        operationQueue.add(Mock(TestBuildOperation))

        then:
        thrown IllegalStateException
    }

    @Unroll
    def "failures propagate to caller regardless of when it failed (#firstOperation, #secondOperation, #thirdOperation)"() {
        given:
        operationQueue.add(firstOperation)
        operationQueue.add(secondOperation)
        operationQueue.add(thirdOperation)
        def failureCount = [ firstOperation, secondOperation, thirdOperation ].findAll({it instanceof Failure}).size()

        when:
        operationQueue.waitForCompletion()

        then:
        // assumes we don't fail early
        MultipleBuildOperationFailures e = thrown()
        e.getCauses().every({ it instanceof GradleException })
        e.getCauses().size() == failureCount

        where:
        firstOperation | secondOperation | thirdOperation
        new Success() | new Success() | new Failure()
        new Success() | new Failure() | new Success()
        new Failure() | new Success() | new Success()
        new Failure() | new Failure() | new Failure()
        new Failure() | new Failure() | new Success()
        new Failure() | new Success() | new Failure()
        new Success() | new Failure() | new Failure()
    }

    def "all failures reported in order"() {
        given:
        operationQueue.add(Stub(TestBuildOperation) {
            run() >> { throw new RuntimeException("first") }
        })
        operationQueue.add(Stub(TestBuildOperation) {
            run() >> { throw new RuntimeException("second") }
        })
        operationQueue.add(Stub(TestBuildOperation) {
            run() >> { throw new RuntimeException("third") }
        })

        when:
        operationQueue.waitForCompletion()

        then:
        // assumes we don't fail early
        MultipleBuildOperationFailures e = thrown()
        e.getCauses()*.message == [ 'first', 'second', 'third' ]
    }
}
