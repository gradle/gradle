/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.manage.schema.extract;

import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.classpath.ClassPath;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class AbstractProxyClassGenerator {
    protected static final String CONSTRUCTOR_NAME = "<init>";
    protected static final String STATIC_CONSTRUCTOR_NAME = "<clinit>";
    protected static final String CONCRETE_SIGNATURE = null;
    protected static final String[] NO_EXCEPTIONS = new String[0];

    @SuppressWarnings("unchecked")
    protected <T> Class<? extends T> defineClass(ClassWriter visitor, ClassLoader classLoader, String generatedTypeName) {
        byte[] bytecode = visitor.toByteArray();

        // TODO: need to share with AsmBackedClassGenerator
        if (classLoader instanceof VisitableURLClassLoader) {
            VisitableURLClassLoader loader = (VisitableURLClassLoader) classLoader;
            return (Class) loader.injectClass(generatedTypeName, bytecode);
        }
        // TODO: need to cache these, also rework boostrapping so that the Gradle runtime ClassLoader is-a VisitableURLClassLoader
        VisitableURLClassLoader child = new VisitableURLClassLoader(classLoader, ClassPath.EMPTY);
        return (Class) child.injectClass(generatedTypeName, bytecode);
    }

    protected void putThisOnStack(MethodVisitor methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
    }

    protected void finishVisitingMethod(MethodVisitor methodVisitor) {
        finishVisitingMethod(methodVisitor, Opcodes.RETURN);
    }

    protected void finishVisitingMethod(MethodVisitor methodVisitor, int returnOpcode) {
        methodVisitor.visitInsn(returnOpcode);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }

}
