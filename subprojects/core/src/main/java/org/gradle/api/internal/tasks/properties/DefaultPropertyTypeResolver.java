/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory;
import org.gradle.internal.properties.annotations.PropertyTypeResolver;
import org.gradle.internal.reflect.annotations.AnnotationCategory;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.Map;

public class DefaultPropertyTypeResolver implements PropertyTypeResolver {
    @Nullable
    @Override
    public Class<? extends Annotation> resolveAnnotationType(Map<AnnotationCategory, Annotation> propertyAnnotations) {
        Annotation typeAnnotation = propertyAnnotations.get(AnnotationCategory.TYPE);
        if (typeAnnotation != null) {
            return typeAnnotation.annotationType();
        } else {
            Annotation normalizationAnnotation = propertyAnnotations.get(ModifierAnnotationCategory.NORMALIZATION);
            if (normalizationAnnotation != null) {
                Class<? extends Annotation> normalizationType = normalizationAnnotation.annotationType();
                if (normalizationType.equals(Classpath.class) || normalizationType.equals(CompileClasspath.class)) {
                    return InputFiles.class;
                }
            }
        }
        return null;
    }
}
