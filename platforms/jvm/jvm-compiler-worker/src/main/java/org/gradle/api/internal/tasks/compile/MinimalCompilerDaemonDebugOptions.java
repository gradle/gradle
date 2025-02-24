/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.compile;

import javax.annotation.Nullable;
import java.io.Serializable;

/**
 * Counterpart to {@link org.gradle.api.tasks.compile.DebugOptions}, intended to be serialized
 * and loaded in a worker process.
 */
public class MinimalCompilerDaemonDebugOptions implements Serializable {

    private final String debugLevel;

    public MinimalCompilerDaemonDebugOptions(@Nullable String debugLevel) {
        this.debugLevel = debugLevel;
    }

    @Nullable
    public String getDebugLevel() {
        return debugLevel;
    }

}
