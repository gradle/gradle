/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.jacoco;

import org.gradle.testing.jacoco.tasks.rules.JacocoLimit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * A serializable snapshot of a {@link JacocoLimit}, used to pass rules to the worker.
 */
@NullMarked
public class SerializableJacocoLimit implements Serializable {
    private final String counter;
    private final String value;
    @Nullable
    private final BigDecimal minimum;
    @Nullable
    private final BigDecimal maximum;

    public SerializableJacocoLimit(String counter, String value, @Nullable BigDecimal minimum, @Nullable BigDecimal maximum) {
        this.counter = counter;
        this.value = value;
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public String getCounter() {
        return counter;
    }

    public String getValue() {
        return value;
    }

    @Nullable
    public BigDecimal getMinimum() {
        return minimum;
    }

    @Nullable
    public BigDecimal getMaximum() {
        return maximum;
    }

    public static SerializableJacocoLimit of(JacocoLimit jacocoLimit) {
        return new SerializableJacocoLimit(
            jacocoLimit.getCounter().get(),
            jacocoLimit.getValue().get(),
            jacocoLimit.getMinimum().getOrNull(),
            jacocoLimit.getMaximum().getOrNull()
        );
    }
}
