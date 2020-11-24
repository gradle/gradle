/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.tooling.internal.protocol;

import java.util.List;
import java.util.function.Supplier;

/**
 * <p>DO NOT CHANGE THIS INTERFACE - it is part of the cross-version protocol.
 *
 * <p>Consumer compatibility: This interface is used by all consumer versions from 6.8.</p>
 * <p>Provider compatibility: This interface is implemented by all provider versions from 6.8.</p>
 *
 * @since 6.8
 */
public interface InternalActionAwareBuildController {
    /**
     * Can the given project model be queried in parallel for this build?
     */
    boolean getCanQueryProjectModelInParallel(Class<?> modelType);

    /**
     * Runs the given actions in parallel and returns the results. The results should be returned in the same order as the actions that produce them.
     */
    <T> List<T> run(List<Supplier<T>> actions);
}
