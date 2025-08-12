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

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;

public class ExpectMaxNRequestsThenReleaseOne extends ExpectMaxNConcurrentRequestsThenRelease {
    public ExpectMaxNRequestsThenReleaseOne(Lock lock, int testId, Duration timeout, int maxConcurrent, WaitPrecondition previous, Collection<? extends ResourceExpectation> expectedRequests, Executor executor) {
        super(lock, testId, timeout, maxConcurrent, previous, expectedRequests, executor);
    }

    @Override
    void doReleaseAction(BlockingHttpServer.BlockingHandler handler, int yetToBeReceived) {
        if (yetToBeReceived > 0) {
            handler.release(1);
        } else {
            // No more requests coming to auto release, so release all remaining requests
            handler.releaseAll();
        }
    }
}
