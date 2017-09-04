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

package org.gradle.internal.time;

import java.util.concurrent.TimeUnit;

public abstract class Time {

    private static final Clock CLOCK = new MonotonicClock();

    private Time() {
    }

    public static Clock clock() {
        return CLOCK;
    }

    public static Timer startTimer() {
        return new DefaultTimer(clock());
    }

    public static Timer startTimerAt(long startTime) {
        return new DefaultTimer(clock(), startTime);
    }

    public static CountdownTimer startCountdownTimer(long timeoutMillis) {
        return new DefaultCountdownTimer(clock(), timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public static CountdownTimer startCountdownTimer(long timeout, TimeUnit unit) {
        return new DefaultCountdownTimer(clock(), timeout, unit);
    }

}
