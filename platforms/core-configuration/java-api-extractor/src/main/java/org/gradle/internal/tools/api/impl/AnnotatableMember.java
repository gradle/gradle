/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSortedSet;

import java.util.SortedSet;
import java.util.TreeSet;

public abstract class AnnotatableMember extends AccessibleMember {

    private final SortedSet<AnnotationMember> annotations = new TreeSet<>();
    private final String signature;

    public AnnotatableMember(int access, String name, String signature) {
        super(access, name);
        this.signature = signature;
    }

    public SortedSet<AnnotationMember> getAnnotations() {
        return ImmutableSortedSet.copyOf(annotations);
    }

    public void addAnnotation(AnnotationMember annotationMember) {
        annotations.add(annotationMember);
    }

    public String getSignature() {
        return signature;
    }

    protected ComparisonChain compare(AnnotatableMember o) {
        return super.compare(o)
            .compare(signature == null ? "" : signature, o.signature == null ? "" : o.signature);
    }
}
