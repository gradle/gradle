/*
 * Copyright 2019 the original author or authors.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.internal.reflect.AnnotationCategory;
import org.gradle.work.Incremental;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Map;

public enum ModifierAnnotationCategory implements AnnotationCategory {
    INCREMENTAL(ImmutableSet.of(
        Incremental.class,
        SkipWhenEmpty.class
    )),
    NORMALIZATION(ImmutableSet.of(
        Classpath.class,
        CompileClasspath.class,
        PathSensitive.class
    )),
    OPTIONAL(ImmutableSet.of(
        Optional.class
    ));

    private final ImmutableSet<Class<? extends Annotation>> annotations;

    ModifierAnnotationCategory(ImmutableSet<Class<? extends Annotation>> annotations) {
        this.annotations = annotations;
    }

    @Override
    public String getDisplayName() {
        return name().toLowerCase();
    }

    public static Map<Class<? extends Annotation>, AnnotationCategory> asMap(Collection<Class<? extends Annotation>> typeAnnotations) {
        ImmutableMap.Builder<Class<? extends Annotation>, AnnotationCategory> builder = ImmutableMap.builder();
        for (Class<? extends Annotation> typeAnnotation : typeAnnotations) {
            builder.put(typeAnnotation, TYPE);
        }
        for (ModifierAnnotationCategory category : values()) {
            for (Class<? extends Annotation> annotation : category.annotations) {
                builder.put(annotation, category);
            }
        }
        return builder.build();
    }
}
