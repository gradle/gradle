/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.jvm.tasks.api.internal;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;
import java.util.SortedSet;

import static com.google.common.collect.Sets.newTreeSet;
import static org.objectweb.asm.Opcodes.*;

/**
 * Visits each {@link Member} of a given class and selects only those members that should
 * be included in the {@link org.gradle.jvm.tasks.api.ApiJar}. Selected API members are
 * delegated to an adapter that determines how to further process those members (e.g.
 * stripping out method bodies), and how to write a new "API class" with them.
 */
public class ApiMemberSelector extends ClassVisitor {

    private final SortedSet<MethodMember> methods = newTreeSet();
    private final SortedSet<FieldMember> fields = newTreeSet();
    private final SortedSet<InnerClassMember> innerClasses = newTreeSet();

    private final ClassVisitor apiMemberAdapter;
    private final boolean apiIncludesPackagePrivateMembers;

    private boolean isInnerClass;
    private ClassMember classMember;

    public ApiMemberSelector(ClassVisitor apiMemberAdapter, boolean apiIncludesPackagePrivateMembers) {
        super(ASM5);
        this.apiMemberAdapter = apiMemberAdapter;
        this.apiIncludesPackagePrivateMembers = apiIncludesPackagePrivateMembers;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        classMember = new ClassMember(version, access, name, signature, superName, interfaces);
        isInnerClass = (access & ACC_SUPER) == ACC_SUPER;
    }

    @Override
    public void visitEnd() {
        super.visitEnd();

        apiMemberAdapter.visit(
            classMember.getVersion(), classMember.getAccess(), classMember.getName(), classMember.getSignature(),
            classMember.getSuperName(), classMember.getInterfaces());
        visitAnnotationMembers(classMember.getAnnotations());
        for (MethodMember method : methods) {
            MethodVisitor mv = apiMemberAdapter.visitMethod(
                method.getAccess(), method.getName(), method.getTypeDesc(), method.getSignature(),
                method.getExceptions().toArray(new String[0]));
            visitAnnotationMembers(mv, method.getAnnotations());
            visitAnnotationMembers(mv, method.getParameterAnnotations());
            mv.visitEnd();
        }
        for (FieldMember field : fields) {
            FieldVisitor fieldVisitor = apiMemberAdapter.visitField(
                field.getAccess(), field.getName(), field.getTypeDesc(), field.getSignature(), null);
            visitAnnotationMembers(fieldVisitor, field.getAnnotations());
            fieldVisitor.visitEnd();
        }
        for (InnerClassMember innerClass : innerClasses) {
            apiMemberAdapter.visitInnerClass(
                innerClass.getName(), innerClass.getOuterName(), innerClass.getInnerName(), innerClass.getAccess());
        }
        apiMemberAdapter.visitEnd();
    }

    private void visitAnnotationMembers(Set<AnnotationMember> annotationMembers) {
        for (AnnotationMember annotation : annotationMembers) {
            AnnotationVisitor annotationVisitor =
                apiMemberAdapter.visitAnnotation(annotation.getName(), annotation.isVisible());
            visitAnnotationValues(annotation, annotationVisitor);
        }
    }

    private void visitAnnotationMembers(MethodVisitor mv, Set<AnnotationMember> annotationMembers) {
        for (AnnotationMember annotation : annotationMembers) {
            AnnotationVisitor annotationVisitor;
            if (annotation instanceof ParameterAnnotationMember) {
                annotationVisitor = mv.visitParameterAnnotation(
                    ((ParameterAnnotationMember) annotation).getParameter(), annotation.getName(),
                    annotation.isVisible());
            } else {
                annotationVisitor = mv.visitAnnotation(annotation.getName(), annotation.isVisible());
            }
            visitAnnotationValues(annotation, annotationVisitor);
        }
    }

    private void visitAnnotationMembers(FieldVisitor fv, Set<AnnotationMember> annotationMembers) {
        for (AnnotationMember annotation : annotationMembers) {
            AnnotationVisitor annotationVisitor = fv.visitAnnotation(annotation.getName(), annotation.isVisible());
            visitAnnotationValues(annotation, annotationVisitor);
        }
    }

