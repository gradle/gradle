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
import org.gradle.performance.measure.DataSeries
import org.gradle.performance.measure.Duration
import org.gradle.performance.measure.MeasuredOperation
import org.gradle.profiler.buildops.BuildOperationExecutionData
import org.gradle.profiler.buildops.BuildOperationMeasurement

@CompileStatic
public class MeasuredOperationList extends LinkedList<MeasuredOperation> {
    String name

    DataSeries<Duration> getTotalTime() {
        return new DataSeries<Duration>(this.collect { it.totalTime })
    }

    /**
     * Per-iteration durations recorded for a build operation requested via
     * {@code measureBuildOperation(...)}. Values are in milliseconds; iterations
     * with no data for {@code measurement} are skipped.
     */
    DataSeries<Duration> getBuildOperationTime(BuildOperationMeasurement measurement) {
        return new DataSeries<Duration>(this.collect { op ->
            BuildOperationExecutionData data = op.buildOperationData?.get(measurement)
            data == null ? null : Duration.millis(data.value)
        })
    }

    String getSpeedStats() {
        format(totalTime)
    }

    private String format(DataSeries<?> measurement) {
        """  ${name} median: ${measurement.median.format()} min: ${measurement.min.format()}, max: ${measurement.max.format()}, se: ${measurement.standardError.format()}}
  > ${measurement.collect { it.format() }}
"""

    }
}
