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

import org.gradle.api.internal.tasks.properties.FileParameterUtils;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.InputNormalizer;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.fingerprint.Normalizer;
import org.gradle.internal.properties.BeanPropertyContext;
import org.gradle.internal.properties.InputBehavior;
import org.gradle.internal.properties.InputFilePropertyType;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.properties.annotations.PropertyAnnotationHandler;
import org.gradle.internal.reflect.PropertyMetadata;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.Severity;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.work.Incremental;
import org.gradle.work.NormalizeLineEndings;

import java.lang.annotation.Annotation;

import static org.gradle.api.internal.tasks.properties.annotations.PropertyAnnotationHandlerSupport.validateUnsupportedPropertyValueTypes;
import static org.gradle.internal.properties.ModifierAnnotationCategory.NORMALIZATION;

public abstract class AbstractInputFilePropertyAnnotationHandler implements PropertyAnnotationHandler {
    @Override
    public Kind getKind() {
        return Kind.INPUT;
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
        Annotation fileNormalization = propertyMetadata.getAnnotationForCategory(NORMALIZATION);
        Normalizer normalizer;
        if (fileNormalization == null) {
            normalizer = null;
        } else if (fileNormalization instanceof PathSensitive) {
            PathSensitivity pathSensitivity = ((PathSensitive) fileNormalization).value();
            normalizer = FileParameterUtils.determineNormalizerForPathSensitivity(pathSensitivity);
        } else if (fileNormalization instanceof Classpath) {
            normalizer = InputNormalizer.RUNTIME_CLASSPATH;
        } else if (fileNormalization instanceof CompileClasspath) {
            normalizer = InputNormalizer.COMPILE_CLASSPATH;
        } else {
            throw new IllegalStateException("Unknown normalization annotation used: " + fileNormalization);
        }
        visitor.visitInputFileProperty(
            propertyName,
            propertyMetadata.isAnnotationPresent(Optional.class),
            determineBehavior(propertyMetadata),
            determineDirectorySensitivity(propertyMetadata),
            determineLineEndingSensitivity(propertyMetadata),
            normalizer,
            value,
            getFilePropertyType()
        );
    }

    private static InputBehavior determineBehavior(PropertyMetadata propertyMetadata) {
        return propertyMetadata.isAnnotationPresent(SkipWhenEmpty.class)
            ? InputBehavior.PRIMARY
            : propertyMetadata.isAnnotationPresent(Incremental.class)
            ? InputBehavior.INCREMENTAL
            : InputBehavior.NON_INCREMENTAL;
    }

    private static LineEndingSensitivity determineLineEndingSensitivity(PropertyMetadata propertyMetadata) {
        return propertyMetadata.isAnnotationPresent(NormalizeLineEndings.class)
            ? LineEndingSensitivity.NORMALIZE_LINE_ENDINGS
            : LineEndingSensitivity.DEFAULT;
    }

    protected DirectorySensitivity determineDirectorySensitivity(PropertyMetadata propertyMetadata) {
        return propertyMetadata.isAnnotationPresent(IgnoreEmptyDirectories.class)
            ? DirectorySensitivity.IGNORE_DIRECTORIES
            : DirectorySensitivity.DEFAULT;
    }

    @Override
    public void validatePropertyMetadata(PropertyMetadata propertyMetadata, TypeValidationContext validationContext) {
        validateUnsupportedPropertyValueTypes(propertyMetadata, validationContext, getAnnotationType());
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
}
