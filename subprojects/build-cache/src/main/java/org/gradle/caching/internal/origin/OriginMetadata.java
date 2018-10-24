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

package org.gradle.caching.internal.origin;

import com.google.common.base.Preconditions;
import org.gradle.internal.id.UniqueId;

public class OriginMetadata {

    private final UniqueId buildInvocationId;
    private final long executionTime;
    private final boolean producedByCurrentBuild;

    public static OriginMetadata fromCurrentBuild(UniqueId buildInvocationId, long executionTime) {
        return new OriginMetadata(buildInvocationId, executionTime, true);
    }

    public static OriginMetadata fromPreviousBuild(UniqueId buildInvocationId, long executionTime) {
        return new OriginMetadata(buildInvocationId, executionTime, false);
    }

    // Remove once Kotlin DSL stopped using OriginExecutionMetadata
    @Deprecated
    protected OriginMetadata(UniqueId buildInvocationId, long executionTime) {
        this(buildInvocationId, executionTime, true);
    }

    private OriginMetadata(UniqueId buildInvocationId, long executionTime, boolean producedByCurrentBuild) {
        this.buildInvocationId = Preconditions.checkNotNull(buildInvocationId, "buildInvocationId");
        this.executionTime = executionTime;
        this.producedByCurrentBuild = producedByCurrentBuild;
    }

    public UniqueId getBuildInvocationId() {
        return buildInvocationId;
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public boolean isProducedByCurrentBuild() {
        return producedByCurrentBuild;
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

        return executionTime == that.executionTime
            && buildInvocationId.equals(that.buildInvocationId)
            && producedByCurrentBuild == that.producedByCurrentBuild;
    }

    @Override
    public int hashCode() {
        int result = buildInvocationId.hashCode();
        result = 31 * result + (int) (executionTime ^ (executionTime >>> 32));
        result = 31 * result + (producedByCurrentBuild ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "OriginMetadata{"
            + "buildInvocationId=" + buildInvocationId
            + ", executionTime=" + executionTime
            + ", producedByCurrentBuild=" + producedByCurrentBuild
            + '}';
    }
}
