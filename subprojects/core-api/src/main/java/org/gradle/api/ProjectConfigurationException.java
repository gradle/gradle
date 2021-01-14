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

package org.gradle.api;

import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.DefaultMultiCauseException;

/**
 * Indicates a problem that occurs during project configuration.
 */
@Contextual
public class ProjectConfigurationException extends DefaultMultiCauseException {
    public ProjectConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception with the given message and causes.
     * @param message The message
     * @param causes The causes
     * @since 5.1
     */
    public ProjectConfigurationException(String message, Iterable<? extends Throwable> causes) {
        super(message, causes);
    }
}
