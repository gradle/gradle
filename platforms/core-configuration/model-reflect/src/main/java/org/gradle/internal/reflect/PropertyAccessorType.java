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

package org.gradle.internal.reflect;

import groovy.lang.GroovySystem;

import javax.annotation.Nullable;
import java.beans.Introspector;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Collection;

/**
 * Distinguishes "get" getters, "is" getters and setters from non-property methods.
 *
 * Generally follows the JavaBean conventions, with 2 exceptions: is methods can return `Boolean` (in addition to `boolean`) and setter methods can return non-void values.
 *
 * This is essentially a superset of the conventions supported by Java, Groovy and Kotlin.
 */
public enum PropertyAccessorType {
    IS_GETTER(2) {
        @Override
        public Class<?> propertyTypeFor(Method method) {
            return method.getReturnType();
        }

        @Override
        public Type genericPropertyTypeFor(Method method) {
            return method.getGenericReturnType();
        }
    },

    GET_GETTER(3) {
        @Override
        public Class<?> propertyTypeFor(Method method) {
            return method.getReturnType();
        }

        @Override
        public Type genericPropertyTypeFor(Method method) {
            return method.getGenericReturnType();
        }
    },

    SETTER(3) {
        @Override
        public Class<?> propertyTypeFor(Method method) {
            requireSingleParam(method);
            return method.getParameterTypes()[0];
        }

        @Override
        public Type genericPropertyTypeFor(Method method) {
            requireSingleParam(method);
            return method.getGenericParameterTypes()[0];
        }

        private void requireSingleParam(Method method) {
            if (!takesSingleParameter(method)) {
                throw new IllegalArgumentException("Setter method should take one parameter: " + method);
            }
        }
    };

    private final int prefixLength;

    PropertyAccessorType(int prefixLength) {
        this.prefixLength = prefixLength;
    }

    public String propertyNameFor(Method method) {
        return propertyNameFor(method.getName());
    }

    public String propertyNameFor(String methodName) {
        String methodNamePrefixRemoved = methodName.substring(prefixLength);
        return Introspector.decapitalize(methodNamePrefixRemoved);
    }

    public abstract Class<?> propertyTypeFor(Method method);

    public abstract Type genericPropertyTypeFor(Method method);

    @Nullable
    public static PropertyAccessorType of(Method method) {
        if (isStatic(method)) {
            return null;
        }
        String methodName = method.getName();
        if (!hasVoidReturnType(method) && takesNoParameter(method)) {
            if (isGetGetterName(methodName)) {
                return GET_GETTER;
            }
            // is method that returns Boolean is not a getter according to JavaBeans, but include it for compatibility with Groovy 3
            if (isIsGetterName(methodName) && (method.getReturnType().equals(Boolean.TYPE) || (isGroovy3() && method.getReturnType().equals(Boolean.class)))) {
                return IS_GETTER;
            }
        }
        if (takesSingleParameter(method) && isSetterName(methodName)) {
            return SETTER;
        }
        return null;
    }

    /**
     * Convenience method org.gradle.util.internal.VersionNumber#parse(String) is not available, therefore check {@link GroovySystem#getVersion()} directly.
     * @return true if Groovy 3 is bundled, false otherwise
     */
    private static boolean isGroovy3() {
        return GroovySystem.getVersion().startsWith("3.");
    }

    public static PropertyAccessorType fromName(String methodName) {
        if (isGetGetterName(methodName)) {
            return GET_GETTER;
        }
        if (isIsGetterName(methodName)) {
            return IS_GETTER;
        }
        if (isSetterName(methodName)) {
            return SETTER;
        }
        return null;
    }

    private static boolean isStatic(Method method) {
        return Modifier.isStatic(method.getModifiers());
    }

    public static boolean hasVoidReturnType(Method method) {
        return void.class.equals(method.getReturnType());
    }

    public static boolean takesNoParameter(Method method) {
        return method.getParameterTypes().length == 0;
    }

    public static boolean takesSingleParameter(Method method) {
        return method.getParameterCount() == 1;
    }

    private static boolean isGetGetterName(String methodName) {
        return methodName.startsWith("get") && methodName.length() > 3;
    }

    private static boolean isIsGetterName(String methodName) {
        return methodName.startsWith("is") && methodName.length() > 2;
    }

    private static boolean isSetterName(String methodName) {
        return methodName.startsWith("set") && methodName.length() > 3;
    }

    public static boolean hasGetter(Collection<PropertyAccessorType> accessorTypes) {
        return accessorTypes.contains(GET_GETTER) || accessorTypes.contains(IS_GETTER);
    }

    public static boolean hasSetter(Collection<PropertyAccessorType> accessorTypes) {
        return accessorTypes.contains(SETTER);
    }
}
