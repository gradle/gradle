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

import com.google.common.annotations.VisibleForTesting;
import com.sun.tools.javac.api.ClientCodeWrapper;
import com.sun.tools.javac.api.DiagnosticFormatter;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.JavacMessages;
import com.sun.tools.javac.util.Log;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.problems.ProblemSpec;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.GeneralDataSpec;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblemReporter;
import org.gradle.api.problems.internal.InternalProblemSpec;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import java.util.Locale;
import java.util.function.Function;

/**
 * A {@link DiagnosticListener} that consumes {@link Diagnostic} messages, and reports them as Gradle {@link Problems}.
 */
// If this annotation is not present, all diagnostic messages would be wrapped in a ClientCodeWrapper.
// We don't need this wrapping feature, hence the trusted annotation.
@ClientCodeWrapper.Trusted
public class DiagnosticToProblemListener implements DiagnosticListener<JavaFileObject> {

    public static final String FORMATTER_FALLBACK_MESSAGE = "Failed to format diagnostic message, falling back to default message formatting";

    private static final Logger LOGGER = Logging.getLogger(DiagnosticToProblemListener.class);

    private final InternalProblemReporter problemReporter;
    private final Function<Diagnostic<? extends JavaFileObject>, String> messageFormatter;

    DiagnosticToProblemListener(InternalProblemReporter problemReporter, Function<Diagnostic<? extends JavaFileObject>, String> messageFormatter) {
        this.problemReporter = problemReporter;
        this.messageFormatter = messageFormatter;
    }

    public DiagnosticToProblemListener(InternalProblemReporter problemReporter, Context context) {
        this.problemReporter = problemReporter;
        this.messageFormatter = diagnostic -> {
            try {
                DiagnosticFormatter<JCDiagnostic> formatter = Log.instance(context).getDiagnosticFormatter();
                return formatter.format((JCDiagnostic) diagnostic, JavacMessages.instance(context).getCurrentLocale());
            } catch (Exception ex) {
                // If for some reason the formatter fails, we can still get the message
                LOGGER.error(FORMATTER_FALLBACK_MESSAGE);
                return diagnostic.getMessage(Locale.getDefault());
            }
        };
    }

    @Override
    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
        problemReporter.reporting(spec -> buildProblem(diagnostic, spec));
    }

    @VisibleForTesting
    void buildProblem(Diagnostic<? extends JavaFileObject> diagnostic, ProblemSpec spec) {
        spec.id(mapKindToId(diagnostic.getKind()), mapKindToLabel(diagnostic.getKind()), GradleCoreProblemGroup.compilation().java());
        spec.severity(mapKindToSeverity(diagnostic.getKind()));
        addFormattedMessage(spec, diagnostic);
        addDetails(spec, diagnostic);
        addLocations(spec, diagnostic);
    }

    private void addFormattedMessage(ProblemSpec spec, Diagnostic<? extends JavaFileObject> diagnostic) {
        String formatted = messageFormatter.apply(diagnostic);
        System.err.println(formatted);
        ((InternalProblemSpec) spec).additionalData(GeneralDataSpec.class, data -> data.put("formatted", formatted)); // TODO (donat) Introduce custom additional data type for compilation problems
    }

    private static void addDetails(ProblemSpec spec, Diagnostic<? extends JavaFileObject> diagnostic) {
        String diagnosticMessage = diagnostic.getMessage(Locale.getDefault());
        if (diagnosticMessage != null) {
            spec.details(diagnosticMessage);
        }
    }

    private static void addLocations(ProblemSpec spec, Diagnostic<? extends JavaFileObject> diagnostic) {
        String resourceName = diagnostic.getSource() != null ? getPath(diagnostic.getSource()) : null;
        int line = clampLocation(diagnostic.getLineNumber());
        int column = clampLocation(diagnostic.getColumnNumber());
        int position = clampLocation(diagnostic.getPosition());
        int start = clampLocation(diagnostic.getStartPosition());
        int end = clampLocation(diagnostic.getEndPosition());

        // We only set the location if we have a resource to point to
        if (resourceName != null) {
            spec.fileLocation(resourceName);
            // If we know the line ...
            if (0 < line) {
                // ... and the column ...
                if (0 < column) {
                    // ... and we know how long the error is (i.e. end - start)
                    // (documentation says that getEndPosition() will be NOPOS if and only if the getPosition() is NOPOS)
                    if (0 < position) {
                        // ... we can report the line, column, and extent ...
                        spec.lineInFileLocation(resourceName, line, column, end - position);
                    } else {
                        // ... otherwise we can still report the line and column
                        spec.lineInFileLocation(resourceName, line, column);
                    }
                } else {
                    // ... otherwise we can still report the line
                    spec.lineInFileLocation(resourceName, line);
                }
            }

            // If we know the offsets ...
            // (offset doesn't require line and column to be set, hence the separate check)
            // (documentation says that getEndPosition() will be NOPOS iff getPosition() is NOPOS)
            if (0 < position) {
                // ... we can report the start and extent
                spec.offsetInFileLocation(resourceName, position, end - position);
            }
        }
    }

    /**
     * Clamp the value to an int, or return {@link Diagnostic#NOPOS} if the value is too large.
     * <p>
     * This is used to ensure that we don't report invalid locations.
     *
     * @param value the value to clamp
     * @return either the clamped value, or {@link Diagnostic#NOPOS}
     */
    private static int clampLocation(long value) {
        if (value > Integer.MAX_VALUE) {
            return Math.toIntExact(Diagnostic.NOPOS);
        } else {
            return (int) value;
        }
    }

    private static String getPath(JavaFileObject fileObject) {
        return fileObject.getName();
    }

    private static String mapKindToId(Diagnostic.Kind kind) {
        switch (kind) {
            case ERROR:
                return "java-compilation-error";
            case WARNING:
            case MANDATORY_WARNING:
                return "java-compilation-warning";
            case NOTE:
                return "java-compilation-note";
            case OTHER:
                return "java-compilation-problem";
            default:
                return "unknown-java-compilation-problem";
        }
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
