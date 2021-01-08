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

import junit.framework.AssertionFailedError
import org.gradle.api.reflect.ObjectInstantiationException
import org.gradle.process.internal.worker.RequestHandler
import org.gradle.process.internal.worker.WorkerControl
import org.gradle.process.internal.worker.WorkerProcessException
import org.gradle.test.fixtures.ConcurrentTestUtil
import spock.lang.Timeout

@Timeout(120)
class MultiRequestWorkerProcessIntegrationTest extends AbstractWorkerProcessIntegrationSpec {
    def "runs methods in a single worker process and stops when requested"() {
        when:
        def builder = workerFactory.multiRequestWorker(StatefulTestWorker.class)
        def worker = builder.build()
        worker.start()
        def result1 = worker.run("value")
        def result2 = worker.run("value")
        def result3 = worker.run("value")
        worker.stop()

        then:
        result1 == "value:1"
        result2 == "value:2"
        result3 == "value:3"

        cleanup:
        worker?.stop()
    }

    def "receives memory status from worker process"() {
        when:
        def builder = workerFactory.multiRequestWorker(StatefulTestWorker.class)
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
        def builder = workerFactory.multiRequestWorker(BrokenTestWorker.class)
        def worker = builder.build()
        worker.start()
        worker.run("abc")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Could not convert abc'

        when:
        def result = worker.run("ok")

        then:
        result == "converted"

        cleanup:
        worker?.stop()
    }

    def "infers worker implementation classpath"() {
        given:
        def cl = compileToDirectoryAndLoad("CustomTestWorker", """
import ${RequestHandler.name}
class CustomTestWorker implements RequestHandler<Long, Object> {
    Object run(Long request) { return new CustomResult() }
}

class CustomResult implements Serializable {
    String toString() { return "custom-result" }
}
""")

        when:
        def builder = workerFactory.multiRequestWorker(cl)
        def worker = builder.build()
        worker.start()
        def result = worker.run(12.toLong())

        then:
        result.toString() == "custom-result"

        cleanup:
        worker?.stop()
    }

    def "propagates failure to load worker implementation class"() {
        given:
        def cl = compileWithoutClasspath("CustomTestWorker", """
import ${RequestHandler.name}
class CustomTestWorker implements RequestHandler<Long, Object> {
    Object run(Long request) { throw new RuntimeException() }
}
""")

        when:
        def builder = workerFactory.multiRequestWorker(cl)
        builder.baseName = 'broken worker'
        def worker = builder.build()
        worker.start()
        worker.run(12.toLong())

        then:
        def e = thrown(WorkerProcessException)
        e.message == 'Failed to run broken worker'
        e.cause instanceof ClassNotFoundException

        and:
        stdout.stdOut == ""
        stdout.stdErr == ""

        cleanup:
        worker?.stop()
    }

    def "propagates failure to instantiate worker implementation instance"() {
        when:
        def builder = workerFactory.multiRequestWorker(RequestHandler.class)
        builder.baseName = 'broken worker'
        def worker = builder.build()
        worker.start()
        worker.run("abc")

        then:
        def e = thrown(WorkerProcessException)
        e.message == 'Failed to run broken worker'
        e.cause instanceof ObjectInstantiationException
        e.cause.message == "Could not create an instance of type ${RequestHandler.name}."

        and:
        stdout.stdOut == ""
        stdout.stdErr == ""

        cleanup:
        worker?.stop()
    }

    def "propagates failure to start worker process"() {
        when:
        def builder = workerFactory.multiRequestWorker(StatefulTestWorker.class)
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
        def builder = workerFactory.multiRequestWorker(CrashingWorker.class)
        builder.baseName = 'broken worker'
        def worker = builder.build()
        worker.start()
        worker.run("halt-ok")

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
        def builder = workerFactory.multiRequestWorker(CrashingWorker.class)
        builder.baseName = 'broken worker'
        def worker = builder.build()
        worker.start()
        worker.run("halt")

        then:
        def e = thrown(WorkerProcessException)
        e.message == 'Failed to run broken worker'
        e.cause instanceof ExecException
        e.cause.message == "Process 'broken worker 1' finished with non-zero exit value 12"

        cleanup:
        stopBroken(worker)
    }

    def stopBroken(WorkerControl workerControl) {
        if (workerControl == null) {
            return
        }
        try {
            workerControl.stop()
            throw new AssertionFailedError("stop should fail")
        } catch (ExecException e) {
            // Ignore
        }
    }
}
