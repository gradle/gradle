/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.execution.fingerprint;

public class InputFileFingerprintingException extends RuntimeException {
    private final String propertyName;

    public InputFileFingerprintingException(String propertyName, Throwable cause) {
        super(String.format("Cannot fingerprint input file property '%s': %s", propertyName, cause.getMessage()), cause);
        this.propertyName = propertyName;
    }

    private InputFileFingerprintingException(String formattedMessage, Throwable cause, String propertyName) {
        super(formattedMessage, cause);
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
