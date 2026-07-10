/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.provider;

import java.io.Serializable;
import java.util.concurrent.Callable;

/**
 * A provider whose value is computed by a {@link Callable}.
 *
 * The given {@link Callable} is stored to the configuration cache, so it must only hold references
 * to configuration cache safe state.
 *
 * Task dependencies attached to the computed value are ignored by this provider.
 *
 * <h2>Configuration Cache Behavior</h2>
 * <b>Lazy</b>. The given {@link Callable} is stored to the cache so the value can be recomputed on each run.
 */
class ChangingProvider<T> extends DefaultProvider<T> {

    public <CALLABLE extends Callable<T> & Serializable> ChangingProvider(CALLABLE value) {
        super(value);
    }

    @Override
    protected String toStringNoReentrance() {
        return "changing(?)";
    }

    @Override
    public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        return ExecutionTimeValue.changingValue(this);
    }
}
