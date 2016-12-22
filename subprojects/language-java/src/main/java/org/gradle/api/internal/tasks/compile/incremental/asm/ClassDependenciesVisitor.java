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

import com.google.common.collect.Sets;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.InstructionAdapter;

import java.util.Set;

public class ClassDependenciesVisitor extends ClassVisitor {

    private final static int API = Opcodes.ASM5;
    private final Set<Integer> constants;
    private final Set<Integer> literals;
    private boolean dependentToAll;

    public ClassDependenciesVisitor() {
        this(Sets.<Integer>newHashSet(), Sets.<Integer>newHashSet());
    }

    public ClassDependenciesVisitor(Set<Integer> constantsCollector, Set<Integer> literalsCollector) {
        super(API);
        this.constants = constantsCollector;
        this.literals = literalsCollector;
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
        if (isConstant(access) && !isPrivate(access) && value!=null) {
            constants.add(value.hashCode()); //non-private const
        }
        return null;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        return new LiteralAdapter(new MethodVisitor(API, null) {
        });
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

        protected LiteralAdapter(MethodVisitor mv) {
            super(API, mv);
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
            if (cst != null && !(cst instanceof Class)) {
                literals.add(cst.hashCode());
            }
            super.visitLdcInsn(cst);
        }
    }
}
