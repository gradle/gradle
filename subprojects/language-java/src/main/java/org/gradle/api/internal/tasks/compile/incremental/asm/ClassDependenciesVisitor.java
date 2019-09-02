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
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis;
import org.gradle.internal.classanalysis.AsmConstants;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

import java.lang.annotation.RetentionPolicy;
import java.util.Set;

public class ClassDependenciesVisitor extends ClassVisitor {

    private final static int API = AsmConstants.ASM_LEVEL;

    private final MethodVisitor privateMethodVisitor;
    private final MethodVisitor accessibleMethodVisitor;
    private final FieldVisitor privateFieldVisitor;
    private final FieldVisitor accessibleFieldVisitor;
    private final IntSet constants;
    private final Set<String> privateTypes;
    private final Set<String> accessibleTypes;
    private final Predicate<String> typeFilter;
    private final StringInterner interner;
    private boolean isAnnotationType;
    private boolean dependencyToAll;
    private final RetentionPolicyVisitor retentionPolicyVisitor;
    private final AnnotationVisitor accessibleAnnotationVisitor;
    private final AnnotationVisitor privateAnnotationVisitor;

    private ClassDependenciesVisitor(Predicate<String> typeFilter, ClassReader reader, StringInterner interner) {
        super(API);
        this.constants = new IntOpenHashSet(2);
        this.privateTypes = Sets.newHashSet();
        this.accessibleTypes = Sets.newHashSet();
        this.privateMethodVisitor = new MethodVisitor(false);
        this.accessibleMethodVisitor = new MethodVisitor(true);
        this.privateFieldVisitor = new FieldVisitor(false);
        this.accessibleFieldVisitor = new FieldVisitor(true);
        this.retentionPolicyVisitor = new RetentionPolicyVisitor();
        this.privateAnnotationVisitor = new AnnotationVisitor(false);
        this.accessibleAnnotationVisitor = new AnnotationVisitor(true);
        this.typeFilter = typeFilter;
        this.interner = interner;
        collectRemainingClassDependencies(reader);
    }

    public static ClassAnalysis analyze(String className, ClassReader reader, StringInterner interner) {
        ClassDependenciesVisitor visitor = new ClassDependenciesVisitor(new ClassRelevancyFilter(className), reader, interner);
        reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        // Remove the "API accessible" types from the "privately used types"
        visitor.privateTypes.removeAll(visitor.accessibleTypes);

        return new ClassAnalysis(interner.intern(className), visitor.getPrivateClassDependencies(), visitor.getAccessibleClassDependencies(), visitor.isDependencyToAll(), visitor.getConstants());
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        isAnnotationType = isAnnotationType(interfaces);
        if (superName != null) {
            // superName can be null if what we are analyzing is `java.lang.Object`
            // which can happen when a custom Java SDK is on classpath (typically, android.jar)
            String type = typeOfFromSlashyString(superName);
            maybeAddDependentType(isAccessible(access), type);
        }
        for (String s : interfaces) {
            String interfaceType = typeOfFromSlashyString(s);
            maybeAddDependentType(isAccessible(access), interfaceType);
        }
    }

    @Override
    public ModuleVisitor visitModule(String name, int access, String version) {
        dependencyToAll = true;
        return null;
    }

