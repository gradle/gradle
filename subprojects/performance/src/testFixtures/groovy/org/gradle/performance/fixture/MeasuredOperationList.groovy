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

import static org.gradle.performance.fixture.PrettyCalculator.prettyBytes
import static org.gradle.performance.fixture.PrettyCalculator.prettyTime

/**
 * by Szczepan Faber, created at: 2/10/12
 */
public class MeasuredOperationList extends LinkedList<MeasuredOperation> {
    String name

    Amount<DataAmount> avgMemory() {
        def bytes = this.collect { it.totalMemoryUsed }
        bytes.sum() / bytes.size()
    }

    Amount<DataAmount> minMemory() {
        return collect { it.totalMemoryUsed }.min()
    }

    Amount<DataAmount> maxMemory() {
        return collect { it.totalMemoryUsed }.max()
    }

    Amount<Duration> avgTime() {
        def currentTimes = this.collect { it.executionTime }
        currentTimes.sum() / currentTimes.size()
    }

    Amount<Duration> maxTime() {
        return collect { it.executionTime }.max()
    }

    Amount<Duration> minTime() {
        return collect { it.executionTime }.min()
    }

    String getSpeedStats() {
        """  ${name} avg: ${prettyTime(avgTime())} ${collect { prettyTime(it.executionTime) }}
  > min: ${prettyTime(minTime())}, max: ${prettyTime(maxTime())}
"""
    }

    String getMemoryStats() {
        """  ${name} avg: ${prettyBytes(avgMemory())} ${collect { prettyBytes(it.totalMemoryUsed) }}
  > min: ${prettyBytes(minMemory())}, max: ${prettyBytes(maxMemory())}
"""
    }
}
