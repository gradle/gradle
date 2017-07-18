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

import com.google.common.base.Predicate;
import com.google.common.collect.Sets;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.commons.InstructionAdapter;

import java.lang.annotation.RetentionPolicy;
import java.util.Set;

public class ClassDependenciesVisitor extends ClassVisitor {

    private final static int API = Opcodes.ASM6;
    private static final MethodVisitor EMPTY_VISITOR = new MethodVisitor(API, null) {
    };

    private final LiteralAdapter literalAdapter;
    private final AnnotationVisitor annotationVisitor;
    private final Set<Integer> constants;
    private final Set<Integer> literals;
    private final Set<String> superTypes;
    private final Set<String> types;
    private final Predicate<String> typeFilter;
    private boolean isAnnotationType;
    private boolean dependencyToAll;

    public ClassDependenciesVisitor(Set<Integer> constantsCollector) {
        this(constantsCollector, null, null, null, null);
    }

    private ClassDependenciesVisitor(Set<Integer> constantsCollector, Set<Integer> literalsCollector, Set<String> types, Predicate<String> typeFilter, ClassReader reader) {
        super(API);
        this.constants = constantsCollector;
        this.literals = literalsCollector;
        this.types = types;
        this.superTypes = types == null ? null : Sets.<String>newHashSet();
        this.annotationVisitor = literals == null ? null : new LiteralRecordingAnnotationVisitor();
        this.literalAdapter = literals == null ? null : new LiteralAdapter();
        this.typeFilter = typeFilter;
        if (reader != null) {
            collectClassDependencies(reader);
        }
    }

    public static ClassAnalysis analyze(String className, ClassReader reader) {
        Set<Integer> constants = Sets.newHashSet();
        Set<Integer> literals = Sets.newHashSet();
        Set<String> classDependencies = Sets.newHashSet();
        ClassDependenciesVisitor visitor = new ClassDependenciesVisitor(constants, literals, classDependencies, new ClassRelevancyFilter(className), reader);
        reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new ClassAnalysis(className, classDependencies, visitor.isDependencyToAll(), constants, literals, visitor.getSuperTypes());
    }

    public static Set<Integer> retrieveConstants(ClassReader reader) {
        Set<Integer> constants = Sets.newHashSet();
        ClassDependenciesVisitor visitor = new ClassDependenciesVisitor(constants);
        reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return constants;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        isAnnotationType = isAnnotationType(interfaces);
        if (superName != null) {
            // superName can be null if what we are analyzing is `java.lang.Object`
            // which can happen when a custom Java SDK is on classpath (typically, android.jar)
            String type = typeOfFromSlashyString(superName);
            maybeAddSuperType(type);
            maybeAddDependentType(type);
        }
        for (String s : interfaces) {
            String interfaceType = typeOfFromSlashyString(s);
            maybeAddDependentType(interfaceType);
            maybeAddSuperType(interfaceType);
        }

    }

    // performs a fast analysis of classes referenced in bytecode (method bodies)
    // avoiding us to implement a costly visitor and potentially missing edge cases
    private void collectClassDependencies(ClassReader reader) {
        char[] charBuffer = new char[reader.getMaxStringLength()];
        for (int i = 1; i < reader.getItemCount(); i++) {
            int itemOffset = reader.getItem(i);
            if (itemOffset > 0 && reader.readByte(itemOffset - 1) == 7) {
                // A CONSTANT_Class entry, read the class descriptor
                String classDescriptor = reader.readUTF8(itemOffset, charBuffer);
                Type type = Type.getObjectType(classDescriptor);
                while (type.getSort() == Type.ARRAY) {
                    type = type.getElementType();
                }
                if (type.getSort() != Type.OBJECT) {
                    // A primitive type
                    continue;
                }
                String name = type.getClassName();
                maybeAddDependentType(name);
            }
        }
    }

    protected void maybeAddSuperType(String type) {
        if (superTypes != null && typeFilter.apply(type)) {
            superTypes.add(type);
        }
    }

    protected void maybeAddDependentType(String type) {
        if (types != null && typeFilter.apply(type)) {
            types.add(type);
        }
    }

    protected String typeOfFromSlashyString(String slashyStyleDesc) {
        return Type.getObjectType(slashyStyleDesc).getClassName();
    }

    public Set<String> getSuperTypes() {
        return superTypes;
    }

    private boolean isAnnotationType(String[] interfaces) {
        return interfaces.length == 1 && interfaces[0].equals("java/lang/annotation/Annotation");
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        maybeAddDependentType(descTypeOf(desc));
        if (isAccessibleConstant(access, value) && constants != null) {
            // we need to compute a hash for a constant, which is based on the name of the constant + its value
            // otherwise we miss the case where a class defines several constants with the same value, or when
            // two values are switched
            constants.add((name + '|' + value).hashCode()); //non-private const
        }
        return null;
    }

    private static boolean isAccessibleConstant(int access, Object value) {
        return isConstant(access) && !isPrivate(access) && value != null;
    }

    protected String descTypeOf(String desc) {
        Type type = Type.getType(desc);
        if (type.getSort() == Type.ARRAY && type.getDimensions() > 0) {
            type = type.getElementType();
        }
        return type.getClassName();
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if (literals == null) {
            return null;
        }
        Type methodType = Type.getMethodType(desc);
        maybeAddDependentType(methodType.getReturnType().getClassName());
        for (Type argType : methodType.getArgumentTypes()) {
            maybeAddDependentType(argType.getClassName());
        }
        return literalAdapter;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (isAnnotationType && "Ljava/lang/annotation/Retention;".equals(desc)) {
            return new RetentionPolicyAnalyzer();
        }
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

    public boolean isDependencyToAll() {
        return dependencyToAll;
    }

    private class LiteralAdapter extends InstructionAdapter {

        protected LiteralAdapter() {
            super(API, EMPTY_VISITOR);
        }

        @Override
        public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
            maybeAddDependentType(descTypeOf(desc));
            super.visitLocalVariable(name, desc, signature, start, end, index);
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

    private class RetentionPolicyAnalyzer extends AnnotationVisitor {
        public RetentionPolicyAnalyzer() {
            super(ClassDependenciesVisitor.API);
        }

        @Override
        public void visitEnum(String name, String desc, String value) {
            if ("Ljava/lang/annotation/RetentionPolicy;".equals(desc)) {
                RetentionPolicy policy = RetentionPolicy.valueOf(value);
                if (policy == RetentionPolicy.SOURCE) {
                    dependencyToAll = true;
                }
            }
        }
    }
}
