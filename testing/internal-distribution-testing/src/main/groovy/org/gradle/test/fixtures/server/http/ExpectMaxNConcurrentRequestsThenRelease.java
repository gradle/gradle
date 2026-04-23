/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.test.fixtures.server.http;

import org.gradle.internal.UncheckedException;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;

public abstract class ExpectMaxNConcurrentRequestsThenRelease extends ExpectMaxNConcurrentRequests {
    private final Executor executor;

    public ExpectMaxNConcurrentRequestsThenRelease(Lock lock, int testId, Duration timeout, int maxConcurrent, WaitPrecondition previous, Collection<? extends ResourceExpectation> expectedRequests, Executor executor) {
        super(lock, testId, timeout, maxConcurrent, previous, expectedRequests);
        this.executor = executor;
    }

    @Override
    protected boolean isAutoRelease() {
        return true;
    }

    @Override
    protected void onExpectedRequestsReceived(BlockingHttpServer.BlockingHandler handler, int yetToBeReceived) {
        executor.execute(() -> {
            // Wait for a short while to check for unexpected requests
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
            doReleaseAction(handler, yetToBeReceived);
        });
    }

    abstract void doReleaseAction(BlockingHttpServer.BlockingHandler handler, int yetToBeReceived);
}