    // performs a fast analysis of classes referenced in bytecode (method bodies)
    // avoiding us to implement a costly visitor and potentially missing edge cases
    private void collectRemainingClassDependencies(ClassReader reader) {
        char[] charBuffer = new char[reader.getMaxStringLength()];
        for (int i = 1; i < reader.getItemCount(); i++) {
            int itemOffset = reader.getItem(i);
            // see https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.4
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
                // Any class that hasn't been added yet, is used in method bodies, which are implementation details and not visible as an "API"1
                if (!accessibleTypes.contains(name)) {
                    maybeAddDependentType(false, name);
                }
            }
        }
    }

    protected void maybeAddDependentType(boolean accessible, String type) {
        if (typeFilter.apply(type)) {
            (accessible ? accessibleTypes : privateTypes).add(intern(type));
        }
    }

    private String intern(String type) {
        return interner.intern(type);
    }

    protected String typeOfFromSlashyString(String slashyStyleDesc) {
        return Type.getObjectType(slashyStyleDesc).getClassName();
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
        boolean accessible = isAccessible(access);
        maybeAddDependentType(accessible, descTypeOf(desc));
        if (isAccessibleConstant(access, value)) {
            // we need to compute a hash for a constant, which is based on the name of the constant + its value
            // otherwise we miss the case where a class defines several constants with the same value, or when
            // two values are switched
            constants.add((name + '|' + value).hashCode()); //non-private const
        }
        return fieldVisitor(accessible);
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
        boolean accessible = isAccessible(access);
        Type methodType = Type.getMethodType(desc);
        maybeAddDependentType(accessible, methodType.getReturnType().getClassName());
        for (Type argType : methodType.getArgumentTypes()) {
            maybeAddDependentType(accessible, argType.getClassName());
        }
        return methodVisitor(accessible);
    }

    @Override
    public org.objectweb.asm.AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (isAnnotationType && "Ljava/lang/annotation/Retention;".equals(desc)) {
            return retentionPolicyVisitor;
        } else {
            maybeAddDependentType(true, Type.getType(desc).getClassName());
            return accessibleAnnotationVisitor;
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

    public boolean isDependencyToAll() {
        return dependencyToAll;
    }

    private AnnotationVisitor annotationVisitor(boolean accessible) {
        return accessible ? accessibleAnnotationVisitor : privateAnnotationVisitor;
    }

    private MethodVisitor methodVisitor(boolean accessible) {
        return accessible ? accessibleMethodVisitor : privateMethodVisitor;
    }

    private FieldVisitor fieldVisitor(boolean accessible) {
        return accessible ? accessibleFieldVisitor : privateFieldVisitor;
    }

    private class FieldVisitor extends org.objectweb.asm.FieldVisitor {
        private final boolean accessible;

        public FieldVisitor(boolean accessible) {
            super(API);
            this.accessible = accessible;
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            maybeAddDependentType(accessible, Type.getType(descriptor).getClassName());
            return annotationVisitor(accessible);
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            maybeAddDependentType(accessible, Type.getType(descriptor).getClassName());
            return annotationVisitor(accessible);
        }
    }

    private class MethodVisitor extends org.objectweb.asm.MethodVisitor {
        private final boolean accessible;

        protected MethodVisitor(boolean accessible) {
            super(API);
            this.accessible = accessible;
        }

        @Override
        public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
            maybeAddDependentType(accessible, descTypeOf(desc));
            super.visitLocalVariable(name, desc, signature, start, end, index);
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            maybeAddDependentType(accessible, Type.getType(descriptor).getClassName());
            return annotationVisitor(accessible);
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
            maybeAddDependentType(accessible, Type.getType(descriptor).getClassName());
            return annotationVisitor(accessible);
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            maybeAddDependentType(accessible, Type.getType(descriptor).getClassName());
            return annotationVisitor(accessible);
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
                    dependencyToAll = true;
                }
            }
        }
    }

    private class AnnotationVisitor extends org.objectweb.asm.AnnotationVisitor {
        private final boolean accessible;

        public AnnotationVisitor(boolean accessible) {
            super(ClassDependenciesVisitor.API);
            this.accessible = accessible;
        }

        @Override
        public void visit(String name, Object value) {
            if (value instanceof Type) {
                maybeAddDependentType(accessible, ((Type) value).getClassName());
            }
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitArray(String name) {
            return this;
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitAnnotation(String name, String descriptor) {
            maybeAddDependentType(accessible, Type.getType(descriptor).getClassName());
            return this;
        }
    }
}
