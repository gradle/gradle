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
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.initialization.transform.utils.ClassAnalysisUtils;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAbi;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis;
import org.gradle.api.internal.tasks.compile.incremental.deps.FieldAbi;
import org.gradle.api.internal.tasks.compile.incremental.deps.MethodAbi;
import org.gradle.model.internal.asm.AsmConstants;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.lang.annotation.RetentionPolicy;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class ClassDependenciesVisitor extends ClassVisitor {

    private static final int API = AsmConstants.ASM_LEVEL;
    /**
     * Handle describing {@link java.lang.invoke.ConstantBootstraps#invoke(MethodHandles.Lookup, String, Class, MethodHandle, Object...)}.
     */
    private static final Handle CONSTANT_BOOTSTRAPS_INVOKE = new Handle(
        Opcodes.H_INVOKESTATIC,
        "java/lang/invoke/ConstantBootstraps",
        "invoke",
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/invoke/MethodHandle;[Ljava/lang/Object;)Ljava/lang/Object;",
        false
    );
    /**
     * Handle describing {@link java.lang.constant.ClassDesc#of(String)}.
     */
    private static final Handle CLASS_DESC_OF = new Handle(
        Opcodes.H_INVOKESTATIC,
        "java/lang/constant/ClassDesc",
        "of",
        "(Ljava/lang/String;)Ljava/lang/constant/ClassDesc;",
        true
    );

    private final IntSet constants;
    private final Set<String> privateTypes;
    private final Set<String> accessibleTypes;
    private final Predicate<String> typeFilter;
    private final StringInterner interner;
    private boolean isAnnotationType;
    private String dependencyToAllReason;
    private String moduleName;
    private final RetentionPolicyVisitor retentionPolicyVisitor;

    private int access;
    private String signature;
    private String superName;
    private String[] interfaces;
    private final Map<String, FieldAbi> fieldAbis;
    private final Map<String, MethodAbi> methodAbis;

    private ClassDependenciesVisitor(Predicate<String> typeFilter, ClassReader reader, StringInterner interner) {
        super(API);
        this.constants = new IntOpenHashSet(2);
        this.privateTypes = new HashSet<>();
        this.accessibleTypes = new HashSet<>();
        this.retentionPolicyVisitor = new RetentionPolicyVisitor();
        this.fieldAbis = new HashMap<>();
        this.methodAbis = new HashMap<>();
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
        return new ClassAnalysis(interner.intern(name), visitor.getPrivateClassDependencies(), visitor.getAccessibleClassDependencies(), visitor.getDependencyToAllReason(), visitor.getConstants(), visitor.getClassAbi());
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

        this.access = access;
        this.signature = signature;
        this.superName = superName;
        this.interfaces = interfaces;
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

    public ClassAbi getClassAbi() {
        return new ClassAbi(access, signature, superName, interfaces == null ? Collections.emptyList() : Arrays.asList(interfaces), fieldAbis, methodAbis);
    }

    private boolean isAnnotationType(String[] interfaces) {
        return interfaces.length == 1 && interfaces[0].equals("java/lang/annotation/Annotation");
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        boolean isAccessible = isAccessible(access);
        Set<String> types = isAccessible ? accessibleTypes : privateTypes;
        maybeAddClassTypesFromSignature(signature, types);
        maybeAddDependentType(types, Type.getType(desc));
        if (isAccessibleConstant(access, value)) {
            // we need to compute a hash for a constant, which is based on the name of the constant + its value
            // otherwise we miss the case where a class defines several constants with the same value, or when
            // two values are switched
            constants.add((name + '|' + value).hashCode()); //non-private const
        }
        if (isAccessible) {
            fieldAbis.put(name, new FieldAbi(access, desc, signature, value));
        }
        return new FieldVisitor(types);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        boolean isAccessible = isAccessible(access);
        Set<String> types = isAccessible ? accessibleTypes : privateTypes;
        maybeAddClassTypesFromSignature(signature, types);
        addTypesFromMethodDescriptor(types, desc);
        if (isAccessible) {
            methodAbis.put(name, new MethodAbi(access, desc, signature, exceptions == null ? Collections.emptyList() : Arrays.asList(exceptions)));
        }
        return new MethodVisitor(types);
    }

    private void addTypesFromMethodDescriptor(Set<String> types, String desc) {
        Type methodType = Type.getMethodType(desc);
        maybeAddDependentType(types, methodType.getReturnType());
        for (Type argType : methodType.getArgumentTypes()) {
            maybeAddDependentType(types, argType);
        }
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

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            if (tryHandleSpecialBootstrapMethod(BootstrapMethod.fromIndy(bootstrapMethodHandle, bootstrapMethodArguments))) {
                return;
            }
            addTypesFromMethodDescriptor(privateTypes, descriptor);
            maybeAddDependentType(privateTypes, Type.getObjectType(bootstrapMethodHandle.getOwner()));

            for (Object arg : bootstrapMethodArguments) {
                addDependentTypeFromBootstrapMethodArgument(arg);
            }
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof ConstantDynamic) {
                addDependentTypesFromConstantDynamic((ConstantDynamic) value);
            }
        }

        private void addDependentTypesFromConstantDynamic(ConstantDynamic arg) {
            if (tryHandleSpecialBootstrapMethod(BootstrapMethod.fromConstantDynamic(arg))) {
                return;
            }
            maybeAddDependentType(privateTypes, Type.getObjectType(arg.getBootstrapMethod().getOwner()));

            for (int i = 0; i < arg.getBootstrapMethodArgumentCount(); i++) {
                addDependentTypeFromBootstrapMethodArgument(arg.getBootstrapMethodArgument(i));
            }
        }

        private void addDependentTypeFromBootstrapMethodArgument(Object arg) {
            if (arg instanceof Type) {
                maybeAddDependentType(privateTypes, (Type) arg);
            } else if (arg instanceof Handle) {
                maybeAddDependentType(privateTypes, Type.getObjectType(((Handle) arg).getOwner()));
            } else if (arg instanceof ConstantDynamic) {
                addDependentTypesFromConstantDynamic((ConstantDynamic) arg);
            }
        }

        /**
         * Some bootstrap methods describe a dependency on a class, despite not containing a class reference in their
         * arguments. One way this can happen is with qualified enums in a switch expression, where they will bootstrap
         * a class constant using {@link java.lang.constant.ClassDesc#of(String)}. The string represents a class name
         * which must be a dependency of the class being analyzed.
         *
         * @param bootstrapMethod the bootstrap method to check
         * @return if the bootstrap method was handled and its types added to the dependency set
         */
        private boolean tryHandleSpecialBootstrapMethod(BootstrapMethod bootstrapMethod) {
            // Currently this method only handles the ClassDesc#of case, but there may be others in the future.
            // If so, this code should be refactored out to its own method.
            if (!bootstrapMethod.getHandle().equals(CONSTANT_BOOTSTRAPS_INVOKE)) {
                return false;
            }
            if (bootstrapMethod.getArguments().size() != 2) {
                return false;
            }
            if (!CLASS_DESC_OF.equals(bootstrapMethod.getArguments().get(0))) {
                return false;
            }
            Object className = bootstrapMethod.getArguments().get(1);
            if (!(className instanceof String)) {
                return false;
            }
            maybeAddDependentType(privateTypes, Type.getObjectType(((String) className).replace('.', '/')));
            return true;
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
