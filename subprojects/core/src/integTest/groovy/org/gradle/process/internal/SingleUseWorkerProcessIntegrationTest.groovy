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

import org.gradle.util.TextUtil
import spock.lang.Ignore
import spock.lang.Timeout

@Timeout(120)
class SingleUseWorkerProcessIntegrationTest extends AbstractWorkerProcessIntegrationSpec {
    def "runs method in worker process and returns the result"() {
        when:
        def builder = workerFactory.create(TestWorkInterface.class, TestWorker.class)
        def worker = builder.build()
        def result = worker.convert("abc", 12)

        then:
        result == "[abc 12]"
    }

    def "runs method in worker process and returns null"() {
        when:
        def builder = workerFactory.create(TestWorkInterface.class, TestWorker.class)
        def worker = builder.build()
        def result = worker.convert(null, 12)

        then:
        result == null
    }

    def "runs method in worker process and returns void result"() {
        when:
        def builder = workerFactory.create(TestWorkInterface.class, TestWorker.class)
        def worker = builder.build()
        worker.doSomething()

        then:
        stdout.stdOut == TextUtil.toPlatformLineSeparators("Ok, did it\n")
    }

    def "propagates failure thrown by method in worker process"() {
        when:
        def builder = workerFactory.create(TestWorkInterface.class, BrokenTestWorker.class)
        def worker = builder.build()
        worker.convert("abc", 12)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Could not convert [abc, 12]'
    }

    @Ignore
    def "can reuse worker proxy to run multiple worker processes"() {
        when:
        def builder = workerFactory.create(TestWorkInterface.class, TestWorker.class)
        def worker = builder.build()
        def result1 = worker.convert("abc", 12)
        def result2 = worker.convert(null, 12)
        def result3 = worker.convert("d", 11)

        then:
        result1 == "[abc 12]"
        result2 == null
        result3 == "[d 11]"
    }

    def "propagates failure to instantiate worker implementation"() {
        when:
        def builder = workerFactory.create(TestWorkInterface.class, TestWorkInterface.class)
        builder.baseName = 'broken worker'
        def worker = builder.build()
        worker.convert("abc", 12)

        then:
        def e = thrown(WorkerProcessException)
        e.message == 'Failed to run broken worker'
        e.cause instanceof InstantiationException
    }

    @Ignore
    def "propagates failure to start worker process"() {
        expect: false
    }

    @Ignore
    def "reports failure when worker crashes handling request"() {
        expect: false
    }

    @Ignore
    def "reports failure when worker does not receive request within expected time"() {
        expect: false
    }

    @Ignore
    def "reports failure when worker does not connect within expected time"() {
        expect: false
    }

    @Ignore
    def "reports failure when worker does not stop within expected time"() {
        expect: false
    }
}
