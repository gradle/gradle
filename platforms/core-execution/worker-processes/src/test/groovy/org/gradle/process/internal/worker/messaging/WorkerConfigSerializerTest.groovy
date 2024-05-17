/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.process.internal.worker.messaging

import org.gradle.api.Action
import org.gradle.api.logging.LogLevel
import org.gradle.internal.remote.internal.inet.MultiChoiceAddress
import org.gradle.internal.serialize.InputStreamBackedDecoder
import org.gradle.internal.serialize.OutputStreamBackedEncoder
import org.gradle.process.internal.worker.WorkerProcessContext
import spock.lang.Specification

class WorkerConfigSerializerTest extends Specification {

    def "serialized and de-serialized WorkerConfig is equivalent to original"() {

        given:
        WorkerConfig original = new WorkerConfig(
            LogLevel.ERROR,
            true,
            "/path/to/user/home",
            new MultiChoiceAddress(new UUID(123, 456), 789, [InetAddress.getByName("example.com")]),
            987,
            "name",
            new TestAction("value")
        )

        when:
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        OutputStreamBackedEncoder encoder = new OutputStreamBackedEncoder(baos)
        new WorkerConfigSerializer().write(encoder, original)

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray())
        InputStreamBackedDecoder decoder = new InputStreamBackedDecoder(bais)
        WorkerConfig processed = new WorkerConfigSerializer().read(decoder)

        then:
        processed.logLevel == original.logLevel
        processed.shouldPublishJvmMemoryInfo() == original.shouldPublishJvmMemoryInfo()
        processed.gradleUserHomeDirPath == original.gradleUserHomeDirPath
        processed.serverAddress == original.serverAddress
        processed.workerId == original.workerId
        processed.displayName == original.displayName
        processed.workerAction instanceof TestAction
        processed.workerAction.value == original.workerAction.value
    }

    private static class TestAction implements Action<WorkerProcessContext>, Serializable {
        private final String value
        private TestAction(String value) {
            this.value = value
        }

        @Override
        void execute(WorkerProcessContext context) {}
    }
}
