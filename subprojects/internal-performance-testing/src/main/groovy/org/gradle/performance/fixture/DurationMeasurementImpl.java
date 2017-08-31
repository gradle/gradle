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

package org.gradle.performance.fixture;

import org.gradle.integtests.fixtures.executer.DurationMeasurement;
import org.gradle.performance.measure.Duration;
import org.gradle.performance.measure.MeasuredOperation;
import org.joda.time.DateTime;

public class DurationMeasurementImpl implements DurationMeasurement {
    private DateTime start;
    private long startNanos;
    private final MeasuredOperation measuredOperation;

    public DurationMeasurementImpl(MeasuredOperation measuredOperation) {
        this.measuredOperation = measuredOperation;
    }

    @Override
    public void start() {
        start = DateTime.now();
        startNanos = System.nanoTime();
    }

    @Override
    public void stop() {
        DateTime end = DateTime.now();
        long endNanos = System.nanoTime();
        measuredOperation.setStart(start);
        measuredOperation.setEnd(end);
        measuredOperation.setTotalTime(Duration.millis((endNanos - startNanos) / 1000000L));
    }

    public static void measure(MeasuredOperation measuredOperation, Runnable runnable) {
        DurationMeasurementImpl durationMeasurement = new DurationMeasurementImpl(measuredOperation);
        durationMeasurement.start();
        try {
            runnable.run();
        } finally {
            durationMeasurement.stop();
        }
    }
}
