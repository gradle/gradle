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

import org.gradle.api.problems.ProblemReporter;
import org.gradle.api.problems.ProblemSpec;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.Severity;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A {@link DiagnosticListener} that consumes {@link Diagnostic} messages, and reports them as Gradle {@link Problems}.
 *
 * @since 8.5
 */
public class DiagnosticToProblemListener implements DiagnosticListener<JavaFileObject> {

    private final ProblemReporter problemReporter;

    /** A collection of filters that will serve as a predicate to determine if a diagnostic should be reported or not.*/
    private final Collection<Predicate<Diagnostic<? extends JavaFileObject>>> diagnosticFilters = new ArrayList<>();

    /**
     * A map of explicit overrides between {@link Diagnostic.Kind} and {@link Severity}.
     * <p>
     * This will take precedence over the default mapping defined in {@link #mapKindToSeverity(Diagnostic.Kind)}.
     */
    private final Map<Diagnostic.Kind, Diagnostic.Kind> severityOverrides = new HashMap<>();


    public DiagnosticToProblemListener(ProblemReporter problemReporter) {
        this.problemReporter = problemReporter;
    }

    public void addDiagnosticFilter(Predicate<Diagnostic<? extends JavaFileObject>> diagnosticFilter) {
        diagnosticFilters.add(diagnosticFilter);
    }

    public void addSeverityOverride(Diagnostic.Kind fromKind, Diagnostic.Kind toKind) {
        severityOverrides.put(fromKind, toKind);
    }

    @Override
    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
        // If any of the filters return true, we should not report the diagnostic
        if (diagnosticFilters.stream().anyMatch(filter -> filter.test(diagnostic))) {
            return;
        }

        String message = diagnostic.getMessage(Locale.getDefault());

        // Apply any overrides or keep the value as is
        Diagnostic.Kind kind = severityOverrides.computeIfAbsent(diagnostic.getKind(), key -> diagnostic.getKind());
        String label = mapKindToLabel(kind);
        Severity severity = mapKindToSeverity(kind);

        String resourceName = diagnostic.getSource() != null ? getPath(diagnostic.getSource()) : null;
        int line = Math.toIntExact(diagnostic.getLineNumber());
        int column = Math.toIntExact(diagnostic.getColumnNumber());
        int length = Math.toIntExact(diagnostic.getEndPosition() - diagnostic.getStartPosition());

        problemReporter.reporting(problem -> {
            ProblemSpec spec = problem
                .label(label)
                .severity(severity)
                .details(message);

            // The category of the problem depends on the severity
            switch (severity) {
                case ADVICE:
                    spec.category("compilation", "java", "compilation-advice");
                    break;
                case WARNING:
                    spec.category("compilation", "java", "compilation-warning");
                    break;
                case ERROR:
                    spec.category("compilation", "java", "compilation-failed");
                    break;
            }

            // We only set the location if we have a resource to point to
            if (resourceName != null) {
                spec.lineInFileLocation(resourceName, line, column, length);
            }
        });

        // We need to print the message to stderr as well, as it was the default behavior of the compiler
        System.err.println(message);
    }

    private static String getPath(JavaFileObject fileObject) {
        return fileObject.getName();
    }

    private static String mapKindToLabel(Diagnostic.Kind kind) {
        switch (kind) {
            case ERROR:
                return "Java compilation error";
            case WARNING:
            case MANDATORY_WARNING:
                return "Java compilation warning";
            case NOTE:
                return "Java compilation note";
            case OTHER:
                return "Java compilation problem";
            default:
                return "Unknown java compilation problem";
        }
    }

    private static Severity mapKindToSeverity(Diagnostic.Kind kind) {
        switch (kind) {
            case ERROR:
                return Severity.ERROR;
            case WARNING:
            case MANDATORY_WARNING:
                return Severity.WARNING;
            case NOTE:
            case OTHER:
            default:
                return Severity.ADVICE;
        }
    }
}
