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

package org.gradle.api.internal.tasks.properties;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.internal.GeneratedSubclass;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.PropertyProblemBuilder;
import org.gradle.internal.typeconversion.UnsupportedNotationException;
import org.gradle.model.internal.type.ModelType;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;

import static org.gradle.internal.reflect.validation.Severity.ERROR;

public enum ValidationActions implements ValidationAction {
    NO_OP("file collection") {
        @Override
        public void doValidate(String propertyName, Object value, TaskValidationContext context) {
        }
    },
    INPUT_FILE_VALIDATOR("file") {
        @Override
        public void doValidate(String propertyName, Object value, TaskValidationContext context) {
            File file = toFile(context, value);
            if (!file.exists()) {
                reportMissingInput(context, "File", propertyName, file);
            } else if (!file.isFile()) {
                reportUnexpectedInputKind(context, "File", propertyName, file);
            }
        }
    },
    INPUT_DIRECTORY_VALIDATOR("directory") {
        @Override
        public void doValidate(String propertyName, Object value, TaskValidationContext context) {
            File directory = toDirectory(context, value);
            if (!directory.exists()) {
                reportMissingInput(context, "Directory", propertyName, directory);
            } else if (!directory.isDirectory()) {
                reportUnexpectedInputKind(context, "Directory", propertyName, directory);
            }
        }
    },
    OUTPUT_DIRECTORY_VALIDATOR("file") {
        @Override
        public void doValidate(String propertyName, Object value, TaskValidationContext context) {
            File directory = toFile(context, value);
            validateNotInReservedFileSystemLocation(propertyName, context, directory);
            if (directory.exists()) {
                if (!directory.isDirectory()) {
                    reportCannotWriteToDirectory(propertyName, context, directory, "'" + directory + "' is not a directory");
                }
            } else {
                for (File candidate = directory.getParentFile(); candidate != null && !candidate.isDirectory(); candidate = candidate.getParentFile()) {
                    if (candidate.exists() && !candidate.isDirectory()) {
                        reportCannotWriteToDirectory(propertyName, context, candidate, "'" + directory + "' ancestor '" + candidate + "' is not a directory");
                        return;
                    }
                }
            }
        }
    },
    OUTPUT_FILE_VALIDATOR("file") {
        @Override
        public void doValidate(String propertyName, Object value, TaskValidationContext context) {
            File file = toFile(context, value);
            validateNotInReservedFileSystemLocation(propertyName, context, file);
            if (file.exists()) {
                if (file.isDirectory()) {
                    reportCannotWriteFileToDirectory(propertyName, context, file);
                }
                // else, assume we can write to anything that exists and is not a directory
            } else {
                for (File candidate = file.getParentFile(); candidate != null && !candidate.isDirectory(); candidate = candidate.getParentFile()) {
                    if (candidate.exists() && !candidate.isDirectory()) {
                        reportCannotCreateParentDirectories(propertyName, context, file, candidate);
                        break;
                    }
                }
            }
        }
    },
    OUTPUT_FILE_TREE_VALIDATOR("directory") {
        @Override
        public void doValidate(String propertyName, Object value, TaskValidationContext context) {
            File directory = toFile(context, value);
            validateNotInReservedFileSystemLocation(propertyName, context, directory);
            if (directory.exists()) {
                if (!directory.isDirectory()) {
                    reportFileTreeWithFileRoot(propertyName, context, directory);
                }
            } else {
                for (File candidate = directory.getParentFile(); candidate != null && !candidate.isDirectory(); candidate = candidate.getParentFile()) {
                    if (candidate.exists() && !candidate.isDirectory()) {
                        reportCannotWriteToDirectory(propertyName, context, candidate, "'" + directory + "' ancestor '" + candidate + "' is not a directory");
                        return;
                    }
                }
            }
        }
    };

