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

package org.gradle.api.internal.artifacts.repositories.transport;

import org.gradle.internal.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class NetworkOperationBackOffAndRetry {
    private final static String MAX_ATTEMPTS = "org.gradle.internal.network.retry.max.attempts";
    private final static String INITIAL_BACKOFF_MS = "org.gradle.internal.network.retry.initial.backOff";

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkOperationBackOffAndRetry.class);
    private final int maxDeployAttempts;
    private final int initialBackOff;

    public NetworkOperationBackOffAndRetry() {
        this(Integer.getInteger(MAX_ATTEMPTS, 3), Integer.getInteger(INITIAL_BACKOFF_MS, 1000));
    }

    public NetworkOperationBackOffAndRetry(int maxDeployAttempts, int initialBackOff) {
        this.maxDeployAttempts = maxDeployAttempts;
        this.initialBackOff = initialBackOff;
        assert maxDeployAttempts > 0;
        assert initialBackOff > 0;
    }

    public void withBackoffAndRetry(Runnable operation) {
        int backoff = initialBackOff;
        int retries = 0;
        while (retries < maxDeployAttempts) {
            retries++;
            Throwable failure;
            try {
                operation.run();
                if (retries > 1) {
                    LOGGER.info("Successfully ran '{}' after {} retries", operation, retries - 1);
                }
                break;
            } catch (Exception throwable) {
                failure = throwable;
            }
            if (!NetworkingIssueVerifier.isLikelyTransientNetworkingIssue(failure) || retries == maxDeployAttempts) {
                throw UncheckedException.throwAsUncheckedException(failure);
            } else {
                LOGGER.info("Error in '{}'. Waiting {}ms before next retry, {} retries left", operation, backoff, maxDeployAttempts - retries);
                LOGGER.debug("Network operation failed", failure);
                try {
                    Thread.sleep(backoff);
                    backoff *= 2;
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        }
    }
}
