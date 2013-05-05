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
package org.gradle.messaging.remote.internal.protocol;

import org.gradle.internal.UncheckedException;
import org.gradle.messaging.remote.internal.Message;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;

public class MethodMetaInfo extends Message {
    private final Type type;
    private final String methodName;
    private final Type[] paramTypes;
    private final Object key;

    public MethodMetaInfo(Object key, Method method) {
        this.key = key;
        type = new Type(method.getDeclaringClass());
        methodName = method.getName();
        paramTypes = new Type[method.getParameterTypes().length];
        for (int i = 0; i < method.getParameterTypes().length; i++) {
            Class<?> paramType = method.getParameterTypes()[i];
            paramTypes[i] = new Type(paramType);
        }
    }

    @Override
    public String toString() {
        return "MethodMetaInfo{"
                + "type=" + type
                + ", methodName='" + methodName + '\''
                + ", paramTypes=" + (paramTypes == null ? null : Arrays.asList(paramTypes))
                + '}';
    }

    public Object getKey() {
        return key;
    }

    public Method findMethod(ClassLoader classLoader) {
        try {
            Class<?> declaringClass = this.type.load(classLoader);
            Class<?>[] paramTypes = new Class[this.paramTypes.length];
            for (int i = 0; i < this.paramTypes.length; i++) {
                Type paramType = this.paramTypes[i];
                paramTypes[i] = paramType.load(classLoader);
            }
            return declaringClass.getMethod(methodName, paramTypes);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }

        MethodMetaInfo other = (MethodMetaInfo) obj;
        if (!key.equals(other.key)) {
            return false;
        }
        if (!type.equals(other.type)) {
            return false;
        }
        if (!methodName.equals(other.methodName)) {
            return false;
        }
        return Arrays.equals(paramTypes, other.paramTypes);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    private static class Type implements Serializable {
        private String typeName;
        private Class<?> type;

        public Type(Class<?> type) {
            this.typeName = type.getName();
            if (type.isPrimitive()) {
                this.type = type;
            }
        }

        Class<?> load(ClassLoader classLoader) throws ClassNotFoundException {
            if (type != null) {
                return type;
            }
            return classLoader.loadClass(typeName);
        }

        @Override
        public boolean equals(Object obj) {
            return ((Type) obj).typeName.equals(typeName);
        }

        @Override
        public int hashCode() {
            return typeName.hashCode();
        }

        @Override
        public String toString() {
            return "Type{"
                    + "typeName='" + typeName + '\''
                    + '}';
        }
    }
}
