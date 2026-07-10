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

import org.gradle.tooling.UnsupportedVersionException;

/**
 * Thrown when a {@link org.gradle.tooling.LongRunningOperation} has been configured
 * with unsupported settings. For example {@link org.gradle.tooling.LongRunningOperation#setJavaHome(java.io.File)}
 * might not be supported by the target Gradle version.
 *
 * @since 1.0
 */
public class UnsupportedOperationConfigurationException extends UnsupportedVersionException {
    public UnsupportedOperationConfigurationException(String message) {
        super(message);
    }

    public UnsupportedOperationConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
