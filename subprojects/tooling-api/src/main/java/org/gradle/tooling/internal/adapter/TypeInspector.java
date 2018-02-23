/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.tooling.internal.adapter;

import net.jcip.annotations.ThreadSafe;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ThreadSafe
class TypeInspector {
    private final Set<Class<?>> stopAt = new HashSet<Class<?>>();
    private final Map<Class<?>, Set<Class<?>>> inspected = new HashMap<Class<?>, Set<Class<?>>>();

    public TypeInspector() {
        stopAt.add(List.class);
        stopAt.add(Set.class);
        stopAt.add(Collection.class);
        stopAt.add(Map.class);
    }

    /**
     * Returns all interfaces reachable from the given interface, including the interface itself.
     */
    public Set<Class<?>> getReachableTypes(Class<?> type) {
        Set<Class<?>> types = inspected.get(type);
        if (types == null) {
            types = new HashSet<Class<?>>();
            visit(type, types);
            inspected.put(type, types);
        }
        return types;
    }

    private void visit(Class<?> type, Set<Class<?>> types) {
        if (type.isArray()) {
            visit(type.getComponentType(), types);
            return;
        }

        if (!type.isInterface() || !types.add(type) || stopAt.contains(type)) {
            return;
        }

        for (Type superType : type.getGenericInterfaces()) {
            visit(superType, types);
        }
        for (Method method : type.getDeclaredMethods()) {
            visit(method.getGenericReturnType(), types);
            for (TypeVariable<Method> typeVariable : method.getTypeParameters()) {
                visit(typeVariable, types);
            }
        }
    }

    private void visit(Type type, Set<Class<?>> types) {
        if (type instanceof Class) {
            visit((Class) type, types);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            visit(parameterizedType.getRawType(), types);
            for (Type typeArg : parameterizedType.getActualTypeArguments()) {
                visit(typeArg, types);
            }
        } else if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            for (Type bound : wildcardType.getUpperBounds()) {
                visit(bound, types);
            }
            for (Type bound : wildcardType.getLowerBounds()) {
                visit(bound, types);
            }
        } else if (type instanceof GenericArrayType) {
            GenericArrayType arrayType = (GenericArrayType) type;
            visit(arrayType.getGenericComponentType(), types);
        } else if (type instanceof TypeVariable) {
            TypeVariable<?> typeVariable = (TypeVariable) type;
            for (Type bound : typeVariable.getBounds()) {
                visit(bound, types);
            }
        }
    }
}
