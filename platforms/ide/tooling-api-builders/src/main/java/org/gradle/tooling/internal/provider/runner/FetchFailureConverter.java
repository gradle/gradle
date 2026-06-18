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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.problems.internal.ProblemInternal;
import org.gradle.internal.build.event.types.DefaultFailure;
import org.gradle.internal.build.event.types.FailureCache;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.tooling.internal.protocol.InternalBasicProblemDetailsVersion3;
import org.gradle.tooling.internal.protocol.InternalFailure;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Build tree scoped {@link FailureCache} for the fetch path: converts source {@link Failure} values into protocol
 * {@link InternalFailure} values, interning by {@link Throwable} identity at every recursion step (see
 * {@link FailureCache} for why this reuse matters). Its lifetime is this build tree, so it is freed when the model
 * phase ends and never retains failure graphs across syncs.
 */
@ServiceScope(Scope.BuildTree.class)
@NullMarked
public class FetchFailureConverter implements FailureCache {

    private final ConcurrentMap<Throwable, InternalFailure> cache = new ConcurrentHashMap<>();

    InternalFailure convert(Failure failure) {
        return DefaultFailure.fromFailure(failure, FetchFailureConverter::noProblemDetails, this);
    }

    @Nullable
    private static InternalBasicProblemDetailsVersion3 noProblemDetails(ProblemInternal problem) {
        return null;
    }

    @Override
    @Nullable
    public InternalFailure get(Throwable original) {
        return cache.get(original);
    }

    @Override
    public InternalFailure intern(Throwable original, InternalFailure converted) {
        InternalFailure previous = cache.putIfAbsent(original, converted);
        return previous != null ? previous : converted;
    }
}
