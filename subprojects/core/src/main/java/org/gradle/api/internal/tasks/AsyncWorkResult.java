/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.work.AsyncWorkCompletion;

/**
 * Provides information about some work which was performed asynchronously.
 */
public interface AsyncWorkResult extends WorkResult, AsyncWorkCompletion {
    /**
     * Registers a callback to be executed once the work represented by this
     * {@link WorkResult} has completed.  Note that multiple callbacks are not
     * guaranteed to be executed in any order.  {@link #waitForCompletion()}
     * will block until all callbacks have been executed.
     *
     * @param callback
     */
    void onCompletion(Runnable callback);
}
