/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.internal.reflect.validation;

public enum Severity {
    /**
     * A validation warning, emitted as a deprecation warning during runtime.
     */
    WARNING("Warning"),

    /**
     * A validation error, emitted as a failure cause during runtime.
     */
    ERROR("Error");

    private final String displayName;

    Severity(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public boolean isWarning() {
        return this != ERROR;
    }
}
