/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.util;

import com.google.common.collect.Lists;

import java.util.List;

public class Alignment<T> {
    private final T previousValue;
    private final T currentValue;
    private final Kind kind;

    private Alignment(T previous, T current) {
        this.previousValue = previous;
        this.currentValue = current;
        this.kind = kindOf(previous, current);
    }

    public T getPreviousValue() {
        return previousValue;
    }

    public T getCurrentValue() {
        return currentValue;
    }

    public Kind getKind() {
        return kind;
    }

    private static <T> Kind kindOf(T previous, T current) {
        if (previous == current) {
            return Kind.identical;
        }
        if (previous == null) {
            return Kind.added;
        }
        if (current == null) {
            return Kind.removed;
        }
        if (current.equals(previous)) {
            return Kind.identical;
        }
        return Kind.transformed;
    }

    @Override
    public String toString() {
        switch (kind) {
            case added:
                return "+" + currentValue;
            case removed:
                return "-" + previousValue;
            case transformed:
                return previousValue + " -> " + currentValue;
            case identical:
                return previousValue.toString();
        }
        throw new IllegalStateException();
    }

    /**
     * Implements the Wagner-Fischer algorithm (https://en.wikipedia.org/wiki/Wagner%E2%80%93Fischer_algorithm) to align 2 sequences of elements.
     * @param current a sequence of elements
     * @param previous the sequence to align to
     * @param <T> the type of the elements of the sequence
     * @return a list of alignments, telling if an element was added, removed, identical, or mutated
     */
    public static <T> List<Alignment<T>> align(T[] current, T[] previous) {
        int currentLen = current.length;
        int previousLen = previous.length;
        int[][] costs = new int[currentLen + 1][previousLen + 1];
        for (int j = 0; j <= previousLen; j++) {
            costs[0][j] = j;
        }
        for (int i = 1; i <= currentLen; i++) {
            costs[i][0] = i;
            for (int j = 1; j <= previousLen; j++) {
                costs[i][j] = Math.min(Math.min(costs[i - 1][j], costs[i][j - 1]) + 1, current[i - 1].equals(previous[j - 1]) ? costs[i - 1][j - 1] : costs[i - 1][j - 1] + 1);
            }
        }

        List<Alignment<T>> result = Lists.newLinkedList();
        for (int i = currentLen, j = previousLen; i > 0 || j > 0;) {
            int cost = costs[i][j];
            if (i > 0 && j > 0 && cost == (current[i - 1].equals(previous[j - 1]) ? costs[i - 1][j - 1] : costs[i - 1][j - 1] + 1)) {
                T a = current[--i];
                T b = previous[--j];
                if (a.equals(b)) {
                    result.add(0, new Alignment<T>(b, a));
                } else {
                    result.add(0, new Alignment<T>(b, a));
                }
            } else if (i > 0 && cost == 1 + costs[i - 1][j]) {
                result.add(0, new Alignment<T>(null, current[--i]));
            } else if (j > 0 && cost == 1 + costs[i][j - 1]) {
                result.add(0, new Alignment<T>(previous[--j], null));
            } else {
                throw new IllegalStateException("Unexpected cost matrix");
            }
        }
        return result;
    }

    public enum Kind {
        added,
        removed,
        transformed,
        identical
    }

}
