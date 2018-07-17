/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.Action;

/**
 * This service can disallow method called under the wrapped delegate action. Each method that needs to be protected needs to call the assertion method in the entry point of the method. Then each code segment that need to disallow method will be wrapped in an action by the service.
 */
public interface ProtectApiService {
    /**
     * Wraps configuration action to protect illegal method calls while executing.
     *
     * @param delegate the delegated action
     * @param <T> the type the action is mutating
     * @return protecting action
     */
    <T> Action<T> wrap(Action<? super T> delegate);

    /**
     * Assert the protected method can be called.
     *
     * @param methodName a string representing the protected method
     */
    void assertMethodExecutionAllowed(String methodName);
}
