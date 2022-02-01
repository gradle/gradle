/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.retrying;

import org.gradle.api.internal.tasks.testing.junit.AbstractJUnitSpec;

import javax.annotation.Nullable;
import java.io.Serializable;

import static com.google.common.base.MoreObjects.firstNonNull;

public class JvmRetrySpec implements Serializable {

    private final long maxRetries;
    private final boolean stopAfterFailure;

    private JvmRetrySpec(long maxRetries, boolean stopAfterFailure) {
        this.maxRetries = maxRetries;
        this.stopAfterFailure = stopAfterFailure;
    }

    public long getMaxRetries() {
        return maxRetries;
    }

    public boolean isStopAfterFailure() {
        return stopAfterFailure;
    }

    static JvmRetrySpec of(@Nullable Long retryUntilFailureCount, @Nullable Long retryUntilStoppedCount) {
        long maxRetries = firstNonNull(retryUntilFailureCount != null ? retryUntilFailureCount : retryUntilStoppedCount, 1L);
        boolean stopAfterFailure = retryUntilFailureCount != null;
        return new JvmRetrySpec(maxRetries, stopAfterFailure);
    }
}
