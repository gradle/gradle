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
import org.gradle.api.problems.ProblemSpec;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.internal.properties.InputFilePropertyType;
import org.gradle.internal.typeconversion.UnsupportedNotationException;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.internal.TextUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Locale;
import java.util.function.Supplier;

import static org.gradle.internal.deprecation.Documentation.userManual;

public enum ValidationActions implements ValidationAction {
    NO_OP("file collection") {
        @Override
        public void doValidate(String propertyName, Object value, PropertyValidationContext context) {
        }

        @Override
        public void validate(String propertyName, Supplier<Object> propertyValue, PropertyValidationContext context) {
        }
    },
    INPUT_FILE_VALIDATOR("file") {
        @Override
        public void doValidate(String propertyName, Object value, PropertyValidationContext context) {
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
        public void doValidate(String propertyName, Object value, PropertyValidationContext context) {
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
        public void doValidate(String propertyName, Object value, PropertyValidationContext context) {
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
        public void doValidate(String propertyName, Object value, PropertyValidationContext context) {
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
        public void doValidate(String propertyName, Object value, PropertyValidationContext context) {
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

    public static final String PROPERTY_IS_NOT_WRITABLE = "Property is not writable";

    public static ValidationAction inputValidationActionFor(InputFilePropertyType type) {
        switch (type) {
            case FILE:
                return INPUT_FILE_VALIDATOR;
            case DIRECTORY:
                return INPUT_DIRECTORY_VALIDATOR;
            case FILES:
                return NO_OP;
            default:
                throw new AssertionError("Unknown input property type " + type);
        }
    }

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

    private static final String INPUT_FILE_DOES_NOT_EXIST = "INPUT_FILE_DOES_NOT_EXIST";

    private static void reportMissingInput(PropertyValidationContext context, String kind, String propertyName, File input) {
        context.visitPropertyProblem(problem -> {
            String lowerKind = kind.toLowerCase(Locale.ROOT);
            problem
                .forProperty(propertyName)
                .id(TextUtil.screamingSnakeToKebabCase(INPUT_FILE_DOES_NOT_EXIST), "Input file does not exist", GradleCoreProblemGroup.validation().property())
                .contextualLabel("specifies " + lowerKind + " '" + input + "' which doesn't exist")
                .documentedAt(userManual("validation_problems", INPUT_FILE_DOES_NOT_EXIST.toLowerCase(Locale.ROOT)))
                .severity(Severity.ERROR)
                .details("An input file was expected to be present but it doesn't exist")
                .solution("Make sure the " + lowerKind + " exists before the task is called")
                .solution("Make sure that the task which produces the " + lowerKind + " is declared as an input");
        });
    }

    private static final String UNEXPECTED_INPUT_FILE_TYPE = "UNEXPECTED_INPUT_FILE_TYPE";

    private static void reportUnexpectedInputKind(PropertyValidationContext context, String kind, String propertyName, File input) {
        context.visitPropertyProblem(problem -> {
            String lowerKind = kind.toLowerCase(Locale.ROOT);
            problem
                .forProperty(propertyName)
                .id(TextUtil.screamingSnakeToKebabCase(UNEXPECTED_INPUT_FILE_TYPE), "Unexpected input file type", GradleCoreProblemGroup.validation().property())
                .contextualLabel(lowerKind + " '" + input + "' is not a " + lowerKind)
                .documentedAt(userManual("validation_problems", "unexpected_input_file_type"))
                .severity(Severity.ERROR)
                .details("Expected an input to be a " + lowerKind + " but it was a " + actualKindOf(input))
                .solution("Use a " + lowerKind + " as an input")
                .solution("Declare the input as a " + actualKindOf(input) + " instead");
        });
    }

    private static final String CANNOT_WRITE_OUTPUT = "CANNOT_WRITE_OUTPUT";

    private static void reportCannotWriteToDirectory(String propertyName, PropertyValidationContext context, File directory, String cause) {
        context.visitPropertyProblem(problem ->
            problem
                .forProperty(propertyName)
                .id(TextUtil.screamingSnakeToKebabCase(CANNOT_WRITE_OUTPUT), PROPERTY_IS_NOT_WRITABLE, GradleCoreProblemGroup.validation().property())
                .contextualLabel("is not writable because " + cause)
                .documentedAt(userManual("validation_problems", CANNOT_WRITE_OUTPUT.toLowerCase(Locale.ROOT)))
                .severity(Severity.ERROR)
                .details("Expected '" + directory + "' to be a directory but it's a " + actualKindOf(directory))
                .solution("Make sure that the '" + propertyName + "' is configured to a directory")
        );
    }

    private static void reportFileTreeWithFileRoot(String propertyName, PropertyValidationContext context, File directory) {
        context.visitPropertyProblem(problem ->
            problem
                .forProperty(propertyName)
                .id(TextUtil.screamingSnakeToKebabCase(CANNOT_WRITE_OUTPUT), PROPERTY_IS_NOT_WRITABLE, GradleCoreProblemGroup.validation().property())
                .contextualLabel("is not writable because '" + directory + "' is not a directory")
                .documentedAt(userManual("validation_problems", CANNOT_WRITE_OUTPUT.toLowerCase(Locale.ROOT)))
                .severity(Severity.ERROR)
                .details("Expected the root of the file tree '" + directory + "' to be a directory but it's a " + actualKindOf(directory))
                .solution("Make sure that the root of the file tree '" + propertyName + "' is configured to a directory")
        );
    }

    private static void reportCannotWriteFileToDirectory(String propertyName, PropertyValidationContext context, File file) {
        context.visitPropertyProblem(problem ->
            problem
                .forProperty(propertyName)
                .id(TextUtil.screamingSnakeToKebabCase(CANNOT_WRITE_OUTPUT), PROPERTY_IS_NOT_WRITABLE, GradleCoreProblemGroup.validation().property())
                .contextualLabel("is not writable because '" + file + "' is not a file")
                .documentedAt(userManual("validation_problems", CANNOT_WRITE_OUTPUT.toLowerCase(Locale.ROOT)))
                .details("Cannot write a file to a location pointing at a directory")
                .severity(Severity.ERROR)
                .solution("Configure '" + propertyName + "' to point to a file, not a directory")
                .solution("Annotate '" + propertyName + "' with @OutputDirectory instead of @OutputFiles")
        );
    }

    private static void reportCannotCreateParentDirectories(String propertyName, PropertyValidationContext context, File file, File ancestor) {
        context.visitPropertyProblem(problem ->
            problem
                .forProperty(propertyName)
                .id(TextUtil.screamingSnakeToKebabCase(CANNOT_WRITE_OUTPUT), PROPERTY_IS_NOT_WRITABLE, GradleCoreProblemGroup.validation().property()) // TODO (donat) missing test coverage
                .contextualLabel("is not writable because '" + file + "' ancestor '" + ancestor + "' is not a directory")
                .documentedAt(userManual("validation_problems", CANNOT_WRITE_OUTPUT.toLowerCase(Locale.ROOT)))
                .severity(Severity.ERROR)
                .details("Cannot create parent directories that are existing as file")
                .solution("Configure '" + propertyName + "' to point to the correct location")
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

    private static final String CANNOT_WRITE_TO_RESERVED_LOCATION = "CANNOT_WRITE_TO_RESERVED_LOCATION";

    private static void validateNotInReservedFileSystemLocation(String propertyName, PropertyValidationContext context, File location) {
        if (context.isInReservedFileSystemLocation(location)) {
            context.visitPropertyProblem(problem ->
                problem
                    .forProperty(propertyName)
                    .id(TextUtil.screamingSnakeToKebabCase(CANNOT_WRITE_TO_RESERVED_LOCATION), "Cannot write to reserved location", GradleCoreProblemGroup.validation().property())
                    .contextualLabel("points to '" + location + "' which is managed by Gradle")
                    .documentedAt(userManual("validation_problems", CANNOT_WRITE_TO_RESERVED_LOCATION.toLowerCase(Locale.ROOT)))
                    .severity(Severity.ERROR)
                    .details("Trying to write an output to a read-only location which is for Gradle internal use only")
                    .solution("Select a different output location")
            );
        }
    }

    private final String targetType;

    ValidationActions(String targetType) {
        this.targetType = targetType;
    }

    protected abstract void doValidate(String propertyName, Object value, PropertyValidationContext context);

    @Override
    public void validate(String propertyName, Supplier<Object> value, PropertyValidationContext context) {
        try {
            doValidate(propertyName, value.get(), context);
        } catch (UnsupportedNotationException unsupportedNotationException) {
            reportUnsupportedValue(propertyName, context, targetType, value.get(), unsupportedNotationException.getCandidates());
        }
    }

    private static final String UNSUPPORTED_NOTATION = "UNSUPPORTED_NOTATION";

    private static void reportUnsupportedValue(String propertyName, PropertyValidationContext context, String targetType, Object value, Collection<String> candidates) {
        context.visitPropertyProblem(problem -> {
                ProblemSpec describedProblem = problem
                    .forProperty(propertyName)
                    .id(TextUtil.screamingSnakeToKebabCase(UNSUPPORTED_NOTATION), "Property has unsupported value", GradleCoreProblemGroup.validation().property())
                    .contextualLabel("has unsupported value '" + value + "'")
                    .documentedAt(userManual("validation_problems", UNSUPPORTED_NOTATION.toLowerCase(Locale.ROOT)))
                    .severity(Severity.ERROR)
                    .details("Type '" + typeOf(value) + "' cannot be converted to a " + targetType);
                if (candidates.isEmpty()) {
                    describedProblem.solution("Use a value of type '" + targetType + "'");
                } else {
                    candidates.forEach(candidate -> describedProblem.solution(toCandidateSolution(candidate)));
                }
            }
        );
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

    private static File toDirectory(PropertyValidationContext context, Object value) {
        if (value instanceof ConfigurableFileTree) {
            return ((ConfigurableFileTree) value).getDir();
        }
        return toFile(context, value);
    }

    private static File toFile(PropertyValidationContext context, Object value) {
        return context.getFileResolver().resolve(value);
    }
}
