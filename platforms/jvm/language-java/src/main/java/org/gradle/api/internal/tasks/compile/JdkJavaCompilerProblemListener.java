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

import org.gradle.api.problems.Problems;
import org.gradle.api.problems.Severity;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import java.util.Locale;

/**
 * A {@link DiagnosticListener} that adopts {@link Diagnostic} problems into Gradle's {@link Problems}, and reports them.
 */
public class JdkJavaCompilerProblemListener implements DiagnosticListener<JavaFileObject> {

    private final Problems problems;

    public JdkJavaCompilerProblemListener(Problems problems) {
        this.problems = problems;
    }

    @Override
    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
        String message = diagnostic.getMessage(Locale.getDefault());

        String label = mapKindToLabel(diagnostic.getKind());
        String resourceName = diagnostic.getSource().getName();
        Integer line = Math.toIntExact(diagnostic.getLineNumber());
        Integer column = Math.toIntExact(diagnostic.getColumnNumber());
        Severity severity = mapKindToSeverity(diagnostic.getKind());

        problems.createProblem(problem -> problem
            .label(label)
            .undocumented()
            .location(resourceName, line, column)
            .category("java", "compilation")
            .severity(severity)
            .details(message)
        ).report();
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
