/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.tools.api;

import org.gradle.internal.tools.api.impl.AnnotationMember;
import org.gradle.internal.tools.api.impl.AnnotationValue;
import org.gradle.internal.tools.api.impl.ClassMember;
import org.gradle.internal.tools.api.impl.FieldMember;
import org.gradle.internal.tools.api.impl.InnerClassMember;
import org.gradle.internal.tools.api.impl.MethodMember;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.ModuleVisitor;

import java.util.Set;

/**
 * A writer for class API members. API members are delegated to an instance of this class.
 *
 * Implementation should determine how to further process those members (e.g. stripping out method bodies),
 * and how to write a new "API class" with them.
 */
public interface ApiMemberWriter {
    ModuleVisitor writeModule(String name, int access, String version);

    void writeClass(ClassMember classMember, Set<MethodMember> methods, Set<FieldMember> fields, Set<InnerClassMember> innerClasses);

    void writeMethod(/* Nullable */ InnerClassMember declaringInnerClass, MethodMember method);

    void writeClassAnnotations(Set<AnnotationMember> annotationMembers);

    void writeMethodAnnotations(MethodVisitor mv, Set<AnnotationMember> annotationMembers);

    void writeFieldAnnotations(FieldVisitor fv, Set<AnnotationMember> annotationMembers);

    void writeAnnotationValues(AnnotationMember annotation, AnnotationVisitor annotationVisitor);

    void writeAnnotationValue(AnnotationVisitor annotationVisitor, AnnotationValue<?> value);
}
