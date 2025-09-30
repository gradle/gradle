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

package org.gradle.tooling.internal.provider.connection;

import com.google.common.collect.ImmutableList;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.InternalFetchModelResult;
import org.gradle.tooling.model.Model;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

@NullMarked
public class DefaultInternalFetchModelResult<T, M> implements InternalFetchModelResult<T, M> {

    public static <T extends Model, M> InternalFetchModelResult<T, M> ofModel(M model) {
        return new DefaultInternalFetchModelResult<>(null, model, ImmutableList.of());
    }

    public static <T extends Model, M> InternalFetchModelResult<T, M> ofFailure(InternalFailure failure) {
        return new DefaultInternalFetchModelResult<>(null, null, ImmutableList.of(failure));
    }

    private final @Nullable T target;
    private final @Nullable M model;
    private final ImmutableList<InternalFailure> failures;

    private DefaultInternalFetchModelResult(@Nullable T target, @Nullable M model, ImmutableList<InternalFailure> failures) {
        this.target = target;
        this.model = model;
        this.failures = failures;
    }

    @Override
    public @Nullable T getTarget() {
        return target;
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
