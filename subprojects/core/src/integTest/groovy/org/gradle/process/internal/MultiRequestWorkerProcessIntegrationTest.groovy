/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal

import org.gradle.process.internal.worker.WorkerControl
import org.gradle.process.internal.worker.WorkerProcessException
import org.gradle.test.fixtures.ConcurrentTestUtil
import spock.lang.Ignore
import spock.lang.Timeout

@Timeout(120)
class MultiRequestWorkerProcessIntegrationTest extends AbstractWorkerProcessIntegrationSpec {
    def "runs methods in a single worker process and stops when requested"() {
        when:
        def builder = workerFactory.multiRequestWorker(TestWorkProcess.class, TestProtocol.class, StatefulTestWorker.class)
        def worker = builder.build()
        worker.start()
        def result1 = worker.convert("value", 1)
        worker.doSomething()
        def result2 = worker.convert("value", 1)
        worker.doSomething()
        def result3 = worker.convert("value", 1)
        worker.stop()

        then:
        result1 == "value:1"
        result2 == "value:2"
        result3 == "value:3"

        cleanup:
        worker?.stop()
    }

    def "gets memory status from worker process"() {
        when:
        def builder = workerFactory.multiRequestWorker(TestWorkProcess.class, TestProtocol.class, StatefulTestWorker.class)
        def worker = builder.build()
        def process = worker.start()

        then:
        ConcurrentTestUtil.poll {
            assert process.jvmMemoryStatus.committedMemory > 0
        }

        cleanup:
        worker?.stop()
    }

    def "propagates failure thrown by method in worker process"() {
        when:
        def builder = workerFactory.multiRequestWorker(TestWorkProcess.class, TestProtocol.class, BrokenTestWorker.class)
        def worker = builder.build()
        worker.start()
        worker.convert("abc", 12)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Could not convert [abc, 12]'

        when:
        worker.doSomething()

        then:
        def f = thrown(UnsupportedOperationException)
        f.message == 'Not implemented'

        cleanup:
        worker?.stop()
    }

    def "infers worker implementation classpath"() {
        given:
        def cl = compileToDirectoryAndLoad("CustomTestWorker", """
import ${TestProtocol.name}
class CustomTestWorker implements TestProtocol {
    Object convert(String param1, long param2) { return new CustomResult() }
    void doSomething() { }
}

class CustomResult implements Serializable {
    String toString() { return "custom-result" }
}
""")

        when:
        def builder = workerFactory.multiRequestWorker(TestWorkProcess.class, TestProtocol.class, cl)
        def worker = builder.build()
        worker.start()
        def result = worker.convert("abc", 12)
        worker.stop()

        then:
        result.toString() == "custom-result"

        cleanup:
        worker?.stop()
    }

    def "propagates failure to load worker implementation class"() {
        given:
        def cl = compileWithoutClasspath("CustomTestWorker", """
import ${TestProtocol.name}
class CustomTestWorker implements TestProtocol {
    Object convert(String param1, long param2) { return param1 + ":" + param2 }
    void doSomething() { }
}
""")

        when:
        def builder = workerFactory.multiRequestWorker(TestWorkProcess.class, TestProtocol.class, cl)
        builder.baseName = 'broken worker'
        def worker = builder.build()
        worker.start()
        worker.convert("abc", 12)

        then:
        def e = thrown(WorkerProcessException)
        e.message == 'Failed to run broken worker'
        e.cause instanceof ClassNotFoundException

        cleanup:
        worker?.stop()
    }

    def "propagates failure to instantiate worker implementation instance"() {
        when:
        def builder = workerFactory.multiRequestWorker(TestWorkProcess.class, TestProtocol.class, TestProtocol.class)
        builder.baseName = 'broken worker'
        def worker = builder.build()
        worker.start()
        worker.convert("abc", 12)

        then:
        def e = thrown(WorkerProcessException)
        e.message == 'Failed to run broken worker'
        e.cause instanceof InstantiationException

        cleanup:
        worker?.stop()
    }

    def "propagates failure to start worker process"() {
        when:
        def builder = workerFactory.multiRequestWorker(TestWorkProcess.class, TestProtocol.class, StatefulTestWorker.class)
        builder.baseName = 'broken worker'
        builder.javaCommand.jvmArgs("-broken")
        def worker = builder.build()
        worker.start()

        then:
        def e = thrown(WorkerProcessException)
        e.message == 'Failed to run broken worker'
        e.cause instanceof ExecException
        e.cause.message.matches("Process 'broken worker 1' finished with non-zero exit value \\d+")

        cleanup:
        stopBroken(worker)
    }

    def "reports failure when worker halts handling request"() {
        when:
        def builder = workerFactory.multiRequestWorker(TestWorkProcess.class, TestProtocol.class, CrashingWorker.class)
        builder.baseName = 'broken worker'
        def worker = builder.build()
        worker.start()
        worker.convert("halt", 0)

        then:
        def e = thrown(WorkerProcessException)
        e.message == 'Failed to run broken worker'
        e.cause instanceof IllegalStateException
        e.cause.message == "No response was received from broken worker but the worker process has finished."

        cleanup:
        worker?.stop()
    }

    def "reports failure when worker crashes handling request"() {
        when:
        def builder = workerFactory.multiRequestWorker(TestWorkProcess.class, TestProtocol.class, CrashingWorker.class)
        builder.baseName = 'broken worker'
        def worker = builder.build()
        worker.start()
        worker.convert("halt", 12)

        then:
        def e = thrown(WorkerProcessException)
        e.message == 'Failed to run broken worker'
        e.cause instanceof ExecException
        e.cause.message == "Process 'broken worker 1' finished with non-zero exit value 12"

        cleanup:
        stopBroken(worker)
    }

    @Ignore
    def "cannot invoke method when not running"() {
        expect: false
    }

    @Ignore
    def "cannot invoke method after process crashes"() {
        expect: false
    }

    def stopBroken(WorkerControl workerControl) {
        try {
            workerControl?.stop()
        } catch (ExecException e) {
            // Ignore
        }
    }
}
