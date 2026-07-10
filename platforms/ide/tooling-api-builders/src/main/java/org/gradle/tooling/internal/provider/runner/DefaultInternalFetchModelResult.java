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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.InternalFetchModelResult;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.Collection;

@NullMarked
class DefaultInternalFetchModelResult<M> implements InternalFetchModelResult<M>, Serializable {
    private final @Nullable M model;
    private final Collection<InternalFailure> failures;

    public DefaultInternalFetchModelResult(@Nullable M model, Collection<InternalFailure> failures) {
        this.model = model;
        this.failures = failures;
    }

    @Override
    public @Nullable M getModel() {
        return model;
    }

    @Override
    public Collection<InternalFailure> getFailures() {
        return failures;
    }
}
