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

package org.gradle.caching.internal.origin;

import org.gradle.internal.hash.HashCode;

import java.time.Duration;

public class OriginMetadata {

    private final String buildInvocationId;
    private final HashCode buildCacheKey;
    private final Duration executionTime;

    public OriginMetadata(String buildInvocationId, HashCode buildCacheKey, Duration executionTime) {
        this.buildInvocationId = buildInvocationId;
        this.buildCacheKey = buildCacheKey;
        this.executionTime = executionTime;
    }

    public String getBuildInvocationId() {
        return buildInvocationId;
    }

    public HashCode getBuildCacheKey() {
        return buildCacheKey;
    }

    public Duration getExecutionTime() {
        return executionTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        OriginMetadata that = (OriginMetadata) o;

        if (!buildInvocationId.equals(that.buildInvocationId)) {
            return false;
        }
        if (!buildCacheKey.equals(that.buildCacheKey)) {
            return false;
        }
        return executionTime.equals(that.executionTime);
    }

    @Override
    public int hashCode() {
        int result = buildInvocationId.hashCode();
        result = 31 * result + buildCacheKey.hashCode();
        result = 31 * result + executionTime.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "OriginMetadata{"
            + "buildInvocationId=" + buildInvocationId
            + ", buildCacheKey=" + buildCacheKey
            + ", executionTime=" + executionTime
            + '}';
    }
}
