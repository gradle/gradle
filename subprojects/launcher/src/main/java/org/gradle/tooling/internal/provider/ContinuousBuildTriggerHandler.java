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

package org.gradle.tooling.internal.provider;

import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.ContinuousExecutionGate;
import org.gradle.internal.UncheckedException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.gradle.internal.filewatch.DefaultFileSystemChangeWaiterFactory.QUIET_PERIOD_SYSPROP;

public class ContinuousBuildTriggerHandler {
    private final BuildCancellationToken cancellationToken;
    private final ContinuousExecutionGate continuousExecutionGate;
    private final CountDownLatch changeOrCancellationArrived = new CountDownLatch(1);
    private final CountDownLatch cancellationArrived = new CountDownLatch(1);
    private final long quietPeriod;
    private volatile long lastChangeAt = monotonicClockMillis();

    public ContinuousBuildTriggerHandler(
        BuildCancellationToken cancellationToken,
        ContinuousExecutionGate continuousExecutionGate
    ) {
        this.cancellationToken = cancellationToken;
        this.continuousExecutionGate = continuousExecutionGate;
        this.quietPeriod = Long.getLong(QUIET_PERIOD_SYSPROP, 250L);
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
                long now = monotonicClockMillis();
                long remainingQuietPeriod = quietPeriod - (now - lastChangeAt);
                if (remainingQuietPeriod <= 0) {
                    break;
                }
                cancellationArrived.await(remainingQuietPeriod, TimeUnit.MILLISECONDS);
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

    public void notifyFileChangeArrived() {
        lastChangeAt = monotonicClockMillis();
        changeOrCancellationArrived.countDown();
    }

    private static long monotonicClockMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }
}
