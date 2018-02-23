/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.resolve.result;

import javax.annotation.Nullable;

public interface BuildableTypedResolveResult<T, E extends Throwable> extends ResolveResult {
    /**
     * Returns the result.
     *
     * @throws E if resolution was not successful.
     */
    T getResult() throws E;

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    E getFailure();

    /**
     * Marks the resolution as completed with the given value.
     */
    void resolved(T result);

    /**
     * Marks the resolution as failed with the given failure.
     */
    void failed(E failure);
}
