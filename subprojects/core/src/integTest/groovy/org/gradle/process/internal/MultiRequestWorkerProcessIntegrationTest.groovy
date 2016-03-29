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
}
