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

package org.gradle.internal.inspection;

import org.gradle.internal.Cast;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.reflect.Types;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultTypeParameterInspection<INTERFACE, PARAMS> implements TypeParameterInspection<INTERFACE, PARAMS> {
    private final Class<INTERFACE> interfaceType;
    private final Class<PARAMS> paramsType;
    private final Class<? extends PARAMS> noParamsType;

    public DefaultTypeParameterInspection(Class<INTERFACE> interfaceType, Class<PARAMS> paramsType, Class<? extends PARAMS> noParamsType) {
        this.interfaceType = interfaceType;
        this.paramsType = paramsType;
        this.noParamsType = noParamsType;
    }

    /**
     * Determines the parameters type for the given implementation.
     *
     * @return The parameters type, or {@code null} when the implementation takes no parameters.
     */
    @Override
    @Nullable
    public <T extends INTERFACE, P extends PARAMS> Class<P> parameterTypeFor(Class<T> implementationType) {
        return parameterTypeFor(implementationType, 0);
    }

    /**
     * Determines the parameters type found at the given type argument index for the given implementation.
     *
     * @return The parameters type, or {@code null} when the implementation takes no parameters.
     */
    @Override
    @Nullable
    public <T extends INTERFACE, P extends PARAMS> Class<P> parameterTypeFor(Class<T> implementationType, int typeArgumentIndex) {
        if (implementationType == interfaceType) {
            return null;
        }
        Class<P> parametersType = inferParameterType(implementationType, typeArgumentIndex);
        if (parametersType == paramsType) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Could not create the parameters for ");
            formatter.appendType(implementationType);
            formatter.append(": must use a sub-type of ");
            formatter.appendType(parametersType);
            formatter.append(" as the parameters type. Use ");
            formatter.appendType(noParamsType);
            formatter.append(" as the parameters type for implementations that do not take parameters.");
            throw new IllegalArgumentException(formatter.toString());
        }
        if (parametersType == noParamsType) {
            return null;
        }
        return parametersType;
    }

    /**
     * Walk the type hierarchy until we find the interface type and keep track the chain of the type parameters.
     *
     * E.g.: For {@code interface Baz<T>}, interface {@code Bar<T extends CharSequence> extends Baz<T>} and {@code class Foo implements Bar<String>},
     * we'll have mapping {@code T extends CharSequence -> String} and {@code T -> String}.
     *
     * When we come to {@code Baz<T>}, we can then query the mapping for {@code T} and get {@code String}.
     */
    @NonNull
    private <T extends INTERFACE, P extends PARAMS> Class<P> inferParameterType(Class<T> implementationType, int typeArgumentIndex) {
        AtomicReference<Type> foundType = new AtomicReference<>();
        Map<Type, Type> collectedTypes = new HashMap<>();
        Types.walkTypeHierarchy(implementationType, type -> {
            for (Type genericInterface : type.getGenericInterfaces()) {
                if (collectTypeParameters(genericInterface, foundType, collectedTypes, typeArgumentIndex)) {
                    return Types.TypeVisitResult.TERMINATE;
                }
            }
            Type genericSuperclass = type.getGenericSuperclass();
            if (collectTypeParameters(genericSuperclass, foundType, collectedTypes, typeArgumentIndex)) {
                return Types.TypeVisitResult.TERMINATE;
            }
            return Types.TypeVisitResult.CONTINUE;
        });

        // Note: we don't handle GenericArrayType here, since
        // we don't support arrays as a type of a Parameter anywhere
        Type type = unwrapTypeVariable(foundType.get());
        return type instanceof Class
            ? Cast.uncheckedNonnullCast(type)
            : type instanceof ParameterizedType
            ? Cast.uncheckedNonnullCast(((ParameterizedType) type).getRawType())
            : Cast.uncheckedNonnullCast(paramsType);
    }

    private boolean collectTypeParameters(Type type, AtomicReference<Type> foundType, Map<Type, Type> collectedTypeParameters, int typeArgumentIndex) {
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            if (parameterizedType.getRawType().equals(interfaceType)) {
                Type parameter = parameterizedType.getActualTypeArguments()[typeArgumentIndex];
                foundType.set(collectedTypeParameters.getOrDefault(parameter, parameter));
                return true;
            }
            Type[] actualTypes = parameterizedType.getActualTypeArguments();
            Type[] typeParameters = ((Class<?>) parameterizedType.getRawType()).getTypeParameters();
            for (int i = 0; i < typeParameters.length; i++) {
                Type firstActualInTypeChain = collectedTypeParameters.getOrDefault(actualTypes[i], actualTypes[i]);
                collectedTypeParameters.put(typeParameters[i], firstActualInTypeChain);
            }
        }
        return false;
    }

    private Type unwrapTypeVariable(Type type) {
        if (type instanceof TypeVariable) {
            Type nextType;
            Queue<Type> queue = new ArrayDeque<>();
            queue.add(type);
            while ((nextType = queue.poll()) != null) {
                for (Type bound : ((TypeVariable<?>) nextType).getBounds()) {
                    if (bound instanceof TypeVariable) {
                        queue.add(bound);
                    } else if (isAssignableFromType(paramsType, bound)) {
                        return bound;
                    }
                }
            }
        }
        return type;
    }

    private static boolean isAssignableFromType(Class<?> clazz, Type type) {
        return (type instanceof Class && clazz.isAssignableFrom((Class<?>) type))
            || (type instanceof ParameterizedType && clazz.isAssignableFrom((Class<?>) ((ParameterizedType) type).getRawType()));
    }
}
