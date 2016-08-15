/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.performance.measure

import groovy.transform.CompileStatic
import groovy.transform.ToString
import org.joda.time.DateTime

@ToString(includeNames = true)
@CompileStatic
public class MeasuredOperation {
    DateTime start
    DateTime end
    Amount<Duration> totalTime
    Amount<Duration> configurationTime
    Amount<Duration> executionTime
    Exception exception
    /** The non-collectable heap usage at the end of the build. This was the original metric used */
    Amount<DataAmount> totalMemoryUsed
    /** The total amount of heap used over the life of the operation. Does not include the perm gen. */
    Amount<DataAmount> totalHeapUsage
    /** The largest amount of heap remaining at the end of a garbage collection. Does not include the perm gen. */
    Amount<DataAmount> maxUncollectedHeap
    /** The largest amount of heap used at the start of a garbage collection. Does not include the perm gen. */
    Amount<DataAmount> maxHeapUsage
    /** The largest amount of committed heap (that is heap requested from the OS). Does not include the perm gen. */
    Amount<DataAmount> maxCommittedHeap
    Amount<Duration> compileTotalTime
    Amount<Duration> gcTotalTime

    boolean isValid() {
        start!=null &&
            end != null &&
            totalTime != null &&
            configurationTime != null &&
            executionTime != null &&
            totalMemoryUsed != null &&
            totalHeapUsage != null &&
            maxUncollectedHeap != null &&
            maxHeapUsage != null &&
            maxCommittedHeap != null
    }
}
