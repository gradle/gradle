/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.tools.api.impl;

import com.google.common.collect.ImmutableSortedSet;
import org.jspecify.annotations.Nullable;

import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A single component of a record, as recorded in the {@code Record} attribute of a class file.
 *
 * <p>Record components keep their declaration order, since that order is part of the API: it
 * determines the parameter order of the canonical constructor and the binding order of a record
 * pattern.</p>
 */
public class RecordComponentMember extends Member {

    private final String typeDesc;
    @Nullable
    private final String signature;
    private final SortedSet<AnnotationMember> annotations = new TreeSet<>();
    private final SortedSet<AnnotationMember> typeAnnotations = new TreeSet<>();

    public RecordComponentMember(String name, String typeDesc, @Nullable String signature) {
        super(name);
        this.typeDesc = typeDesc;
        this.signature = signature;
    }

    public String getTypeDesc() {
        return typeDesc;
    }

    @Nullable
    public String getSignature() {
        return signature;
    }

    public SortedSet<AnnotationMember> getAnnotations() {
        return ImmutableSortedSet.copyOf(annotations);
    }

    public void addAnnotation(AnnotationMember annotationMember) {
        annotations.add(annotationMember);
    }

    public SortedSet<AnnotationMember> getTypeAnnotations() {
        return ImmutableSortedSet.copyOf(typeAnnotations);
    }

    public void addTypeAnnotation(TypeAnnotationMember typeAnnotationMember) {
        typeAnnotations.add(typeAnnotationMember);
    }
}
