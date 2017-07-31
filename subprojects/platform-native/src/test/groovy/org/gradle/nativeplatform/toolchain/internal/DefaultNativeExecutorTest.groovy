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

package org.gradle.nativeplatform.toolchain.internal

import org.gradle.concurrent.ParallelismConfiguration
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.concurrent.ParallelismConfigurationManager
import spock.lang.Specification

class DefaultNativeExecutorTest extends Specification {
    ExecutorFactory executorFactory = Mock()
    ManagedExecutor managedExecutor = Mock()
    ParallelismConfigurationManager parallelismConfigurationManager = Mock()

    def "registers and unregisters parallelism listeners"() {
        when:
        NativeExecutor nativeExecutor = new DefaultNativeExecutor(executorFactory, parallelismConfigurationManager)

        then:
        1 * parallelismConfigurationManager.getParallelismConfiguration() >> Mock(ParallelismConfiguration)
        1 * parallelismConfigurationManager.addListener(_)
        1 * executorFactory.create(_, _) >> managedExecutor

        when:
        nativeExecutor.stop()

        then:
        1 * parallelismConfigurationManager.removeListener(_)
        1 * managedExecutor.shutdown()
    }

    def "Creates a thread pool that matches max workers"() {
        when:
        NativeExecutor nativeExecutor = new DefaultNativeExecutor(executorFactory, parallelismConfigurationManager)

        then:
        1 * parallelismConfigurationManager.getParallelismConfiguration() >> Stub(ParallelismConfiguration) {
            getMaxWorkerCount() >> maxWorkers
        }

        and:
        1 * executorFactory.create(_, maxWorkers) >> managedExecutor

        where:
        maxWorkers << [1, 4, 8]
    }

    def "resizes thread pool if parallelism changes"() {
        when:
        NativeExecutor nativeExecutor = new DefaultNativeExecutor(executorFactory, parallelismConfigurationManager)

        then:
        1 * parallelismConfigurationManager.getParallelismConfiguration() >> Stub(ParallelismConfiguration)
        1 * executorFactory.create(_, _) >> managedExecutor

        when:
        nativeExecutor.onParallelismConfigurationChange(Stub(ParallelismConfiguration) {
            getMaxWorkerCount() >> newMaxWorkers
        })

        then:
        1 * managedExecutor.setFixedPoolSize(newMaxWorkers)

        where:
        newMaxWorkers << [1, 4, 8]
    }
}
