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

import org.gradle.api.internal.file.TestFiles
import org.gradle.workers.ForkMode
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerConfiguration
import spock.lang.Specification

class DefaultWorkerConfigurationTest extends Specification {
    WorkerConfiguration workerConfiguration = new DefaultWorkerConfiguration(TestFiles.execFactory())

    def "can accurately adapt to/from ForkMode"() {
        when:
        workerConfiguration.setForkMode(forkMode)

        then:
        workerConfiguration.getIsolationMode() == isolationMode
        workerConfiguration.getForkMode() == forkMode

        where:
        forkMode        | isolationMode
        ForkMode.AUTO   | IsolationMode.AUTO
        ForkMode.NEVER  | IsolationMode.CLASSLOADER
        ForkMode.ALWAYS | IsolationMode.PROCESS
    }

    def "can accurately adapt to/from IsolationMode"() {
        when:
        workerConfiguration.setIsolationMode(isolationMode)

        then:
        workerConfiguration.getIsolationMode() == isolationMode
        workerConfiguration.getForkMode() == forkMode

        where:
        isolationMode             | forkMode
        IsolationMode.AUTO        | ForkMode.AUTO
        IsolationMode.NONE        | ForkMode.NEVER
        IsolationMode.CLASSLOADER | ForkMode.NEVER
        IsolationMode.PROCESS     | ForkMode.ALWAYS
    }
}
