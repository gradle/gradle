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

import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.tasks.properties.BeanPropertyContext;
import org.gradle.api.internal.tasks.properties.FileParameterUtils;
import org.gradle.api.internal.tasks.properties.InputFilePropertyType;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.ClasspathNormalizer;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.CompileClasspathNormalizer;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.internal.execution.fingerprint.InputFingerprintingException;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.reflect.PropertyMetadata;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.Severity;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.work.Incremental;
import org.gradle.work.NormalizeLineEndings;

import java.lang.annotation.Annotation;

import static org.gradle.api.internal.tasks.properties.ModifierAnnotationCategory.NORMALIZATION;
import static org.gradle.api.internal.tasks.properties.annotations.PropertyAnnotationHandlerSupport.validateUnsupportedPropertyValueTypes;

public abstract class AbstractInputFilePropertyAnnotationHandler implements PropertyAnnotationHandler {
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
        Annotation fileNormalization = propertyMetadata.getAnnotationForCategory(NORMALIZATION);
        Class<? extends FileNormalizer> fileNormalizer;
        if (fileNormalization == null) {
            fileNormalizer = null;
        } else if (fileNormalization instanceof PathSensitive) {
            PathSensitivity pathSensitivity = ((PathSensitive) fileNormalization).value();
            fileNormalizer = FileParameterUtils.determineNormalizerForPathSensitivity(pathSensitivity);
        } else if (fileNormalization instanceof Classpath) {
            fileNormalizer = ClasspathNormalizer.class;
        } else if (fileNormalization instanceof CompileClasspath) {
            fileNormalizer = CompileClasspathNormalizer.class;
        } else {
            throw new IllegalStateException("Unknown normalization annotation used: " + fileNormalization);
        }
        visitor.visitInputFileProperty(
            propertyName,
            propertyMetadata.isAnnotationPresent(Optional.class),
            propertyMetadata.isAnnotationPresent(SkipWhenEmpty.class),
            determineDirectorySensitivity(propertyMetadata),
            propertyMetadata.isAnnotationPresent(NormalizeLineEndings.class) ? LineEndingSensitivity.NORMALIZE_LINE_ENDINGS : LineEndingSensitivity.DEFAULT,
            propertyMetadata.isAnnotationPresent(Incremental.class),
            fileNormalizer,
            value,
            getFilePropertyType()
        );
    }

    @SuppressWarnings("deprecation")
    protected DirectorySensitivity determineDirectorySensitivity(PropertyMetadata propertyMetadata) {
        return propertyMetadata.isAnnotationPresent(IgnoreEmptyDirectories.class)
            ? DirectorySensitivity.IGNORE_DIRECTORIES
            : DirectorySensitivity.UNSPECIFIED;
    }

    @Override
    public void validatePropertyMetadata(PropertyMetadata propertyMetadata, TypeValidationContext validationContext) {
        validateUnsupportedPropertyValueTypes(propertyMetadata, validationContext, getAnnotationType());
        Class<?> valueType = propertyMetadata.getGetterMethod().getReturnType();
        checkForTypesNeedingInput(propertyMetadata.getPropertyName(), valueType);
        if (!propertyMetadata.hasAnnotationForCategory(NORMALIZATION)) {
            validationContext.visitPropertyProblem(problem -> {
                String propertyName = propertyMetadata.getPropertyName();
                problem.withId(ValidationProblemId.MISSING_NORMALIZATION_ANNOTATION)
                    .reportAs(Severity.ERROR)
                    .onlyAffectsCacheableWork()
                    .forProperty(propertyName)
                    .withDescription(() -> String.format("is annotated with @%s but missing a normalization strategy", getAnnotationType().getSimpleName()))
                    .happensBecause("If you don't declare the normalization, outputs can't be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly")
                    .addPossibleSolution("Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath")
                    .documentedAt("validation_problems", "missing_normalization_annotation");
            });
        }
    }

    protected abstract InputFilePropertyType getFilePropertyType();

    private static void checkForTypesNeedingInput(String propertyName, Class<?> valueType) {
        if (RegularFileProperty.class.isAssignableFrom(valueType) || DirectoryProperty.class.isAssignableFrom(valueType)) {
            String msg = "value '%s' cannot be serialized.  This property might have to use @InputFile, or a related file-based input annotation, instead of @Input";
            throw new InputFingerprintingException(propertyName, String.format(msg, propertyName));
        }
    }
}
