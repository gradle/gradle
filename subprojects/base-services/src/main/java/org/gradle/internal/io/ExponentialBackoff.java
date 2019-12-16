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
package org.gradle.internal.io;

import org.gradle.internal.time.CountdownTimer;
import org.gradle.internal.time.Time;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class ExponentialBackoff<S extends ExponentialBackoff.Signal> {

    private static final int CAP_FACTOR = 100;
    private static final long SLOT_TIME = 25;

    private final Random random = new Random();
    private final S signal;

    private final long slotTime;
    private final int timeoutMs;
    private CountdownTimer timer;

    public static <T extends Signal> ExponentialBackoff<T> of(int amount, TimeUnit unit, T signal) {
        return new ExponentialBackoff<T>((int) TimeUnit.MILLISECONDS.convert(amount, unit), signal, SLOT_TIME);
    }

    public static ExponentialBackoff<Signal> of(int amount, TimeUnit unit, int slotTime, TimeUnit slotTimeUnit) {
        return new ExponentialBackoff<Signal>((int) TimeUnit.MILLISECONDS.convert(amount, unit), Signal.SLEEP, TimeUnit.MILLISECONDS.convert(slotTime, slotTimeUnit));
    }

    private ExponentialBackoff(int timeoutMs, S signal, long slotTime) {
        this.timeoutMs = timeoutMs;
        this.signal = signal;
        this.slotTime = slotTime;
        restartTimer();
    }

    public void restartTimer() {
        timer = Time.startCountdownTimer(timeoutMs);
    }

    public <T> T retryUntil(IOQuery<T> query) throws IOException, InterruptedException {
        int iteration = 0;
        T result;
        while ((result = query.run()) == null) {
            if (timer.hasExpired()) {
                break;
            }
            boolean signaled = signal.await(backoffPeriodFor(++iteration));
            if (signaled) {
                iteration = 0;
            }
        }
        return result;
    }

    long backoffPeriodFor(int iteration) {
        return random.nextInt(Math.min(iteration, CAP_FACTOR)) * slotTime;
    }

    public CountdownTimer getTimer() {
        return timer;
    }

    public S getSignal() {
        return signal;
    }

    public interface Signal {
        Signal SLEEP = new Signal() {
            @Override
            public boolean await(long period) throws InterruptedException {
                Thread.sleep(period);
                return false;
            }
        };
        boolean await(long period) throws InterruptedException;
    }
}
