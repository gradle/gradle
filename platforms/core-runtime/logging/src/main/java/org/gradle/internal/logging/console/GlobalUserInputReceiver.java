/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.logging.console;

import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.concurrent.ThreadSafe;

/**
 * The global {@link UserInputReceiver} instance.
 */
@ServiceScope(Scopes.Global.class)
@ThreadSafe
public interface GlobalUserInputReceiver extends UserInputReceiver {
    /**
     * Defines the {@link UserInputReceiver} instance for this service to delegate to.
     *
     * <p>Typically, the provided instance will be able to read from the console and forward the result to the daemon, but doesn't have to.
     * The instance is not injected directly into this service as the instance will be constructed in some other scope.
     * </p>
     */
    void dispatchTo(UserInputReceiver userInput);

    /**
     * Discards the current {@link UserInputReceiver} instance used by this service.
     */
    void stopDispatching();
}
