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

import javax.annotation.Nullable;
import java.util.Optional;

public class CompilationFailedException extends RuntimeException implements CompilationFailedIndicator {

    public static final String RESOLUTION_MESSAGE = "Check your code and dependencies to fix the compilation error(s)";
    public static final String COMPILATION_FAILED_DETAILS_ABOVE = "Compilation failed; see the compiler error output for details.";
    public static final String COMPILATION_FAILED_DETAILS_BELOW = "Compilation failed; see the compiler output below.";

    private final ApiCompilerResult compilerPartialResult;
    private final String diagnosticCounts;

    public CompilationFailedException() {
        this((ApiCompilerResult) null);
    }

    public CompilationFailedException(int exitCode) {
        super(String.format("Compilation failed with exit code %d; see the compiler error output for details.", exitCode));
        this.compilerPartialResult = null;
        this.diagnosticCounts = null;
    }

    public CompilationFailedException(Throwable cause) {
        super(cause);
        this.compilerPartialResult = null;
        this.diagnosticCounts = null;
    }

    public CompilationFailedException(@Nullable ApiCompilerResult result) {
        super(COMPILATION_FAILED_DETAILS_ABOVE);
        this.compilerPartialResult = result;
        this.diagnosticCounts = null;
    }

    public CompilationFailedException(ApiCompilerResult result, String diagnosticCounts) {
        super(COMPILATION_FAILED_DETAILS_BELOW);
        this.compilerPartialResult = result;
        this.diagnosticCounts = diagnosticCounts;
    }

    public Optional<ApiCompilerResult> getCompilerPartialResult() {
        return Optional.ofNullable(compilerPartialResult);
    }

    @Override
    @Nullable
    public String getDiagnosticCounts() {
        return diagnosticCounts;
    }
}
