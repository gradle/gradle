/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.operations;

import org.jspecify.annotations.Nullable;

public final class OperationFinishEvent {
    private final long startTime;
    private final long endTime;
    private final Throwable failure;
    private final Object result;

    /**
     *
     * @param startTime the time the operation started
     * @param currentTime the current time when the operation is considered finished
     * @param failure operation failure
     * @param result operation result
     */
    public OperationFinishEvent(long startTime, long currentTime, @Nullable Throwable failure, @Nullable Object result) {
        this.startTime = startTime;
        this.endTime = currentTime;
        this.failure = failure;
        this.result = result;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    @Nullable
    public Throwable getFailure() {
        return failure;
    }

    @Nullable
    public Object getResult() {
        return result;
    }

    @Override
    public String toString() {
        return "OperationFinishEvent{" +
            "startTime=" + startTime +
            ", endTime=" + endTime +
            ", failure=" + failure +
            ", result=" + result +
            '}';
    }
}
