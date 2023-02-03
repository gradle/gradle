/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.workers.fixtures.WorkerExecutorFixture

abstract class AbstractWorkerExecutorIntegrationTest extends AbstractIntegrationSpec {

    def fixture = new WorkerExecutorFixture(temporaryFolder)

    def setup() {
        fixture.prepareTaskTypeUsingWorker()
    }

    void assertWorkerExecuted(String taskName) {
        fixture.list.each {
            outputFileDir.file(taskName).file(it).assertExists()
        }
    }

    void assertSameDaemonWasUsed(String task1, String task2) {
        fixture.list.each {
            assert outputFileDir.file(task1).file(it).text == outputFileDir.file(task2).file(it).text
        }
    }

    void assertDifferentDaemonsWereUsed(String task1, String task2) {
        fixture.list.each {
            assert outputFileDir.file(task1).file(it).text != outputFileDir.file(task2).file(it).text
        }
    }

    @Override
    TestFile getBuildFile() {
        return fixture.getBuildFile()
    }

    protected TestFile getOutputFileDir() {
        return fixture.outputFileDir
    }
}
