/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.initialization;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.LocationAwareException;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.Contextual;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.util.ReflectionUtil;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link ClassGenerator} which mixes {@link org.gradle.api.LocationAwareException} into the supplied exception
 * types. Uses {@link ExceptionHelper} to do the work.
 */
public class ExceptionDecoratingClassGenerator implements ClassGenerator {
    private static final Map<Class<?>, Class<?>> GENERATED_CLASSES = new HashMap<Class<?>, Class<?>>();

    public <T> T newInstance(Class<T> type, Object... parameters) {
        Throwable throwable = ReflectionUtil.newInstance(generate(type), parameters);
        throwable.setStackTrace(((Throwable) parameters[0]).getStackTrace());
        return type.cast(throwable);
    }

    public <T> Class<? extends T> generate(Class<T> type) {
        Class generated = GENERATED_CLASSES.get(type);
        if (generated == null) {
            generated = doGenerate(type);
            GENERATED_CLASSES.put(type, generated);
        }
        return generated;
    }

    private <T> Class<? extends T> doGenerate(Class<T> type) {
        ClassWriter visitor = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String typeName = StringUtils.substringBeforeLast(type.getName(), ".") + ".LocationAware" + type.getSimpleName();
        Type generatedType = Type.getType("L" + typeName.replaceAll("\\.", "/") + ";");
        Type superclassType = Type.getType(type);

        visitor.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC, generatedType.getInternalName(), null,
                superclassType.getInternalName(), new String[]{Type.getType(LocationAwareException.class).getInternalName()});

        Type helperType = Type.getType(ExceptionHelper.class);
        Type throwableType = Type.getType(Throwable.class);
        Type scriptSourceType = Type.getType(ScriptSource.class);
        Type integerType = Type.getType(Integer.class);

        // GENERATE private ExceptionHelper helper;
        visitor.visitField(Opcodes.ACC_PRIVATE, "helper", helperType.getDescriptor(), null, null);

        // GENERATE <init>(<type> target, ScriptSource source, Integer lineNumber)

        String methodDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE,
                new Type[]{superclassType, scriptSourceType, integerType});
        MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "<init>", methodDescriptor, null,
                new String[0]);
        methodVisitor.visitCode();

        boolean noArgsConstructor;
        try {
            type.getConstructor(type);
            noArgsConstructor = false;
        } catch (NoSuchMethodException e) {
            try {
                type.getConstructor();
                noArgsConstructor = true;
            } catch (NoSuchMethodException e1) {
                throw new IllegalArgumentException(String.format(
                        "Cannot create subtype for exception '%s'. It needs a zero-args or copy constructor.",
                        type.getName()));
            }
        }

        if (noArgsConstructor) {
            // GENERATE super()
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), "<init>",
                    Type.getMethodDescriptor(Type.VOID_TYPE, new Type[0]));
            // END super()
        } else {
            // GENERATE super(target)
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), "<init>",
                    Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{superclassType}));
            // END super(target)
        }

        // GENERATE helper = new ExceptionHelper(this, target, source, lineNumber)
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);

        methodVisitor.visitTypeInsn(Opcodes.NEW, helperType.getInternalName());
        methodVisitor.visitInsn(Opcodes.DUP);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 2);
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 3);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, helperType.getInternalName(), "<init>",
                Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{throwableType, throwableType, scriptSourceType, integerType}));

        methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), "helper",
                helperType.getDescriptor());

        // END helper = new ExceptionHelper(target)

        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();

        // END <init>(<type> target, ScriptSource source, Integer lineNumber)

        for (Method method : ExceptionHelper.class.getDeclaredMethods()) {
            // GENERATE public <type> <method>() { return helper.<method>(); }
            methodDescriptor = Type.getMethodDescriptor(Type.getType(method.getReturnType()), new Type[0]);
            methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), methodDescriptor, null,
                    new String[0]);
            methodVisitor.visitCode();

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), "helper",
                    helperType.getDescriptor());
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, helperType.getInternalName(), method.getName(),
                    methodDescriptor);

            methodVisitor.visitInsn(Opcodes.ARETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
            // END public <type> <method>() { return helper.<method>(); }
        }

        visitor.visitEnd();

        byte[] bytecode = visitor.toByteArray();
        return (Class<T>) ReflectionUtil.invoke(type.getClassLoader(), "defineClass", new Object[]{
                typeName, bytecode, 0, bytecode.length
        });
    }

    public static class ExceptionHelper {
        private final Throwable target;
        private final ScriptSource source;
        private final Integer lineNumber;

        public ExceptionHelper(Throwable owner, Throwable target, ScriptSource source, Integer lineNumber) {
            if (owner.getCause() == null) {
                owner.initCause(target.getCause());
            }
            this.target = target;
            this.source = source;
            this.lineNumber = lineNumber;
        }

        public String getOriginalMessage() {
            return target.getMessage();
        }

        public Throwable getOriginalException() {
            return target;
        }

        public String getLocation() {
            if (source == null) {
                return null;
            }
            String sourceMsg = StringUtils.capitalize(source.getDisplayName());
            if (lineNumber == null) {
                return sourceMsg;
            }
            return String.format("%s line: %d", sourceMsg, lineNumber);
        }

        public String getMessage() {
            String location = getLocation();
            String message = target.getMessage();
            if (location == null && message == null) {
                return null;
            }
            if (location == null) {
                return message;
            }
            if (message == null) {
                return location;
            }
            return String.format("%s%n%s", location, message);
        }

        public ScriptSource getScriptSource() {
            return source;
        }

        public Integer getLineNumber() {
            return lineNumber;
        }

        public List<Throwable> getReportableCauses() {
            ArrayList<Throwable> causes = new ArrayList<Throwable>();
            for (Throwable t = target.getCause(); t != null; t = t.getCause()) {
                causes.add(t);
                if (t.getClass().getAnnotation(Contextual.class) == null) {
                    break;
                }
            }
            return causes;
        }

    }
}
