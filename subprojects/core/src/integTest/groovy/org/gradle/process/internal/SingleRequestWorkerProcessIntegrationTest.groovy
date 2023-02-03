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

import org.gradle.api.reflect.ObjectInstantiationException
import org.gradle.process.internal.worker.RequestHandler
import org.gradle.process.internal.worker.WorkerProcessException
import spock.lang.Timeout

@Timeout(120)
class SingleRequestWorkerProcessIntegrationTest extends AbstractWorkerProcessIntegrationSpec {
    def "runs methods in worker process and returns the result"() {
        when:
        def builder = workerFactory.singleRequestWorker(TestWorker.class)
        def worker = builder.build()
        def result = worker.run(12.toLong())

        then:
        result == "[12]"
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
        def builder = workerFactory.singleRequestWorker(cl)
        def worker = builder.build()
        def result = worker.run(12.toLong())

        then:
        result.toString() == "custom-result"
    }

    def "runs method in worker process and returns null"() {
        when:
        def builder = workerFactory.singleRequestWorker(TestWorker.class)
        def worker = builder.build()
        def result = worker.run(null)

        then:
        result == null
    }

    def "propagates failure thrown by method in worker process"() {
        when:
        def builder = workerFactory.singleRequestWorker(BrokenTestWorker.class)
        def worker = builder.build()
        worker.run("abc")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Could not convert abc'
    }

    def "reports failure to load worker implementation class"() {
        given:
        def cl = compileWithoutClasspath("CustomTestWorker", """
import ${RequestHandler.name}
class CustomTestWorker implements RequestHandler<Long, Object> {
    Object run(Long request) { throw new RuntimeException() }
}
""")

        when:
        def builder = workerFactory.singleRequestWorker(cl)
        builder.baseName = 'broken worker'
        def worker = builder.build()
        worker.run(12.toLong())

        then:
        def e = thrown(WorkerProcessException)
        e.message == 'Failed to run broken worker'
        e.cause instanceof ClassNotFoundException

        and:
        stdout.stdOut == ""
        stdout.stdErr == ""
    }

    def "reports failure to instantiate worker implementation instance"() {
        when:
        def builder = workerFactory.singleRequestWorker(RequestHandler.class)
        builder.baseName = 'broken worker'
        def worker = builder.build()
        worker.run("abc")

        then:
        def e = thrown(WorkerProcessException)
        e.message == 'Failed to run broken worker'
        e.cause instanceof ObjectInstantiationException
        e.cause.message == "Could not create an instance of type ${RequestHandler.name}."

        and:
        stdout.stdOut == ""
        stdout.stdErr == ""
    }

    def "propagates failure to start worker process"() {
        when:
        def builder = workerFactory.singleRequestWorker(TestWorker.class)
        builder.baseName = 'broken worker'
        builder.javaCommand.jvmArgs("-broken")
        def worker = builder.build()
        worker.run(12.toLong())

        then:
        def e = thrown(WorkerProcessException)
        e.message == 'Failed to run broken worker'
        e.cause instanceof ExecException
        e.cause.message.matches("Process 'broken worker 1' finished with non-zero exit value \\d+")
    }

    def "reports failure when worker halts handling request"() {
        when:
        def builder = workerFactory.singleRequestWorker(CrashingWorker.class)
        builder.baseName = 'broken worker'
        def worker = builder.build()
        worker.run("halt-ok")

        then:
        def e = thrown(WorkerProcessException)
        e.message == 'Failed to run broken worker'
        e.cause instanceof IllegalStateException
        e.cause.message == "No response was received from broken worker but the worker process has finished."
    }

    def "reports failure when worker crashes handling request"() {
        when:
        def builder = workerFactory.singleRequestWorker(CrashingWorker.class)
        builder.baseName = 'broken worker'
        def worker = builder.build()
        worker.run("halt")

        then:
        def e = thrown(WorkerProcessException)
        e.message == 'Failed to run broken worker'
        e.cause instanceof ExecException
        e.cause.message == "Process 'broken worker 1' finished with non-zero exit value 12"
    }
}
