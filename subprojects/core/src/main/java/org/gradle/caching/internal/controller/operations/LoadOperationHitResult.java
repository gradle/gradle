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

package org.gradle.caching.internal.controller.operations;

import org.gradle.caching.internal.BuildCacheLoadBuildOperationType;

public class LoadOperationHitResult implements BuildCacheLoadBuildOperationType.Result {

    private final long size;
    private final long entryCount;

    public LoadOperationHitResult(long size, long entryCount) {
        this.size = size;
        this.entryCount = entryCount;
    }

    @Override
    public long getArchiveSize() {
        return size;
    }

    @Override
    public long getArchiveEntryCount() {
        return entryCount;
    }

}
