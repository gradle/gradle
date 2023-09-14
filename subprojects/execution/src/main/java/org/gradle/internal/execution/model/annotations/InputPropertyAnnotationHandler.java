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
package org.gradle.internal.execution.model.annotations;

import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.problems.Severity;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.model.internal.type.ModelType;

import java.io.File;
import java.net.URL;
import java.util.List;

import static org.gradle.api.problems.Severity.WARNING;
import static org.gradle.internal.deprecation.Documentation.userManual;
import static org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory.OPTIONAL;

public class InputPropertyAnnotationHandler extends AbstractInputPropertyAnnotationHandler {
    public InputPropertyAnnotationHandler() {
        super(Input.class, ModifierAnnotationCategory.annotationsOf(OPTIONAL));
    }

    @Override
    public boolean isPropertyRelevant() {
        return true;
    }

    @Override
    public void visitPropertyValue(String propertyName, PropertyValue value, PropertyMetadata propertyMetadata, PropertyVisitor visitor) {
        visitor.visitInputProperty(propertyName, value, propertyMetadata.isAnnotationPresent(Optional.class));
    }

    @Override
    public void validatePropertyMetadata(PropertyMetadata propertyMetadata, TypeValidationContext validationContext) {
        validateUnsupportedInputPropertyValueTypes(propertyMetadata, validationContext, getAnnotationType());
        Class<?> valueType = propertyMetadata.getDeclaredType().getRawType();
        validateNotDirectoryType(propertyMetadata, validationContext, valueType);
        validateNotFileType(propertyMetadata, validationContext, valueType);
        validateNotOptionalPrimitiveType(propertyMetadata, validationContext, valueType);
        validateNotUrlType(propertyMetadata, validationContext);
    }

    private void validateNotOptionalPrimitiveType(PropertyMetadata propertyMetadata, TypeValidationContext validationContext, Class<?> valueType) {
        if (valueType.isPrimitive() && propertyMetadata.isAnnotationPresent(Optional.class)) {
            validationContext.visitPropertyProblem(problem ->
                problem
                    .forProperty(propertyMetadata.getPropertyName())
                    .label("of type %s shouldn't be annotated with @Optional", valueType.getName())
                    .documentedAt(userManual("validation_problems", "cannot_use_optional_on_primitive_types"))
                    .noLocation()
                    .type(ValidationProblemId.CANNOT_USE_OPTIONAL_ON_PRIMITIVE_TYPE.name())
                    .details("Properties of primitive type cannot be optional")
                    .severity(Severity.ERROR)
                    .solution("Remove the @Optional annotation")
                    .solution("Use the " + JavaReflectionUtil.getWrapperTypeForPrimitiveType(valueType).getName() + " type instead")
            );
        }
    }

    private void validateNotFileType(PropertyMetadata propertyMetadata, TypeValidationContext validationContext, Class<?> valueType) {
        if (File.class.isAssignableFrom(valueType)
            || RegularFile.class.isAssignableFrom(valueType)
            || RegularFileProperty.class.isAssignableFrom(valueType)
            || java.nio.file.Path.class.isAssignableFrom(valueType)
            || FileCollection.class.isAssignableFrom(valueType)) {
            validationContext.visitPropertyProblem(problem ->
                problem
                    .forProperty(propertyMetadata.getPropertyName())
                    .label("has @Input annotation used on property of type '%s'", ModelType.of(valueType).getDisplayName())
                    .documentedAt(userManual("validation_problems", "incorrect_use_of_input_annotation"))
                    .noLocation()
                    .type(ValidationProblemId.INCORRECT_USE_OF_INPUT_ANNOTATION.name())
                    .severity(Severity.ERROR)
                    .details("A property of type '" + ModelType.of(valueType).getDisplayName() + "' annotated with @Input cannot determine how to interpret the file")
                    .solution("Annotate with @InputFile for regular files")
                    .solution("Annotate with @InputFiles for collections of files")
                    .solution("If you want to track the path, return File.absolutePath as a String and keep @Input")
            );
        }
    }

    private void validateNotDirectoryType(PropertyMetadata propertyMetadata, TypeValidationContext validationContext, Class<?> valueType) {
        if (Directory.class.isAssignableFrom(valueType)
            || DirectoryProperty.class.isAssignableFrom(valueType)) {
            validationContext.visitPropertyProblem(problem ->
                problem
                    .forProperty(propertyMetadata.getPropertyName())
                    .label("has @Input annotation used on property of type '%s'", ModelType.of(valueType).getDisplayName())
                    .documentedAt(userManual("validation_problems", "incorrect_use_of_input_annotation"))
                    .noLocation()
                    .type(ValidationProblemId.INCORRECT_USE_OF_INPUT_ANNOTATION.name())
                    .severity(Severity.ERROR)
                    .details("A property of type '" + ModelType.of(valueType).getDisplayName() + "' annotated with @Input cannot determine how to interpret the file")
                    .solution("Annotate with @InputDirectory for directories")
            );
        }
    }

    private static void validateNotUrlType(PropertyMetadata propertyMetadata, TypeValidationContext validationContext) {
        List<Class<?>> valueTypes = unpackValueTypesOf(propertyMetadata);
        if (valueTypes.stream().anyMatch(URL.class::isAssignableFrom)) {
            validationContext.visitPropertyProblem(problem ->
                problem
                    .forProperty(propertyMetadata.getPropertyName())
                    .label("has @Input annotation used on type '%s' or a property of this type", URL.class.getName())
                    .documentedAt(userManual("validation_problems", "unsupported_value_type"))
                    .noLocation()
                    .type(ValidationProblemId.UNSUPPORTED_VALUE_TYPE.name())
                    .severity(WARNING)
                    .details(String.format("Type '%s' is not supported on properties annotated with @Input because Java Serialization can be inconsistent for this type", URL.class.getName()))
                    .solution("Use type 'java.net.URI' instead")
            );
        }
    }
}
