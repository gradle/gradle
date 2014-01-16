/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.performance.measure;

import java.util.ArrayList;
import java.util.List;

/**
 * A collection of measurements of some given units.
 */
public class DataSeries<Q> extends ArrayList<Amount<Q>> {
    private final Amount<Q> average;
    private final Amount<Q> max;
    private final Amount<Q> min;

    public DataSeries(List<? extends Amount<Q>> values) {
        super(values);
        Amount<Q> total = values.get(0);
        Amount<Q> min = values.get(0);
        Amount<Q> max = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            Amount<Q> amount = values.get(i);
            total = total.plus(amount);
            min = min.compareTo(amount) <= 0 ? min : amount;
            max = max.compareTo(amount) >= 0 ? max : amount;
        }
        average = total.div(values.size());
        this.min = min;
        this.max = max;
    }

    public Amount<Q> getAverage() {
        return average;
    }

    public Amount<Q> getMin() {
        return min;
    }

    public Amount<Q> getMax() {
        return max;
    }
}
