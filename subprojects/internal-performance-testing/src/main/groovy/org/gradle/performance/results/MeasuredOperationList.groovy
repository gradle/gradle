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

package org.gradle.performance.results

import groovy.transform.CompileStatic
import org.gradle.performance.measure.DataAmount
import org.gradle.performance.measure.DataSeries
import org.gradle.performance.measure.Duration
import org.gradle.performance.measure.MeasuredOperation

@CompileStatic
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

    DataSeries<Duration> getCompileTotalTime() {
        return new DataSeries<Duration>(this.collect { it.compileTotalTime ?: Duration.millis(0) })
    }

    DataSeries<Duration> getGcTotalTime() {
        return new DataSeries<Duration>(this.collect { it.gcTotalTime ?: Duration.millis(0) })
    }

    String getSpeedStats() {
        format(totalTime)
    }

    private String format(DataSeries<?> measurement) {
        """  ${name} median: ${measurement.median.format()} min: ${measurement.min.format()}, max: ${measurement.max.format()}, se: ${measurement.standardError.format()}, sem: ${measurement.standardErrorOfMean.format()}
  > ${measurement.collect { it.format() }}
"""

    }
}
