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

package org.gradle.internal.execution;

import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Try;

import java.util.Optional;

/**
 * A deferred result of a unit of work execution.
 */
public interface DeferredResult<T> {

    Try<T> getResult();

    /**
     * The origin metadata of the result.
     *
     * If a previously produced output was reused in some way, the reused output's origin metadata is returned.
     * If the output was produced in this request, then the current execution's origin metadata is returned.
     */
    Optional<OriginMetadata> getOriginMetadata();
}
