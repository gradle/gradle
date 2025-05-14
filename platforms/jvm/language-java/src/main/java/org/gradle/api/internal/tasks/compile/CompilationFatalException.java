/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.tasks.compile;

import org.gradle.internal.exceptions.CompilationFailedIndicator;
import org.jspecify.annotations.Nullable;

/**
 * Indicates a fatal error during compilation. Gradle will not try to recover output files from a previous compilation.
 */
public class CompilationFatalException extends RuntimeException implements CompilationFailedIndicator {

    public CompilationFatalException(Throwable cause) {
        super("Unrecoverable compilation error: " + cause.getMessage(), cause);
    }

    @Override
    @Nullable
    public String getDiagnosticCounts() {
        return null;
    }

    @Override
    public String getShortMessage() {
        return getMessage();
    }
}
