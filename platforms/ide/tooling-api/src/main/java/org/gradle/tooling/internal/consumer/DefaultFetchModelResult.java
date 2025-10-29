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

package org.gradle.tooling.internal.consumer;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.lazy.Lazy;
import org.gradle.tooling.Failure;
import org.gradle.tooling.FetchModelResult;
import org.gradle.tooling.internal.consumer.parameters.BuildProgressListenerAdapter;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.function.Supplier;

@NullMarked
public class DefaultFetchModelResult<M> implements FetchModelResult<M> {
    @Nullable
    private final M model;
    private final Lazy<Collection<? extends Failure>> failures;

    private DefaultFetchModelResult(@Nullable M model, Supplier<Collection<? extends Failure>> failures) {
        this.model = model;
        this.failures = Lazy.locking().of(failures);
    }

    @Override
    public @Nullable M getModel() {
        return model;
    }

    @Override
    public Collection<? extends Failure> getFailures() {
        return failures.get();
    }

    public static <M> DefaultFetchModelResult<M> of(@Nullable M model, Collection<? extends InternalFailure> failures) {
        return new DefaultFetchModelResult<>(model, () -> BuildProgressListenerAdapter.toFailures(failures));
    }

    public static <M> DefaultFetchModelResult<M> success(M model) {
        return new DefaultFetchModelResult<>(model, ImmutableList::of);
    }

    public static <M> DefaultFetchModelResult<M> failure(Exception exception) {
        return new DefaultFetchModelResult<>(null, () -> ImmutableList.of(DefaultFailure.fromThrowable(exception)));
    }
}