    private void visitAnnotationValues(AnnotationMember annotation, AnnotationVisitor annotationVisitor) {
        for (AnnotationValue<?> value : annotation.getValues()) {
            visitAnnotationValue(annotationVisitor, value);
        }
        annotationVisitor.visitEnd();
    }

    private void visitAnnotationValue(AnnotationVisitor annotationVisitor, AnnotationValue<?> value) {
        String name = value.getName();
        if (value instanceof EnumAnnotationValue) {
            annotationVisitor.visitEnum(name, ((EnumAnnotationValue) value).getTypeDesc(), (String) value.getValue());
        } else if (value instanceof SimpleAnnotationValue) {
            annotationVisitor.visit(name, value.getValue());
        } else if (value instanceof ArrayAnnotationValue) {
            AnnotationVisitor arrayVisitor = annotationVisitor.visitArray(name);
            AnnotationValue<?>[] values = ((ArrayAnnotationValue) value).getValue();
            for (AnnotationValue<?> annotationValue : values) {
                visitAnnotationValue(arrayVisitor, annotationValue);
            }
            arrayVisitor.visitEnd();
        } else if (value instanceof AnnotationAnnotationValue) {
            AnnotationMember annotation = ((AnnotationAnnotationValue) value).getValue();
            AnnotationVisitor annVisitor = annotationVisitor.visitAnnotation(name, annotation.getName());
            visitAnnotationValues(annotation, annVisitor);
        }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        AnnotationMember ann = new AnnotationMember(desc, visible);
        classMember.addAnnotation(ann);
        return new SortingAnnotationVisitor(ann, super.visitAnnotation(desc, visible));
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if ("<clinit>".equals(name)) {
            // discard static initializers
            return null;
        }
        if (isCandidateApiMember(access, apiIncludesPackagePrivateMembers) || ("<init>".equals(name) && isInnerClass)) {
            final MethodMember methodMember = new MethodMember(access, name, desc, signature, exceptions);
            methods.add(methodMember);
            return new MethodVisitor(ASM5) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    AnnotationMember ann = new AnnotationMember(desc, visible);
                    methodMember.addAnnotation(ann);
                    return new SortingAnnotationVisitor(ann, super.visitAnnotation(desc, visible));
                }

                @Override
                public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
                    ParameterAnnotationMember ann = new ParameterAnnotationMember(desc, visible, parameter);
                    methodMember.addParameterAnnotation(ann);
                    return new SortingAnnotationVisitor(ann, super.visitParameterAnnotation(parameter, desc, visible));
                }
            };
        }
        return null;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        if (isCandidateApiMember(access, apiIncludesPackagePrivateMembers)) {
            final FieldMember fieldMember = new FieldMember(access, name, signature, desc);
            fields.add(fieldMember);
            return new FieldVisitor(ASM5) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    AnnotationMember ann = new AnnotationMember(desc, visible);
                    fieldMember.addAnnotation(ann);
                    return new SortingAnnotationVisitor(ann, super.visitAnnotation(desc, visible));
                }
            };
        }
        return null;
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        if (innerName == null) {
            return;
        }
        if (!apiIncludesPackagePrivateMembers && isPackagePrivateMember(access)) {
            return;
        }
        innerClasses.add(new InnerClassMember(access, name, outerName, innerName));
        super.visitInnerClass(name, outerName, innerName, access);
    }

    public static boolean isCandidateApiMember(int access, boolean apiIncludesPackagePrivateMembers) {
        return isPublicMember(access)
            || isProtectedMember(access)
            || (apiIncludesPackagePrivateMembers && isPackagePrivateMember(access));
    }

    private static boolean isPublicMember(int access) {
        return (access & ACC_PUBLIC) == ACC_PUBLIC;
    }

    private static boolean isProtectedMember(int access) {
        return (access & ACC_PROTECTED) == ACC_PROTECTED;
    }

    private static boolean isPackagePrivateMember(int access) {
        return access == 0
            || access == ACC_STATIC
            || access == ACC_SUPER
            || access == (ACC_SUPER | ACC_STATIC);
    }
}
