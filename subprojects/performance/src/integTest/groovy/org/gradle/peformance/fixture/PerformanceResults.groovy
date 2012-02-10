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

package org.gradle.peformance.fixture

public class PerformanceResults {

    List<MeasuredOperation> previous = new LinkedList<MeasuredOperation>()
    List<MeasuredOperation> current = new LinkedList<MeasuredOperation>()

    def clear() {
        previous.clear();
        current.clear();
    }

    void addResult(MeasuredOperation previous, MeasuredOperation current) {
        this.previous.add(previous)
        this.current.add(current)
    }

    void assertEveryBuildSucceeds() {
        assert previous.size() == current.size()
        def previousExceptions = previous.findAll { it.exception }
        def currentExceptions  = previous.findAll { it.exception }
        assert previousExceptions.isEmpty() & currentExceptions.isEmpty()
    }

    void assertCurrentReleaseIsNotSlower() {
        assert previous.size() == current.size()
        long averagePrevious = previous.collect { it.executionTime }.sum() / previous.size()
        long averageCurrent  = current.collect { it.executionTime }.sum() / current.size()
        assert averageCurrent <= averagePrevious : """Looks like the current gradle is slower than latest release.
previous release build times: ${previous}
current gradle build times:   ${current}
"""
    }
}
