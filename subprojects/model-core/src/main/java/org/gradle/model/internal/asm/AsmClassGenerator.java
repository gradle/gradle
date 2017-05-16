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

package org.gradle.model.internal.asm;

import org.gradle.internal.Cast;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;

public class AsmClassGenerator {
    private static final JavaMethod<ClassLoader, ?> DEFINE_CLASS_METHOD = JavaReflectionUtil.method(ClassLoader.class, Class.class, "defineClass", String.class, byte[].class, Integer.TYPE, Integer.TYPE);
    private final ClassWriter visitor;
    private final String generatedTypeName;
    private final Type generatedType;
    private final Class<?> targetType;

    public AsmClassGenerator(Class<?> targetType, String classNameSuffix) {
        this.targetType = targetType;
        visitor = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        generatedTypeName = targetType.getName() + classNameSuffix;
        generatedType = Type.getType("L" + generatedTypeName.replaceAll("\\.", "/") + ";");
    }

    public ClassWriter getVisitor() {
        return visitor;
    }

    public Class<?> getTargetType() {
        return targetType;
    }

    public String getGeneratedTypeName() {
        return generatedTypeName;
    }

    public Type getGeneratedType() {
        return generatedType;
    }

    public <T> Class<T> define() {
        return define(targetType.getClassLoader());
    }

    public <T> Class<T> define(ClassLoader targetClassLoader) {
        byte[] generatedByteCode = visitor.toByteArray();
        return Cast.uncheckedCast(DEFINE_CLASS_METHOD.invoke(targetClassLoader, generatedTypeName, generatedByteCode, 0, generatedByteCode.length));
    }
}
