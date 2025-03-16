/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.performance.results.report;

import org.gradle.performance.results.MeasuredOperationList;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Line {
    String label;
    List<List<Number>> data;

    public Line(MeasuredOperationList measuredOperations) {
        List<Double> points = measuredOperations.getTotalTime().asDoubleList();
        this.label = measuredOperations.getName();
        this.data = IntStream.range(0, points.size())
            .mapToObj(index -> Arrays.<Number>asList(index, points.get(index)))
            .collect(Collectors.toList());
    }

    public String getLabel() {
        return label;
    }

    public List<List<Number>> getData() {
        return data;
    }
}
