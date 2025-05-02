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

import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.operations.BuildCacheRemoteLoadBuildOperationType;

public class LoadOperationDetails implements BuildCacheRemoteLoadBuildOperationType.Details {

    private final BuildCacheKey buildCacheKey;

    public LoadOperationDetails(BuildCacheKey buildCacheKey) {
        this.buildCacheKey = buildCacheKey;
    }

    @Override
    public String getCacheKey() {
        return buildCacheKey.getHashCode();
    }
}
