/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.tooling.exceptions;

import org.gradle.tooling.GradleConnectionException;

/**
 * Thrown when the {@link org.gradle.tooling.LongRunningOperation} has been configured
 * with unsupported build arguments. For more information see the
 * {@link org.gradle.tooling.LongRunningOperation#withArguments(String...)} method.
 */
public class UnsupportedBuildArgumentException extends GradleConnectionException {
    public UnsupportedBuildArgumentException(String message) {
        super(message);
    }

    public UnsupportedBuildArgumentException(String message, Throwable cause) {
        super(message, cause);
    }
}
