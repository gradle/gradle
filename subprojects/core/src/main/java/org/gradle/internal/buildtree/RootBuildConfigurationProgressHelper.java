/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Helper interface to track if certain root build operations are already counted in the total progress
 * of the root build configuration operation.
 */
@ServiceScope(Scope.BuildTree.class)
public interface RootBuildConfigurationProgressHelper {
    boolean isWorkGraphCounted();

    boolean isStoreCounted();

    boolean isLoadAfterStoreCounted();

    /**
     * {@return how much additional total progress the root build configuration operation should have}
     */
    default int getAdditionalTotalProgress() {
        return (isWorkGraphCounted() ? 1 : 0) + (isStoreCounted() ? 1 : 0) + (isLoadAfterStoreCounted() ? 1 : 0);
    }
}
