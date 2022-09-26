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
package org.gradle.api.internal.tasks.properties.annotations;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.tasks.properties.BeanPropertyContext;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.reflect.AnnotationCategory;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.reflect.PropertyMetadata;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.model.internal.type.ModelType;

import java.io.File;
import java.lang.annotation.Annotation;

import static org.gradle.api.internal.tasks.properties.ModifierAnnotationCategory.OPTIONAL;
import static org.gradle.api.internal.tasks.properties.annotations.PropertyAnnotationHandlerSupport.validateUnsupportedPropertyValueTypes;
import static org.gradle.internal.reflect.validation.Severity.ERROR;
import static org.gradle.internal.reflect.validation.Severity.WARNING;

public class InputPropertyAnnotationHandler implements PropertyAnnotationHandler {
    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Input.class;
    }

    @Override
    public ImmutableSet<? extends AnnotationCategory> getAllowedModifiers() {
        return ImmutableSet.of(OPTIONAL);
    }

    @Override
    public boolean isPropertyRelevant() {
        return true;
    }

    @Override
    public boolean shouldVisit(PropertyVisitor visitor) {
        return true;
    }

    @Override
    public void visitPropertyValue(String propertyName, PropertyValue value, PropertyMetadata propertyMetadata, PropertyVisitor visitor, BeanPropertyContext context) {
        visitor.visitInputProperty(propertyName, value, propertyMetadata.isAnnotationPresent(Optional.class));
    }

    @Override
    public void validatePropertyMetadata(PropertyMetadata propertyMetadata, TypeValidationContext validationContext) {
        validateUnsupportedPropertyValueTypes(propertyMetadata, validationContext, getAnnotationType());
        Class<?> valueType = propertyMetadata.getGetterMethod().getReturnType();
        validateNotDirectoryType(propertyMetadata, validationContext, valueType);
        validateNotFileTypeErrors(propertyMetadata, validationContext, valueType);
        validateNotFileTypeWarnings(propertyMetadata, validationContext, valueType);
        validateNotPrimitiveType(propertyMetadata, validationContext, valueType);
    }

    private void validateNotPrimitiveType(PropertyMetadata propertyMetadata, TypeValidationContext validationContext, Class<?> valueType) {
        if (valueType.isPrimitive() && propertyMetadata.isAnnotationPresent(Optional.class)) {
            validationContext.visitPropertyProblem(problem ->
                problem.withId(ValidationProblemId.CANNOT_USE_OPTIONAL_ON_PRIMITIVE_TYPE)
                    .reportAs(ERROR)
                    .forProperty(propertyMetadata.getPropertyName())
                    .withDescription(() -> "of type " + valueType.getName() + " shouldn't be annotated with @Optional")
                    .happensBecause("Properties of primitive type cannot be optional")
                    .addPossibleSolution("Remove the @Optional annotation")
                    .addPossibleSolution(() -> "Use the " + JavaReflectionUtil.getWrapperTypeForPrimitiveType(valueType).getName() + " type instead")
                    .documentedAt("validation_problems", "cannot_use_optional_on_primitive_types")
            );
        }
    }

    private void validateNotFileTypeErrors(PropertyMetadata propertyMetadata, TypeValidationContext validationContext, Class<?> valueType) {
        if (File.class.isAssignableFrom(valueType)
            || java.nio.file.Path.class.isAssignableFrom(valueType)
            || FileCollection.class.isAssignableFrom(valueType)) {
            validationContext.visitPropertyProblem(problem ->
                problem.withId(ValidationProblemId.INCORRECT_USE_OF_INPUT_ANNOTATION)
                        .forProperty(propertyMetadata.getPropertyName())
                        .reportAs(ERROR)
                        .withDescription(() -> String.format("has @Input annotation used on property of type '%s'", ModelType.of(valueType).getDisplayName()))
                        .happensBecause(() -> "A property of type '" + ModelType.of(valueType).getDisplayName() + "' annotated with @Input cannot determine how to interpret the file")
                        .addPossibleSolution("Annotate with @InputFile for regular files")
                        .addPossibleSolution("Annotate with @InputFiles for collections of files")
                        .addPossibleSolution("If you want to track the path, return File.absolutePath as a String and keep @Input")
                        .documentedAt("validation_problems", "incorrect_use_of_input_annotation")
            );
        }
    }

    /**
     * The problems checked here will become errors in Gradle 8.0.
     */
    @Deprecated
    @ReplacedBy("These checks will be consolidated into validateNotFileTypeErrors in Gradle 8.0.")
    private void validateNotFileTypeWarnings(PropertyMetadata propertyMetadata, TypeValidationContext validationContext, Class<?> valueType) {
        if (RegularFile.class.isAssignableFrom(valueType)
            || RegularFileProperty.class.isAssignableFrom(valueType)) {
            validationContext.visitPropertyProblem(problem ->
                    problem.withId(ValidationProblemId.INCORRECT_USE_OF_INPUT_ANNOTATION)
                            .forProperty(propertyMetadata.getPropertyName())
                            .reportAs(WARNING)
                            .withDescription(() -> String.format("has @Input annotation used on property of type '%s'", ModelType.of(valueType).getDisplayName()))
                            .happensBecause(() -> "A property of type '" + ModelType.of(valueType).getDisplayName() + "' annotated with @Input cannot determine how to interpret the file")
                            .addPossibleSolution("Annotate with @InputFile for regular files")
                            .addPossibleSolution("Annotate with @InputFiles for collections of files")
                            .addPossibleSolution("If you want to track the path, return File.absolutePath as a String and keep @Input")
                            .documentedAt("validation_problems", "incorrect_use_of_input_annotation")
            );
        }
    }

    private void validateNotDirectoryType(PropertyMetadata propertyMetadata, TypeValidationContext validationContext, Class<?> valueType) {
        if (Directory.class.isAssignableFrom(valueType)
            || DirectoryProperty.class.isAssignableFrom(valueType)) {
            validationContext.visitPropertyProblem(problem ->
                    problem.withId(ValidationProblemId.INCORRECT_USE_OF_INPUT_ANNOTATION)
                            .forProperty(propertyMetadata.getPropertyName())
                            .reportAs(WARNING)
                            .withDescription(() -> String.format("has @Input annotation used on property of type '%s'", ModelType.of(valueType).getDisplayName()))
                            .happensBecause(() -> "A property of type '" + ModelType.of(valueType).getDisplayName() + "' annotated with @Input cannot determine how to interpret the file")
                            .addPossibleSolution("Annotate with @InputDirectory for directories")
                            .documentedAt("validation_problems", "incorrect_use_of_input_annotation")
            );
        }
    }
}
