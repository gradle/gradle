/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.performance.fixture

import org.gradle.performance.measure.*

public class MeasuredOperationList extends LinkedList<MeasuredOperation> {
    String name

    DataSeries<DataAmount> getTotalMemoryUsed() {
        return new DataSeries<DataAmount>(this.collect { it.totalMemoryUsed })
    }

    DataSeries<DataAmount> getTotalHeapUsage() {
        return new DataSeries<DataAmount>(this.collect { it.totalHeapUsage })
    }

    DataSeries<DataAmount> getMaxHeapUsage() {
        return new DataSeries<DataAmount>(this.collect { it.maxHeapUsage })
    }

    DataSeries<DataAmount> getMaxUncollectedHeap() {
        return new DataSeries<DataAmount>(this.collect { it.maxUncollectedHeap })
    }

    DataSeries<DataAmount> getMaxCommittedHeap() {
        return new DataSeries<DataAmount>(this.collect { it.maxCommittedHeap })
    }

    DataSeries<Duration> getTotalTime() {
        return new DataSeries<Duration>(this.collect { it.totalTime })
    }

    DataSeries<Duration> getConfigurationTime() {
        return new DataSeries<Duration>(this.collect { it.configurationTime })
    }

    DataSeries<Duration> getExecutionTime() {
        return new DataSeries<Duration>(this.collect { it.executionTime })
    }

    String getSpeedStats() {
        format(totalTime)
    }

    String getMemoryStats() {
        format(totalMemoryUsed)
    }

    private String format(DataSeries<?> measurement) {
        """  ${name} avg: ${measurement.average.format()} min: ${measurement.min.format()}, max: ${measurement.max.format()}, stddev: ${measurement.stddev.format()}
  > ${measurement.collect { it.format() }}
"""

    }
}
