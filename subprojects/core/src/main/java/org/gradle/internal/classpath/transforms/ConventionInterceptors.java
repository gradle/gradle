/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.classpath.transforms;

import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.initialization.ConventionInterfaceGenerator;
import org.gradle.model.internal.asm.MethodVisitorScope;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Supplier;

import static org.gradle.initialization.ConventionInterfaceGenerator.DEFAULT_DECORATED_CONVENTION_NAME;
import static org.gradle.internal.classpath.transforms.CommonTypes.OBJECT_TYPE;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Type.getMethodDescriptor;

public class ConventionInterceptors {
    static final Type PROJECT_TYPE = Type.getType(Project.class);
    static final Type CONVENTION_INTERCEPTORS_TYPE = Type.getType(ConventionInterceptors.class);
    static final String RETURN_OBJECT_FROM_PROJECT = getMethodDescriptor(OBJECT_TYPE, PROJECT_TYPE);
    static final String RETURN_CONVENTION = getMethodDescriptor(ConventionInterfaceGenerator.CONVENTION_TYPE);

    // this is executed instead of `Project.getConvention()`
    // usage is in `visitMethodInsn`
    public static Object interceptProjectGetConventionMethod(Project project) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Class<?> aClass = Class.forName(DEFAULT_DECORATED_CONVENTION_NAME, true, Thread.currentThread().getContextClassLoader());
        return aClass.getConstructor(ExtensionContainer.class).newInstance(project.getExtensions());
    }

    public boolean visitMethodInsn(MethodVisitorScope mv, String className, int opcode, String owner, String name, String descriptor, boolean isInterface, Supplier<MethodNode> readMethodNode) {
        if (opcode != INVOKEVIRTUAL && opcode != INVOKEINTERFACE) {
            return false;
        }
//      Project.getConvention -> ProjectInterceptors.interceptGetConvention()
//      This generates the code placed where Project.getConvention() is called.
        if (PROJECT_TYPE.getInternalName().equals(owner) && "getConvention".equals(name) && RETURN_CONVENTION.equals(descriptor)) {
            mv._INVOKESTATIC(CONVENTION_INTERCEPTORS_TYPE, "interceptProjectGetConventionMethod", RETURN_OBJECT_FROM_PROJECT);
            return true;
        }
        return false;
    }

}
