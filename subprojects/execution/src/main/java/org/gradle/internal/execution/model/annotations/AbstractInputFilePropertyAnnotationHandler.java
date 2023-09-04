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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.Severity;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.internal.execution.model.InputNormalizer;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.FileNormalizer;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.properties.InputBehavior;
import org.gradle.internal.properties.InputFilePropertyType;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.work.Incremental;
import org.gradle.work.NormalizeLineEndings;

import java.lang.annotation.Annotation;

import static org.gradle.internal.deprecation.Documentation.userManual;
import static org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory.NORMALIZATION;

public abstract class AbstractInputFilePropertyAnnotationHandler extends AbstractInputPropertyAnnotationHandler {

    private final InputFilePropertyType filePropertyType;

    public AbstractInputFilePropertyAnnotationHandler(Class<? extends Annotation> annotationType, InputFilePropertyType filePropertyType, ImmutableSet<Class<? extends Annotation>> allowedModifiers) {
        super(annotationType, allowedModifiers);
        this.filePropertyType = filePropertyType;
    }

    @Override
    public boolean isPropertyRelevant() {
        return true;
    }

    @Override
    public void visitPropertyValue(String propertyName, PropertyValue value, PropertyMetadata propertyMetadata, PropertyVisitor visitor) {
        FileNormalizer normalizer = propertyMetadata.getAnnotationForCategory(NORMALIZATION)
            .map(fileNormalization -> {
                if (fileNormalization instanceof PathSensitive) {
                    PathSensitivity pathSensitivity = ((PathSensitive) fileNormalization).value();
                    return InputNormalizer.determineNormalizerForPathSensitivity(pathSensitivity);
                } else if (fileNormalization instanceof Classpath) {
                    return InputNormalizer.RUNTIME_CLASSPATH;
                } else if (fileNormalization instanceof CompileClasspath) {
                    return InputNormalizer.COMPILE_CLASSPATH;
                } else {
                    throw new IllegalStateException("Unknown normalization annotation used: " + fileNormalization);
                }
            })
            .orElse(null);
        visitor.visitInputFileProperty(
            propertyName,
            propertyMetadata.isAnnotationPresent(Optional.class),
            determineBehavior(propertyMetadata),
            determineDirectorySensitivity(propertyMetadata),
            determineLineEndingSensitivity(propertyMetadata),
            normalizer,
            value,
            filePropertyType
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
        validateUnsupportedInputPropertyValueTypes(propertyMetadata, validationContext, getAnnotationType());
        if (!propertyMetadata.hasAnnotationForCategory(NORMALIZATION)) {
            validationContext.visitPropertyProblem(problem -> {
                String propertyName = propertyMetadata.getPropertyName();
                problem
                    .forProperty(propertyName)
                    .label(String.format("is annotated with @%s but missing a normalization strategy", getAnnotationType().getSimpleName()))
                    .documentedAt(userManual("validation_problems", "missing_normalization_annotation"))
                    .noLocation()
                    .type(ValidationProblemId.MISSING_NORMALIZATION_ANNOTATION.name())
                    .group(ProblemGroup.TYPE_VALIDATION_ID)
                    .severity(Severity.ERROR)
                    .details("If you don't declare the normalization, outputs can't be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly")
                    .solution("Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath");
            });
        }
    }
}
