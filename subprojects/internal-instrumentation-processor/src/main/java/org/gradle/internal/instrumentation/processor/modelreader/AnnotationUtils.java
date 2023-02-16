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

package org.gradle.internal.instrumentation.processor.modelreader;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class AnnotationUtils {
    public static Set<TypeElement> collectMetaAnnotationTypes(Element annotatedElement) {
        class Recurse {
            void recurse(Set<TypeElement> result, Element annotatedElement) {
                annotatedElement.getAnnotationMirrors().forEach(annotationMirror -> {
                    Element element = annotationMirror.getAnnotationType().asElement();
                    if (element instanceof TypeElement && result.add((TypeElement) element)) {
                        recurse(result, element);
                    }
                });
            }
        }

        Set<TypeElement> result = new HashSet<>();
        new Recurse().recurse(result, annotatedElement);
        return result;
    }

    public static Optional<? extends AnnotationMirror> findAnnotationMirror(Element element, Class<?> annotationClass) {
        String annotationClassName = annotationClass.getCanonicalName();
        return element.getAnnotationMirrors().stream().filter(it -> it.getAnnotationType().toString().equals(annotationClassName)).findFirst();
    }

    public static Optional<? extends AnnotationValue> findAnnotationValue(AnnotationMirror annotation, String key) {
        return annotation.getElementValues().entrySet().stream().filter(it -> it.getKey().getSimpleName().toString().equals(key)).map(Map.Entry::getValue).findFirst();
    }
}
