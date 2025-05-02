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

package org.gradle.internal.build.event.types;

import org.gradle.tooling.internal.protocol.events.InternalIncrementalTaskResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskCachedResult;
import org.gradle.tooling.internal.protocol.events.InternalTaskSuccessResult;

import java.util.List;

public class DefaultTaskSuccessResult extends AbstractTaskResult implements InternalTaskSuccessResult, InternalTaskCachedResult, InternalIncrementalTaskResult {

    private final boolean upToDate;
    private final boolean fromCache;

    public DefaultTaskSuccessResult(long startTime, long endTime, boolean upToDate, boolean fromCache, String outcomeDescription, boolean incremental, List<String> executionReasons) {
        super(startTime, endTime, outcomeDescription, incremental, executionReasons);
        this.upToDate = upToDate;
        this.fromCache = fromCache;
    }

    @Override
    public boolean isUpToDate() {
        return upToDate;
    }

    @Override
    public boolean isFromCache() {
        return fromCache;
    }

}
