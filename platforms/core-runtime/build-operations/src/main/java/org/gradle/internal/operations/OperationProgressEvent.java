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

package org.gradle.internal.operations;

import javax.annotation.Nullable;

public final class OperationProgressEvent {

    private final long time;
    @Nullable
    private final Object details;

    public OperationProgressEvent(long time, @Nullable Object details) {
        this.time = time;
        this.details = details;
    }

    public long getTime() {
        return time;
    }

    @Nullable
    public Object getDetails() {
        return details;
    }
}