    public static ValidationAction outputValidationActionFor(OutputFilePropertySpec spec) {
        if (spec instanceof DirectoryTreeOutputFilePropertySpec) {
            return OUTPUT_FILE_TREE_VALIDATOR;
        }
        switch (spec.getOutputType()) {
            case FILE:
                return OUTPUT_FILE_VALIDATOR;
            case DIRECTORY:
                return OUTPUT_DIRECTORY_VALIDATOR;
            default:
                throw new AssertionError("Unknown tree type " + spec);
        }
    }

    private static void reportMissingInput(TaskValidationContext context, String kind, String propertyName, File input) {
        context.visitPropertyProblem(problem -> {
            String lowerKind = kind.toLowerCase();
            problem.withId(ValidationProblemId.INPUT_FILE_DOES_NOT_EXIST)
                .forProperty(propertyName)
                .reportAs(ERROR)
                .withDescription(() -> "specifies " + lowerKind + " '" + input + "' which doesn't exist")
                .happensBecause("An input file was expected to be present but it doesn't exist")
                .addPossibleSolution(() -> "Make sure the " + lowerKind + " exists before the task is called")
                .addPossibleSolution(() -> "Make sure that the task which produces the " + lowerKind + " is declared as an input")
                .documentedAt("validation_problems", "input_file_does_not_exist");
        });
    }

    private static void reportUnexpectedInputKind(TaskValidationContext context, String kind, String propertyName, File input) {
        context.visitPropertyProblem(problem -> {
            String lowerKind = kind.toLowerCase();
            problem.withId(ValidationProblemId.UNEXPECTED_INPUT_FILE_TYPE)
                .forProperty(propertyName)
                .reportAs(ERROR)
                .withDescription(() -> lowerKind + " '" + input + "' is not a " + lowerKind)
                .happensBecause(() -> "Expected an input to be a " + lowerKind + " but it was a " + actualKindOf(input))
                .addPossibleSolution(() -> "Use a " + lowerKind + " as an input")
                .addPossibleSolution(() -> "Declare the input as a " + actualKindOf(input) + " instead")
                .documentedAt("validation_problems", "unexpected_input_file_type");
        });
    }

    private static void reportCannotWriteToDirectory(String propertyName, TaskValidationContext context, File directory, String cause) {
        context.visitPropertyProblem(problem ->
            problem.withId(ValidationProblemId.CANNOT_WRITE_OUTPUT)
                .reportAs(ERROR)
                .forProperty(propertyName)
                .withDescription(() -> "is not writable because " + cause)
                .happensBecause(() -> "Expected '" + directory + "' to be a directory but it's a " + actualKindOf(directory))
                .addPossibleSolution("Make sure that the '" + propertyName + "' is configured to a directory")
                .documentedAt("validation_problems", "cannot_write_output")
        );
    }

    private static void reportFileTreeWithFileRoot(String propertyName, TaskValidationContext context, File directory) {
        context.visitPropertyProblem(problem ->
            problem.withId(ValidationProblemId.CANNOT_WRITE_OUTPUT)
                .reportAs(ERROR)
                .forProperty(propertyName)
                .withDescription(() -> "is not writable because '" + directory + "' is not a directory")
                .happensBecause(() -> "Expected the root of the file tree '" + directory + "' to be a directory but it's a " + actualKindOf(directory))
                .addPossibleSolution("Make sure that the root of the file tree '" + propertyName + "' is configured to a directory")
                .documentedAt("validation_problems", "cannot_write_output")
        );
    }

    private static void reportCannotWriteFileToDirectory(String propertyName, TaskValidationContext context, File file) {
        context.visitPropertyProblem(problem -> {
                PropertyProblemBuilder problemBuilder = problem.withId(ValidationProblemId.CANNOT_WRITE_OUTPUT)
                    .reportAs(ERROR)
                    .forProperty(propertyName)
                    .withDescription(() -> "is not writable because '" + file + "' is not a file")
                    .happensBecause(() -> "Cannot write a file to a location pointing at a directory")
                    .addPossibleSolution(() -> "Configure '" + propertyName + "' to point to a file, not a directory")
                    .addPossibleSolution(() -> "Annotate '" + propertyName + "' with @OutputDirectory instead of @OutputFiles")
                    .documentedAt("validation_problems", "cannot_write_output");
            }
        );
    }

