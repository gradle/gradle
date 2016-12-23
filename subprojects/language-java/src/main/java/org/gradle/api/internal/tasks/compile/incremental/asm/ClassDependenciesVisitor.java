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

package org.gradle.api.internal.tasks.compile.incremental.asm;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.commons.InstructionAdapter;

import java.util.Set;

public class ClassDependenciesVisitor extends ClassVisitor {

    private final static int API = Opcodes.ASM5;
    private static final MethodVisitor EMPTY_VISITOR = new MethodVisitor(API, null) {
    };

    private final LiteralAdapter literalAdapter;
    private final AnnotationVisitor annotationVisitor;
    private final Set<Integer> constants;
    private final Set<Integer> literals;
    private boolean dependentToAll;

    public ClassDependenciesVisitor(Set<Integer> constantsCollector, Set<Integer> literalsCollector) {
        super(API);
        this.constants = constantsCollector;
        this.literals = literalsCollector;
        this.annotationVisitor = literals == null ? null : new LiteralRecordingAnnotationVisitor();
        this.literalAdapter = literals == null ? null : new LiteralAdapter();
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if (isAnnotationType(interfaces)) {
            dependentToAll = true;
        }
    }

    private boolean isAnnotationType(String[] interfaces) {
        return interfaces.length == 1 && interfaces[0].equals("java/lang/annotation/Annotation");
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        if (isConstant(access) && !isPrivate(access) && value!=null && constants != null) {
            constants.add(value.hashCode()); //non-private const
        }
        return null;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if (literals != null) {
            return literalAdapter;
        }
        return null;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        return annotationVisitor;
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        return annotationVisitor;
    }

    private static boolean isPrivate(int access) {
        return (access & Opcodes.ACC_PRIVATE) != 0;
    }

    private static boolean isConstant(int access) {
        return (access & Opcodes.ACC_FINAL) != 0 && (access & Opcodes.ACC_STATIC) != 0;
    }

    public boolean isDependentToAll() {
        return dependentToAll;
    }

    private class LiteralAdapter extends InstructionAdapter {

        protected LiteralAdapter() {
            super(API, EMPTY_VISITOR);
        }

        @Override
        public AnnotationVisitor visitAnnotationDefault() {
            return annotationVisitor;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            return annotationVisitor;
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            return annotationVisitor;
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
            return annotationVisitor;
        }

        @Override
        public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            return annotationVisitor;
        }

        @Override
        public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            return annotationVisitor;
        }

        @Override
        public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String desc, boolean visible) {
            return annotationVisitor;
        }

        @Override
        public void iconst(int cst) {
            literals.add(cst);
            super.iconst(cst);
        }

        @Override
        public void fconst(float cst) {
            literals.add(Float.valueOf(cst).hashCode());
            super.fconst(cst);
        }

        @Override
        public void dconst(double cst) {
            literals.add(Double.valueOf(cst).hashCode());
            super.dconst(cst);
        }

        @Override
        public void lconst(long cst) {
            literals.add(Long.valueOf(cst).hashCode());
            super.lconst(cst);
        }

        @Override
        public void visitLdcInsn(Object cst) {
            recordConstant(cst);
            super.visitLdcInsn(cst);
        }
    }

    protected void recordConstant(Object cst) {
        if (cst != null && !(cst instanceof Class)) {
            literals.add(cst.hashCode());
        }
    }

    private class LiteralRecordingAnnotationVisitor extends AnnotationVisitor {
        public LiteralRecordingAnnotationVisitor() {
            super(ClassDependenciesVisitor.API, null);
        }

        @Override
        public void visit(String name, Object value) {
            recordConstant(value);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String desc) {
            return this;
        }
    }
}
