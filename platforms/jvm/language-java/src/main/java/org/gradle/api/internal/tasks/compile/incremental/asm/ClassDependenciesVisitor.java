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

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.gradle.api.internal.initialization.transform.utils.ClassAnalysisUtils;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis;
import org.gradle.model.internal.asm.AsmConstants;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class ClassDependenciesVisitor extends ClassVisitor {

    private final static int API = AsmConstants.ASM_LEVEL;

    private final IntSet constants;
    private final Set<String> privateTypes;
    private final Set<String> accessibleTypes;
    private final Predicate<String> typeFilter;
    private final StringInterner interner;
    private boolean isAnnotationType;
    private String dependencyToAllReason;
    private String moduleName;
    private final RetentionPolicyVisitor retentionPolicyVisitor;

    private ClassDependenciesVisitor(Predicate<String> typeFilter, ClassReader reader, StringInterner interner) {
        super(API);
        this.constants = new IntOpenHashSet(2);
        this.privateTypes = new HashSet<>();
        this.accessibleTypes = new HashSet<>();
        this.retentionPolicyVisitor = new RetentionPolicyVisitor();
        this.typeFilter = typeFilter;
        this.interner = interner;
        collectRemainingClassDependencies(reader);
    }

    public static ClassAnalysis analyze(String className, ClassReader reader, StringInterner interner) {
        ClassDependenciesVisitor visitor = new ClassDependenciesVisitor(new ClassRelevancyFilter(className), reader, interner);
        reader.accept(visitor, ClassReader.SKIP_FRAMES);

        // Remove the "API accessible" types from the "privately used types"
        visitor.privateTypes.removeAll(visitor.accessibleTypes);
        String name = visitor.moduleName != null ? visitor.moduleName : className;
        return new ClassAnalysis(interner.intern(name), visitor.getPrivateClassDependencies(), visitor.getAccessibleClassDependencies(), visitor.getDependencyToAllReason(), visitor.getConstants());
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        isAnnotationType = isAnnotationType(interfaces);
        Set<String> types = isAccessible(access) ? accessibleTypes : privateTypes;
        maybeAddClassTypesFromSignature(signature, types);
        if (superName != null) {
            // superName can be null if what we are analyzing is `java.lang.Object`
            // which can happen when a custom Java SDK is on classpath (typically, android.jar)
            Type type = Type.getObjectType(superName);
            maybeAddDependentType(types, type);
        }
        for (String s : interfaces) {
            Type interfaceType = Type.getObjectType(s);
            maybeAddDependentType(types, interfaceType);
        }
    }

    @Override
    public ModuleVisitor visitModule(String name, int access, String version) {
        moduleName = name;
        dependencyToAllReason = "module-info of '" + name + "' has changed";
        return null;
    }

    // performs a fast analysis of classes referenced in bytecode (method bodies)
    // avoiding us to implement a costly visitor and potentially missing edge cases
    private void collectRemainingClassDependencies(ClassReader reader) {
        ClassAnalysisUtils.getClassDependencies(reader, classDescriptor -> {
            Type type = Type.getObjectType(classDescriptor);
            maybeAddDependentType(privateTypes, type);
        });
    }

    private void maybeAddClassTypesFromSignature(String signature, Set<String> types) {
        if (signature != null) {
            SignatureReader signatureReader = new SignatureReader(signature);
            signatureReader.accept(new SignatureVisitor(API) {
                @Override
                public void visitClassType(String className) {
                    Type type = Type.getObjectType(className);
                    maybeAddDependentType(types, type);
                }
            });
        }
    }

    protected void maybeAddDependentType(Set<String> types, Type type) {
        while (type.getSort() == Type.ARRAY) {
            type = type.getElementType();
        }
        if (type.getSort() != Type.OBJECT) {
            return;
        }
        String name = type.getClassName();
        if (typeFilter.test(name)) {
            types.add(interner.intern(name));
        }
    }

    public Set<String> getPrivateClassDependencies() {
        return privateTypes;
    }

    public Set<String> getAccessibleClassDependencies() {
        return accessibleTypes;
    }

    public IntSet getConstants() {
        return constants;
    }

    private boolean isAnnotationType(String[] interfaces) {
        return interfaces.length == 1 && interfaces[0].equals("java/lang/annotation/Annotation");
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        Set<String> types = isAccessible(access) ? accessibleTypes : privateTypes;
        maybeAddClassTypesFromSignature(signature, types);
        maybeAddDependentType(types, Type.getType(desc));
        if (isAccessibleConstant(access, value)) {
            // we need to compute a hash for a constant, which is based on the name of the constant + its value
            // otherwise we miss the case where a class defines several constants with the same value, or when
            // two values are switched
            constants.add((name + '|' + value).hashCode()); //non-private const
        }
        return new FieldVisitor(types);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        Set<String> types = isAccessible(access) ? accessibleTypes : privateTypes;
        maybeAddClassTypesFromSignature(signature, types);
        Type methodType = Type.getMethodType(desc);
        maybeAddDependentType(types, methodType.getReturnType());
        for (Type argType : methodType.getArgumentTypes()) {
            maybeAddDependentType(types, argType);
        }
        return new MethodVisitor(types);
    }

    @Override
    public org.objectweb.asm.AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (isAnnotationType && "Ljava/lang/annotation/Retention;".equals(desc)) {
            return retentionPolicyVisitor;
        } else {
            maybeAddDependentType(accessibleTypes, Type.getType(desc));
            return new AnnotationVisitor(accessibleTypes);
        }
    }

    private static boolean isAccessible(int access) {
        return (access & Opcodes.ACC_PRIVATE) == 0;
    }

    private static boolean isAccessibleConstant(int access, Object value) {
        return isConstant(access) && isAccessible(access) && value != null;
    }

    private static boolean isConstant(int access) {
        return (access & Opcodes.ACC_FINAL) != 0 && (access & Opcodes.ACC_STATIC) != 0;
    }

    public String getDependencyToAllReason() {
        return dependencyToAllReason;
    }

    private class FieldVisitor extends org.objectweb.asm.FieldVisitor {
        private final Set<String> types;

        public FieldVisitor(Set<String> types) {
            super(API);
            this.types = types;
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            maybeAddDependentType(types, Type.getType(descriptor));
            return new AnnotationVisitor(types);
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            maybeAddDependentType(types, Type.getType(descriptor));
            return new AnnotationVisitor(types);
        }
    }

    private class MethodVisitor extends org.objectweb.asm.MethodVisitor {
        private final Set<String> types;

        protected MethodVisitor(Set<String> types) {
            super(API);
            this.types = types;
        }

        @Override
        public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
            maybeAddClassTypesFromSignature(signature, privateTypes);
            maybeAddDependentType(privateTypes, Type.getType(desc));
            super.visitLocalVariable(name, desc, signature, start, end, index);
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            maybeAddDependentType(types, Type.getType(descriptor));
            return new AnnotationVisitor(types);
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
            maybeAddDependentType(types, Type.getType(descriptor));
            return new AnnotationVisitor(types);
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            maybeAddDependentType(types, Type.getType(descriptor));
            return new AnnotationVisitor(types);
        }
    }

    private class RetentionPolicyVisitor extends org.objectweb.asm.AnnotationVisitor {
        public RetentionPolicyVisitor() {
            super(ClassDependenciesVisitor.API);
        }

        @Override
        public void visitEnum(String name, String desc, String value) {
            if ("Ljava/lang/annotation/RetentionPolicy;".equals(desc)) {
                RetentionPolicy policy = RetentionPolicy.valueOf(value);
                if (policy == RetentionPolicy.SOURCE) {
                    dependencyToAllReason = "source retention annotation '" + name + "' has changed";
                }
            }
        }
    }

    private class AnnotationVisitor extends org.objectweb.asm.AnnotationVisitor {
        private final Set<String> types;

        public AnnotationVisitor(Set<String> types) {
            super(ClassDependenciesVisitor.API);
            this.types = types;
        }

        @Override
        public void visit(String name, Object value) {
            if (value instanceof Type) {
                maybeAddDependentType(types, (Type) value);
            }
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitArray(String name) {
            return this;
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitAnnotation(String name, String descriptor) {
            maybeAddDependentType(types, Type.getType(descriptor));
            return this;
        }
    }
}
