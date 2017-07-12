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

package org.gradle.initialization

import org.gradle.internal.concurrent.DefaultParallelismConfiguration
import org.gradle.internal.concurrent.ParallelismConfigurationManager
import org.gradle.concurrent.ParallelismConfiguration
import org.gradle.internal.concurrent.ParallelismConfigurationListener
import org.gradle.internal.event.ListenerManager
import spock.lang.Specification


class DefaultParallelismConfigurationManagerTest extends Specification {
    ParallelismConfigurationListener broadcaster = Mock(ParallelismConfigurationListener)
    ListenerManager listenerManager = Mock(ListenerManager) { 1 * getBroadcaster(ParallelismConfigurationListener.class) >> broadcaster }
    ParallelismConfigurationManager parallelExecutionManager = new DefaultParallelismConfigurationManager(listenerManager)
    ParallelismConfiguration configuration = Mock(ParallelismConfiguration)

    def "notifies listeners when parallelism configuration changes"() {
        when:
        parallelExecutionManager.setParallelismConfiguration(configuration)

        then:
        1 * broadcaster.onParallelismConfigurationChange(configuration)
    }

    def "registers/deregisters listeners with listener manager"() {
        ParallelismConfigurationListener listener = Mock(ParallelismConfigurationListener)

        when:
        parallelExecutionManager.addListener(listener)

        then:
        1 * listenerManager.addListener(listener)

        when:
        parallelExecutionManager.removeListener(listener)

        then:
        1 * listenerManager.removeListener(listener)
    }

    def "uses default parallelism configuration when not set"() {
        expect:
        parallelExecutionManager.parallelismConfiguration.maxWorkerCount == DefaultParallelismConfiguration.DEFAULT.maxWorkerCount
        parallelExecutionManager.parallelismConfiguration.parallelProjectExecutionEnabled == DefaultParallelismConfiguration.DEFAULT.parallelProjectExecutionEnabled
    }
}
