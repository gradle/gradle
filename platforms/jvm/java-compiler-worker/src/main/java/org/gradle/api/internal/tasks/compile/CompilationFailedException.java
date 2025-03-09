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

import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.internal.exceptions.CompilationFailedIndicator;
import org.gradle.problems.internal.rendering.ProblemRenderer;

import javax.annotation.Nullable;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;

public class CompilationFailedException extends RuntimeException implements CompilationFailedIndicator {

    public static final String RESOLUTION_MESSAGE = "Check your code and dependencies to fix the compilation error(s)";
    public static final String COMPILATION_FAILED_DETAILS_ABOVE = "Compilation failed; see the compiler error output for details.";
    public static final String COMPILATION_FAILED_DETAILS_BELOW = "Compilation failed; see the compiler output below.";

    private final ApiCompilerResult compilerPartialResult;
    private final String diagnosticCounts;
    private final String shortMessage;

    public CompilationFailedException() {
        this((ApiCompilerResult) null);
    }

    public CompilationFailedException(int exitCode) {
        super(String.format("Compilation failed with exit code %d; see the compiler error output for details.", exitCode));
        this.compilerPartialResult = null;
        this.diagnosticCounts = null;
        shortMessage = getMessage();
    }

    public CompilationFailedException(Throwable cause) {
        super(cause);
        this.compilerPartialResult = null;
        this.diagnosticCounts = null;
        shortMessage = getMessage();
    }

    public CompilationFailedException(@Nullable ApiCompilerResult result) {
        super(COMPILATION_FAILED_DETAILS_ABOVE);
        this.compilerPartialResult = result;
        this.diagnosticCounts = null;
        this.shortMessage = getMessage();
    }

    CompilationFailedException(ApiCompilerResult result, List<InternalProblem> reportedProblems, String diagnosticCounts) {
        super(exceptionMessage(COMPILATION_FAILED_DETAILS_BELOW + System.lineSeparator(), reportedProblems, diagnosticCounts));
        this.compilerPartialResult = result;
        this.diagnosticCounts = diagnosticCounts;
        this.shortMessage = COMPILATION_FAILED_DETAILS_BELOW;
    }

    /*
     * Build Scans do not consume Problems API reports to render compilation errors yet. To keep the error message in scans consistent with the console, we need to render the problems in the exception message.
     */
    private static String exceptionMessage(String prefix, List<InternalProblem> problems, String diagnosticCounts) {
        StringWriter result = new StringWriter();
        result.append(prefix);
        new ProblemRenderer(result).render(problems);
        result.append(System.lineSeparator());
        result.append(diagnosticCounts);
        return result.toString();
    }

    public Optional<ApiCompilerResult> getCompilerPartialResult() {
        return Optional.ofNullable(compilerPartialResult);
    }

    @Override
    @Nullable
    public String getDiagnosticCounts() {
        return diagnosticCounts;
    }

    @Override
    public String getShortMessage() {
        return shortMessage;
    }
}
