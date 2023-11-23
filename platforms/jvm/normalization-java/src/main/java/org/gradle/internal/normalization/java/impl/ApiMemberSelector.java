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

package org.gradle.internal.normalization.java.impl;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;

import java.util.SortedSet;

import static com.google.common.collect.Sets.newTreeSet;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PROTECTED;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;

/**
 * Visits each {@link Member} of a given class and selects only those members that
 * are part of its API.
 * Selected members are delegated to an adapter that determines how to further
 * process those members (e.g. stripping out method bodies), and how to write a
 * new "API class" with them.
 */
public class ApiMemberSelector extends ClassVisitor {

    private final SortedSet<MethodMember> methods = newTreeSet();
    private final SortedSet<FieldMember> fields = newTreeSet();
    private final SortedSet<InnerClassMember> innerClasses = newTreeSet();

    private final String className;
    private final ApiMemberWriter apiMemberWriter;
    private final boolean apiIncludesPackagePrivateMembers;

    private boolean isInnerClass;
    private ClassMember classMember;
    private boolean thisClassIsPrivateInnerClass;

    public ApiMemberSelector(String className, ApiMemberWriter apiMemberWriter, boolean apiIncludesPackagePrivateMembers) {
        super(Opcodes.ASM9);
        this.className = className;
        this.apiMemberWriter = apiMemberWriter;
        this.apiIncludesPackagePrivateMembers = apiIncludesPackagePrivateMembers;
    }

    public boolean isPrivateInnerClass() {
        return thisClassIsPrivateInnerClass;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        classMember = new ClassMember(version, access, name, signature, superName, interfaces);
        isInnerClass = (access & ACC_SUPER) == ACC_SUPER;
    }

    @Override
    public ModuleVisitor visitModule(String name, int access, String version) {
        return apiMemberWriter.writeModule(name, access, version);
    }

    @Override
    public void visitEnd() {
        super.visitEnd();

        apiMemberWriter.writeClass(classMember, methods, fields, innerClasses);
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
            return new MethodVisitor(Opcodes.ASM9) {
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
            Object keepValue = (access & ACC_STATIC) == ACC_STATIC && ((access & ACC_FINAL) == ACC_FINAL) ? value : null;
            final FieldMember fieldMember = new FieldMember(access, name, signature, desc, keepValue);
            fields.add(fieldMember);
            return new FieldVisitor(Opcodes.ASM9) {
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
        boolean privateInnerClass = (access & ACC_PRIVATE) != 0;
        if (name.equals(className) && privateInnerClass) {
            thisClassIsPrivateInnerClass = true;
        }
        if (outerName == null || innerName == null || privateInnerClass) {
            // A local, anonymous class or a private inner class - ignore the reference
            return;
        }

        if (!apiIncludesPackagePrivateMembers && isPackagePrivateMember(access)) {
            return;
        }

        innerClasses.add(new InnerClassMember(access, name, outerName, innerName));
        super.visitInnerClass(name, outerName, innerName, access);
    }

    @Override
    public void visitPermittedSubclass(String permittedSubclass) {
        classMember.getPermittedSubclasses().add(permittedSubclass);
        super.visitPermittedSubclass(permittedSubclass);
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
        return (access & (ACC_PUBLIC | ACC_PROTECTED | ACC_PRIVATE)) == 0;
    }
}
