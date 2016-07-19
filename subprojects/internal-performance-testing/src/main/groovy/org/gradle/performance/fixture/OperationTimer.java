/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.performance.measure.Duration;
import org.gradle.performance.measure.MeasuredOperation;
import org.joda.time.DateTime;

public class OperationTimer {
    public MeasuredOperation measure(Action<? super MeasuredOperation> action) {
        MeasuredOperation result = new MeasuredOperation();
        DateTime start = DateTime.now();
        long startNanos = System.nanoTime();
        try {
            action.execute(result);
        } catch (Exception e) {
            result.setException(e);
        }
        DateTime end = DateTime.now();
        long endNanos = System.nanoTime();
        result.setStart(start);
        result.setEnd(end);
        result.setTotalTime(Duration.millis((endNanos - startNanos) / 1000000L));
        return result;
    }
}
