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

package org.gradle.internal.typeconversion;

import java.util.concurrent.TimeUnit;

public class NormalizedTimeUnit {

    private int value;
    private TimeUnit timeUnit;

    public NormalizedTimeUnit(int value, TimeUnit timeUnit) {
        this.value = value;
        this.timeUnit = timeUnit;
    }

    public static NormalizedTimeUnit millis(int value) {
        return new NormalizedTimeUnit(value, TimeUnit.MILLISECONDS);
    }

    public int getValue() {
        return value;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }
}
