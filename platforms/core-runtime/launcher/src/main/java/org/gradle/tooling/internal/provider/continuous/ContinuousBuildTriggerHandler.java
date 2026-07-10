/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.tooling.internal.provider.continuous;

import org.gradle.deployment.internal.ContinuousExecutionGate;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.UncheckedException;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ContinuousBuildTriggerHandler {
    private final BuildCancellationToken cancellationToken;
    private final ContinuousExecutionGate continuousExecutionGate;
    private final CountDownLatch changeOrCancellationArrived = new CountDownLatch(1);
    private final CountDownLatch cancellationArrived = new CountDownLatch(1);
    private final Duration quietPeriod;
    private volatile Instant lastChangeAt = nowFromMonotonicClock();
    private volatile boolean changeArrived;

    public ContinuousBuildTriggerHandler(
        BuildCancellationToken cancellationToken,
        ContinuousExecutionGate continuousExecutionGate,
        Duration continuousBuildQuietPeriod
    ) {
        this.cancellationToken = cancellationToken;
        this.continuousExecutionGate = continuousExecutionGate;
        this.quietPeriod = continuousBuildQuietPeriod;
    }

    public void wait(Runnable notifier) {
        Runnable cancellationHandler = () -> {
            changeOrCancellationArrived.countDown();
            cancellationArrived.countDown();
        };
        if (cancellationToken.isCancellationRequested()) {
            return;
        }
        try {
            cancellationToken.addCallback(cancellationHandler);
            notifier.run();
            changeOrCancellationArrived.await();
            while (!cancellationToken.isCancellationRequested()) {
                Instant now = nowFromMonotonicClock();
                Instant endOfQuietPeriod = lastChangeAt.plus(quietPeriod);
                if (!endOfQuietPeriod.isAfter(now)) {
                    break;
                }
                Duration remainingQuietPeriod = Duration.between(now, endOfQuietPeriod);
                cancellationArrived.await(remainingQuietPeriod.toMillis(), TimeUnit.MILLISECONDS);
            }
            if (!cancellationToken.isCancellationRequested()) {
                continuousExecutionGate.waitForOpen();
            }
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            cancellationToken.removeCallback(cancellationHandler);
        }
    }

    public boolean hasBeenTriggered() {
        return changeArrived;
    }

    public void notifyFileChangeArrived() {
        changeArrived = true;
        lastChangeAt = nowFromMonotonicClock();
        changeOrCancellationArrived.countDown();
    }

    private static Instant nowFromMonotonicClock() {
        return Instant.ofEpochMilli(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
    }
}
