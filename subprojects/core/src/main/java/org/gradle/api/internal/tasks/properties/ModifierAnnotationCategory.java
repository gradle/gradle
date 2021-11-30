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
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.internal.reflect.AnnotationCategory;
import org.gradle.work.Incremental;
import org.gradle.work.NormalizeLineEndings;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Map;

public enum ModifierAnnotationCategory implements AnnotationCategory {
    INCREMENTAL("incremental",
        Incremental.class,
        SkipWhenEmpty.class
    ),
    NORMALIZATION("normalization",
        Classpath.class,
        CompileClasspath.class,
        PathSensitive.class
    ),
    OPTIONAL("optional",
        Optional.class
    ),
    IGNORE_EMPTY_DIRECTORIES("ignore empty directories",
        IgnoreEmptyDirectories.class
    ),
    NORMALIZE_LINE_ENDINGS("ignore line endings",
        NormalizeLineEndings.class
    );

    private final String displayName;
    private final ImmutableSet<Class<? extends Annotation>> annotations;

    @SafeVarargs
    @SuppressWarnings("varargs")
    ModifierAnnotationCategory(String displayName, Class<? extends Annotation>... annotations) {
        this.displayName = displayName;
        this.annotations = ImmutableSet.copyOf(annotations);
    }

    @Override
    public String getDisplayName() {
        return displayName;
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
