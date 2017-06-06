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

package org.gradle.internal.nativeintegration;

/**
 * Encapsulates what happened when we tried to modify the environment.
 */
public enum EnvironmentModificationResult {
    SUCCESS(null),
    JAVA_9_IMMUTABLE_ENVIRONMENT("Java 9 does not support modifying environment variables."),
    UNSUPPORTED_ENVIRONMENT("There is no native integration with this operating environment.");

    private final String reason;

    EnvironmentModificationResult(String reason) {
        this.reason = reason;
    }

    @Override
    public String toString() {
        return reason;
    }

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
