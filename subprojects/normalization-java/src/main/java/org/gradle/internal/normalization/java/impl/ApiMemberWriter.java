/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.normalization.java.impl;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.ModuleVisitor;

import java.util.Set;

public class ApiMemberWriter {

    private final ClassVisitor apiMemberAdapter;

    public ApiMemberWriter(ClassVisitor apiMemberAdapter) {
        this.apiMemberAdapter = apiMemberAdapter;
    }

    public ModuleVisitor writeModule(String name, int access, String version) {
        return apiMemberAdapter.visitModule(name, access, version);
    }

    public void writeClass(ClassMember classMember, Set<MethodMember> methods, Set<FieldMember> fields, Set<InnerClassMember> innerClasses) {
        apiMemberAdapter.visit(
            classMember.getVersion(), classMember.getAccess(), classMember.getName(), classMember.getSignature(),
            classMember.getSuperName(), classMember.getInterfaces());
        writeClassAnnotations(classMember.getAnnotations());
        for (MethodMember method : methods) {
            writeMethod(method);
        }
        for (FieldMember field : fields) {
            FieldVisitor fieldVisitor = apiMemberAdapter.visitField(
                field.getAccess(), field.getName(), field.getTypeDesc(), field.getSignature(), field.getValue());
            writeFieldAnnotations(fieldVisitor, field.getAnnotations());
            fieldVisitor.visitEnd();
        }
        for (InnerClassMember innerClass : innerClasses) {
            apiMemberAdapter.visitInnerClass(
                innerClass.getName(), innerClass.getOuterName(), innerClass.getInnerName(), innerClass.getAccess());
        }
        apiMemberAdapter.visitEnd();
    }

    protected void writeMethod(MethodMember method) {
        MethodVisitor mv = apiMemberAdapter.visitMethod(
            method.getAccess(), method.getName(), method.getTypeDesc(), method.getSignature(),
            method.getExceptions().toArray(new String[0]));
        writeMethodAnnotations(mv, method.getAnnotations());
        writeMethodAnnotations(mv, method.getParameterAnnotations());
        mv.visitEnd();
    }

    protected void writeClassAnnotations(Set<AnnotationMember> annotationMembers) {
        for (AnnotationMember annotation : annotationMembers) {
            AnnotationVisitor annotationVisitor =
                apiMemberAdapter.visitAnnotation(annotation.getName(), annotation.isVisible());
            writeAnnotationValues(annotation, annotationVisitor);
        }
    }

    private void writeMethodAnnotations(MethodVisitor mv, Set<AnnotationMember> annotationMembers) {
        for (AnnotationMember annotation : annotationMembers) {
            AnnotationVisitor annotationVisitor;
            if (annotation instanceof ParameterAnnotationMember) {
                annotationVisitor = mv.visitParameterAnnotation(
                    ((ParameterAnnotationMember) annotation).getParameter(), annotation.getName(),
                    annotation.isVisible());
            } else {
                annotationVisitor = mv.visitAnnotation(annotation.getName(), annotation.isVisible());
            }
            writeAnnotationValues(annotation, annotationVisitor);
        }
    }

    private void writeFieldAnnotations(FieldVisitor fv, Set<AnnotationMember> annotationMembers) {
        for (AnnotationMember annotation : annotationMembers) {
            AnnotationVisitor annotationVisitor = fv.visitAnnotation(annotation.getName(), annotation.isVisible());
            writeAnnotationValues(annotation, annotationVisitor);
        }
    }

    private void writeAnnotationValues(AnnotationMember annotation, AnnotationVisitor annotationVisitor) {
        for (AnnotationValue<?> value : annotation.getValues()) {
            writeAnnotationValue(annotationVisitor, value);
        }
        annotationVisitor.visitEnd();
    }

    private void writeAnnotationValue(AnnotationVisitor annotationVisitor, AnnotationValue<?> value) {
        String name = value.getName();
        if (value instanceof EnumAnnotationValue) {
            annotationVisitor.visitEnum(name, ((EnumAnnotationValue) value).getTypeDesc(), (String) value.getValue());
        } else if (value instanceof SimpleAnnotationValue) {
            annotationVisitor.visit(name, value.getValue());
        } else if (value instanceof ArrayAnnotationValue) {
            AnnotationVisitor arrayVisitor = annotationVisitor.visitArray(name);
            AnnotationValue<?>[] values = ((ArrayAnnotationValue) value).getValue();
            for (AnnotationValue<?> annotationValue : values) {
                writeAnnotationValue(arrayVisitor, annotationValue);
            }
            arrayVisitor.visitEnd();
        } else if (value instanceof AnnotationAnnotationValue) {
            AnnotationMember annotation = ((AnnotationAnnotationValue) value).getValue();
            AnnotationVisitor annVisitor = annotationVisitor.visitAnnotation(name, annotation.getName());
            writeAnnotationValues(annotation, annVisitor);
        }
    }
}
