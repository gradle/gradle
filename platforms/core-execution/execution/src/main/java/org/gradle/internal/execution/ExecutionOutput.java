/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.execution;

import javax.annotation.Nullable;

/**
 * The result of executing the user code.
 */
public interface ExecutionOutput {
    /**
     * Whether any significant amount of work has happened while executing the user code.
     * <p>
     * What amounts to "significant work" is up to the type of work implementation.
     */
    WorkResult getDidWork();

    /**
     * Implementation-specific output of executing the user code.
     */
    @Nullable
    Object getOutput();

    /**
     * Whether this output should be stored in the build cache.
     */
    default boolean canStoreInCache() {
        return true;
    }
}
