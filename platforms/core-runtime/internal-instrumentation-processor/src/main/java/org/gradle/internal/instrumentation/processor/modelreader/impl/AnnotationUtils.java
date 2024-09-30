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

package org.gradle.internal.instrumentation.processor.modelreader.impl;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.lang.annotation.Annotation;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class AnnotationUtils {
    public static Optional<? extends AnnotationMirror> findMetaAnnotationMirror(Element element, Class<? extends Annotation> annotationClass) {
        return collectMetaAnnotations(element).stream().filter(it -> isAnnotationOfType(it, annotationClass)).findFirst();
    }

    private static Set<AnnotationMirror> collectMetaAnnotations(Element annotatedElement) {
        Set<AnnotationMirror> result = new LinkedHashSet<>();

        class Recurse {
            void recurse(Element annotatedElement) {
                annotatedElement.getAnnotationMirrors().forEach(annotationMirror -> {
                    Element element = annotationMirror.getAnnotationType().asElement();
                    if (element instanceof TypeElement && result.add(annotationMirror)) {
                        recurse(element);
                    }
                });
            }
        }

        new Recurse().recurse(annotatedElement);
        return result;
    }

    public static Optional<? extends AnnotationMirror> findAnnotationMirror(Element element, Class<? extends Annotation> annotationClass) {
        return element.getAnnotationMirrors().stream().filter(it -> isAnnotationOfType(it, annotationClass)).findFirst();
    }

    public static Optional<? extends AnnotationValue> findAnnotationValue(AnnotationMirror annotation, String key) {
        return annotation.getElementValues().entrySet().stream().filter(it -> it.getKey().getSimpleName().toString().equals(key)).map(Map.Entry::getValue).findFirst();
    }

    public static Optional<? extends AnnotationValue> findAnnotationValueWithDefaults(Elements elements, AnnotationMirror annotation, String key) {
        return elements.getElementValuesWithDefaults(annotation).entrySet().stream().filter(it -> it.getKey().getSimpleName().toString().equals(key)).map(Map.Entry::getValue).findFirst();
    }

    public static boolean isAnnotationOfType(AnnotationMirror annotation, Class<? extends Annotation> type) {
        return annotation.getAnnotationType().toString().equals(type.getCanonicalName());
    }
}
