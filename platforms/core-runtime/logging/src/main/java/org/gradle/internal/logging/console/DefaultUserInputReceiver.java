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

import org.gradle.internal.logging.events.PromptOutputEvent;

import java.util.concurrent.atomic.AtomicReference;

public class DefaultUserInputReceiver implements GlobalUserInputReceiver {
    private final AtomicReference<UserInputReceiver> delegate = new AtomicReference<UserInputReceiver>();

    @Override
    public void readAndForwardText(PromptOutputEvent event) {
        UserInputReceiver userInput = delegate.get();
        if (userInput == null) {
            throw new IllegalStateException("User input has not been initialized.");
        }
        userInput.readAndForwardText(event);
    }

    @Override
    public void dispatchTo(UserInputReceiver userInput) {
        if (!delegate.compareAndSet(null, userInput)) {
            throw new IllegalStateException("User input has already been initialized.");
        }
    }

    @Override
    public void stopDispatching() {
        delegate.set(null);
    }
}
