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

import com.google.common.collect.Lists;
import org.apache.commons.math3.stat.inference.MannWhitneyUTest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A collection of measurements of some given units.
 */
public class DataSeries<Q> extends ArrayList<Amount<Q>> {
    private final Amount<Q> average;
    private final Amount<Q> median;
    private final Amount<Q> max;
    private final Amount<Q> min;
    // https://en.wikipedia.org/wiki/Standard_error
    private final Amount<Q> standardError;

    public DataSeries(Iterable<? extends Amount<Q>> values) {
        for (Amount<Q> value : values) {
            if (value != null) {
                add(value);
            }
        }

        if (isEmpty()) {
            average = null;
            median = null;
            max = null;
            min = null;
            standardError = null;
            return;
        }

        Amount<Q> total = get(0);
        Amount<Q> min = get(0);
        Amount<Q> max = get(0);
        for (int i = 1; i < size(); i++) {
            Amount<Q> amount = get(i);
            total = total.plus(amount);
            min = min.compareTo(amount) <= 0 ? min : amount;
            max = max.compareTo(amount) >= 0 ? max : amount;
        }
        List<Amount<Q>> sorted = Lists.newArrayList(this);
        Collections.sort(sorted);
        Amount<Q> medianLeft = sorted.get((sorted.size() - 1) / 2);
        Amount<Q> medianRight = sorted.get((sorted.size() - 1) / 2 + 1 - sorted.size() % 2);
        median = medianLeft.plus(medianRight).div(2);
        average = total.div(size());
        this.min = min;
        this.max = max;

        BigDecimal sumSquares = BigDecimal.ZERO;
        Units<Q> baseUnits = average.getUnits().getBaseUnits();
        BigDecimal averageValue = average.toUnits(baseUnits).getValue();
        for (Amount<Q> amount : this) {
            BigDecimal diff = amount.toUnits(baseUnits).getValue();
            diff = diff.subtract(averageValue);
            diff = diff.multiply(diff);
            sumSquares = sumSquares.add(diff);
        }
        // This isn't quite right, as we may lose precision when converting to a double
        BigDecimal result = BigDecimal.valueOf(Math.sqrt(sumSquares.divide(BigDecimal.valueOf(size()), RoundingMode.HALF_UP).doubleValue())).setScale(2, RoundingMode.HALF_UP);

        standardError = Amount.valueOf(result, baseUnits);
    }

    public Amount<Q> getAverage() {
        return average;
    }

    public Amount<Q> getMedian() {
        return median;
    }

    public Amount<Q> getMin() {
        return min;
    }

    public Amount<Q> getMax() {
        return max;
    }

    public Amount<Q> getStandardError() {
        return standardError;
    }

    public static double confidenceInDifference(DataSeries first, DataSeries second) {
        return 1 - new MannWhitneyUTest().mannWhitneyUTest(first.asDoubleArray(), second.asDoubleArray());
    }

    public List<Double> asDoubleList() {
        return stream().map(Amount::getValue).map(BigDecimal::doubleValue).collect(Collectors.toList());
    }

    private double[] asDoubleArray() {
        return stream().map(Amount::getValue).mapToDouble(BigDecimal::doubleValue).toArray();
    }
}
