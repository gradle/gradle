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

import javax.annotation.Nullable;

/**
 * Controls how user input is routed to the daemon.
 */
public interface UserInputReceiver {
    /**
     * Requests that a line of text should be received from the user, for example via this process' stdin, and forwarded to the {link UserInputReader} instance in the daemon.
     * Does not block waiting for the input.
     */
    void readAndForwardText(Normalizer normalizer);

    /**
     * Requests that bytes should be read from this process' stdin and forwarded to the daemon.
     * Does not block waiting for the input.
     */
    void readAndForwardStdin();

    interface Normalizer {
        /**
         * Validates and normalizes the given text received from the user.
         *
         * @return The normalized text to forward to the daemon.
         */
        @Nullable
        String normalize(String text);
    }
}
