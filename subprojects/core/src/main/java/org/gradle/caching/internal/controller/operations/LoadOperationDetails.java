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
import org.gradle.caching.internal.controller.BuildCacheLoadCommand;
import org.gradle.caching.internal.controller.BuildCacheServiceRole;

public class LoadOperationDetails implements BuildCacheLoadBuildOperationType.Details {

    private final BuildCacheServiceRole role;
    private final BuildCacheLoadCommand loadOp;

    public LoadOperationDetails(BuildCacheServiceRole role, BuildCacheLoadCommand loadOp) {
        this.role = role;
        this.loadOp = loadOp;
    }

    @Override
    public String getRole() {
        return role.getDisplayName();
    }

    @Override
    public String getCacheKey() {
        return loadOp.getKey().getHashCode();
    }
}
