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

import org.gradle.caching.internal.operations.BuildCacheRemoteLoadBuildOperationType;

public class LoadOperationMissResult implements BuildCacheRemoteLoadBuildOperationType.Result {

    public static final BuildCacheRemoteLoadBuildOperationType.Result INSTANCE = new LoadOperationMissResult();

    private LoadOperationMissResult() {
    }

    @Override
    public boolean isHit() {
        return false;
    }

    @Override
    public long getArchiveSize() {
        return 0;
    }

}
