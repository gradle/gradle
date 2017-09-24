/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.model;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import groovy.lang.GroovyObject;
import org.gradle.api.GradleException;
import org.gradle.api.Named;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.internal.UncheckedException;
import org.gradle.model.internal.asm.AsmClassGenerator;
import org.gradle.model.internal.inspect.FormattingValidationProblemCollector;
import org.gradle.model.internal.inspect.ValidationProblemCollector;
import org.gradle.model.internal.type.ModelType;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.objectweb.asm.Opcodes.*;

public class NamedObjectInstantiator {
    public static final NamedObjectInstantiator INSTANCE = new NamedObjectInstantiator();
    private static final Type OBJECT = Type.getType(Object.class);
    private static final Type STRING = Type.getType(String.class);
    private static final Type CLASS_GENERATING_LOADER = Type.getType(ClassGeneratingLoader.class);
    private static final Type MANAGED = Type.getType(Managed.class);
    private static final String[] INTERFACES_FOR_ABSTRACT_CLASS = {MANAGED.getInternalName()};
    private static final String RETURN_VOID = Type.getMethodDescriptor(Type.VOID_TYPE);
    private static final String RETURN_STRING = Type.getMethodDescriptor(STRING);
    private static final String RETURN_VOID_FROM_STRING = Type.getMethodDescriptor(Type.VOID_TYPE, STRING);
    private static final String RETURN_OBJECT_FROM_STRING = Type.getMethodDescriptor(OBJECT, STRING);
    private static final String NAME_FIELD = "__name__";
    private static final String[] EMPTY_STRINGS = new String[0];
    private static final String CONSTRUCTOR_NAME = "<init>";

    // Currently retains strong references to types
    private final LoadingCache<Class<?>, LoadingCache<String, Object>> values = CacheBuilder.newBuilder().build(new CacheLoader<Class<?>, LoadingCache<String, Object>>() {
        @Override
        public LoadingCache<String, Object> load(Class<?> type) {
            return CacheBuilder.newBuilder().build(loaderFor(type));
        }
    });

    public <T extends Named> T named(final Class<T> type, final String name) throws ObjectInstantiationException {
        try {
            return type.cast(values.getUnchecked(type).getUnchecked(name));
        } catch (UncheckedExecutionException e) {
            throw new ObjectInstantiationException(type, e.getCause());
        }
    }

    private ClassGeneratingLoader loaderFor(Class<?> publicClass) {
        //
        // Generate implementation class
        //

        FormattingValidationProblemCollector problemCollector = new FormattingValidationProblemCollector("Named implementation class", ModelType.of(publicClass));
        visitFields(publicClass, problemCollector);
        if (problemCollector.hasProblems()) {
            throw new GradleException(problemCollector.format());
        }

        AsmClassGenerator generator = new AsmClassGenerator(publicClass, "$Impl");
        Type implementationType = generator.getGeneratedType();
        ClassWriter visitor = generator.getVisitor();
        Type publicType = Type.getType(publicClass);

        Type superClass;
        String[] interfaces;
        if (publicClass.isInterface()) {
            superClass = OBJECT;
            interfaces = new String[]{publicType.getInternalName(), MANAGED.getInternalName()};
        } else {
            superClass = publicType;
            interfaces = INTERFACES_FOR_ABSTRACT_CLASS;
        }

        visitor.visit(V1_5, ACC_PUBLIC | ACC_SYNTHETIC, implementationType.getInternalName(), null, superClass.getInternalName(), interfaces);

        //
        // Add name field
        //

        visitor.visitField(ACC_PRIVATE, NAME_FIELD, STRING.getDescriptor(), null, null);

        //
        // Add constructor
        //

        MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC, CONSTRUCTOR_NAME, RETURN_VOID_FROM_STRING, null, EMPTY_STRINGS);
        methodVisitor.visitCode();
        // Call this.super()
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superClass.getInternalName(), CONSTRUCTOR_NAME, RETURN_VOID, false);
        // Set this.name = param1
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
        methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, implementationType.getInternalName(), NAME_FIELD, STRING.getDescriptor());
        // Done
        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();

        //
        // Add `getName()`
        //

        methodVisitor = visitor.visitMethod(ACC_PUBLIC, "getName", RETURN_STRING, null, EMPTY_STRINGS);
        methodVisitor.visitCode();
        // return this.name
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitFieldInsn(Opcodes.GETFIELD, implementationType.getInternalName(), NAME_FIELD, STRING.getDescriptor());
        methodVisitor.visitInsn(Opcodes.ARETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();

        //
        // Add `toString()`
        //

        methodVisitor = visitor.visitMethod(ACC_PUBLIC, "toString", RETURN_STRING, null, EMPTY_STRINGS);
        methodVisitor.visitCode();
        // return this.name
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitFieldInsn(Opcodes.GETFIELD, implementationType.getInternalName(), NAME_FIELD, STRING.getDescriptor());
        methodVisitor.visitInsn(Opcodes.ARETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();

        visitor.visitEnd();
        generator.define();

        //
        // Generate factory class
        //

        generator = new AsmClassGenerator(publicClass, "$Factory");
        visitor = generator.getVisitor();
        visitor.visit(V1_5, ACC_PUBLIC | ACC_SYNTHETIC, generator.getGeneratedType().getInternalName(), null, CLASS_GENERATING_LOADER.getInternalName(), EMPTY_STRINGS);

        //
        // Add constructor
        //

        methodVisitor = visitor.visitMethod(ACC_PUBLIC, CONSTRUCTOR_NAME, RETURN_VOID, null, EMPTY_STRINGS);
        methodVisitor.visitCode();
        // Call this.super()
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, CLASS_GENERATING_LOADER.getInternalName(), CONSTRUCTOR_NAME, RETURN_VOID, false);
        // Done
        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();

        //
        // Add factory method
        //

        methodVisitor = visitor.visitMethod(ACC_PUBLIC, "load", RETURN_OBJECT_FROM_STRING, null, EMPTY_STRINGS);
        methodVisitor.visitCode();
        // Call return new <implClass>(param1)
        methodVisitor.visitTypeInsn(Opcodes.NEW, implementationType.getInternalName());
        methodVisitor.visitInsn(Opcodes.DUP);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, implementationType.getInternalName(), CONSTRUCTOR_NAME, RETURN_VOID_FROM_STRING, false);
        methodVisitor.visitInsn(Opcodes.ARETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();

        visitor.visitEnd();
        Class<Object> factoryClass = generator.define();
        try {
            return (ClassGeneratingLoader) (factoryClass.newInstance());
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private void visitFields(Class<?> type, ValidationProblemCollector collector) {
        if (type.equals(Object.class)) {
            return;
        }
        if (type.getSuperclass() != null) {
            visitFields(type.getSuperclass(), collector);
        }

        // Disallow instance fields. This doesn't guarantee that the object is immutable, just makes it less likely
        // We might tighten this constraint to also disallow any _code_ on immutable types that reaches out to static state
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || GroovyObject.class.isAssignableFrom(type) && field.getName().equals("metaClass")) {
                continue;
            }
            collector.add(field, "A Named implementation class must not define any instance fields.");
        }
    }

    /**
     * Mixed into each generated class, to mark it as managed.
     */
    public interface Managed {
    }

    protected abstract static class ClassGeneratingLoader extends CacheLoader<String, Object> {
        @Override
        public abstract Object load(String name);
    }
}