    private static void reportCannotCreateParentDirectories(String propertyName, TaskValidationContext context, File file, File ancestor) {
        context.visitPropertyProblem(problem -> {
                PropertyProblemBuilder problemBuilder = problem.withId(ValidationProblemId.CANNOT_WRITE_OUTPUT)
                    .reportAs(ERROR)
                    .forProperty(propertyName)
                    .withDescription(() -> "is not writable because '" + file + "' ancestor '" + ancestor + "' is not a directory")
                    .happensBecause(() -> "Cannot create parent directories that are existing as file")
                    .addPossibleSolution(() -> "Configure '" + propertyName + "' to point to the correct location")
                    .documentedAt("validation_problems", "cannot_write_output");
            }
        );
    }

    private static String actualKindOf(File input) {
        if (input.isFile()) {
            return "file";
        }
        if (input.isDirectory()) {
            return "directory";
        }
        return "unexpected file type";
    }

    private static void validateNotInReservedFileSystemLocation(String propertyName, TaskValidationContext context, File location) {
        if (context.isInReservedFileSystemLocation(location)) {
            context.visitPropertyProblem(problem ->
                problem.withId(ValidationProblemId.CANNOT_WRITE_TO_RESERVED_LOCATION)
                    .forProperty(propertyName)
                    .reportAs(ERROR)
                    .withDescription(() -> "points to '" + location + "' which is managed by Gradle")
                    .happensBecause("Trying to write an output to a read-only location which is for Gradle internal use only")
                    .addPossibleSolution("Select a different output location")
                    .documentedAt("validation_problems", "cannot_write_to_reserved_location")
            );
        }
    }

    private final String targetType;

    ValidationActions(String targetType) {
        this.targetType = targetType;
    }

    protected abstract void doValidate(String propertyName, Object value, TaskValidationContext context);

    @Override
    public void validate(String propertyName, Object value, TaskValidationContext context) {
        try {
            doValidate(propertyName, value, context);
        } catch (UnsupportedNotationException unsupportedNotationException) {
            context.visitPropertyProblem(problem -> {
                    problem.withId(ValidationProblemId.UNSUPPORTED_NOTATION)
                        .forProperty(propertyName)
                        .reportAs(ERROR)
                        .withDescription(() -> "has unsupported value '" + value + "'")
                        .happensBecause(() -> "Type '" + typeOf(value) + "' cannot be converted to a " + targetType);
                    Collection<String> candidates = unsupportedNotationException.getCandidates();
                    if (candidates.isEmpty()) {
                        problem.addPossibleSolution(() -> "Use a value of type '" + targetType + "'");
                    } else {
                        candidates.forEach(candidate -> problem.addPossibleSolution(() -> toCandidateSolution(candidate)));
                    }
                    problem.documentedAt("validation_problems", "unsupported_notation");
                }
            );
        }
    }

    private static String typeOf(@Nullable Object instance) {
        if (instance == null) {
            return Object.class.getSimpleName();
        }
        if (instance instanceof GeneratedSubclass) {
            return ModelType.of(((GeneratedSubclass) instance).publicType()).getDisplayName();
        }
        return ModelType.typeOf(instance).getDisplayName();
    }

    private static String toCandidateSolution(String conversionCandidate) {
        String result = StringUtils.uncapitalize(conversionCandidate);
        if (result.endsWith(".")) {
            result = result.substring(0, result.lastIndexOf("."));
        }
        return "Use " + result;
    }

    private static File toDirectory(TaskValidationContext context, Object value) {
        if (value instanceof ConfigurableFileTree) {
            return ((ConfigurableFileTree) value).getDir();
        }
        return toFile(context, value);
    }

    private static File toFile(TaskValidationContext context, Object value) {
        return context.getFileOperations().file(value);
    }
}
