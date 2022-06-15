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

package org.gradle.api.logging.configuration;

/**
 * Specifies the warning mode a user wants to see.
 *
 * @since 4.5
 */
public enum WarningMode {
    /**
     * Show all warnings.
     */
    All(true),

    /**
     * Display a summary at the end of the build instead of rendering all warnings into the console output.
     */
    Summary(false),

    /**
     * No deprecation warnings at all.
     */
    None(false),

    /**
     * Show all warnings and fail the build if any warning present
     *
     * @since 5.6
     */
    Fail(true);

    private boolean displayMessages;

    WarningMode(boolean displayMessages) {
        this.displayMessages = displayMessages;
    }

    /**
     * Indicates whether deprecation messages are to be printed in-line
     *
     * @return {@code true} if messages are to be printed, {@code false} otherwise
     *
     * @since 5.6
     */
    public boolean shouldDisplayMessages() {
        return displayMessages;
    }
}
