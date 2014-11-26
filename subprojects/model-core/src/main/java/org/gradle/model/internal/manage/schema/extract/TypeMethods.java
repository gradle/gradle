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

package org.gradle.model.internal.manage.schema.extract;

import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.*;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Iterator;

public class TypeMethods implements Iterable<Method> {

    private final ImmutableListMultimap<String, Method> methodsByName;

    private final ImmutableSet<String> overloadedMethodNames;

    public static TypeMethods of(ModelType<?> type) {
        return new TypeMethods(type.getRawClass().getMethods());
    }

    public TypeMethods(Method[] methods) {
        methodsByName = Multimaps.index(Arrays.asList(methods), new Function<Method, String>() {
            public String apply(Method method) {
                return method.getName();
            }
        });
        overloadedMethodNames = findOverloadedMethodNames();
    }

    private ImmutableSet<String> findOverloadedMethodNames() {
        Iterable<String> overloadedMethods = Iterables.filter(methodsByName.keySet(), new Predicate<String>() {
            public boolean apply(String input) {
                return CollectionUtils.dedup(methodsByName.get(input), new MethodSignatureEquivalence()).size() > 1;
            }
        });
        return ImmutableSet.copyOf(overloadedMethods);
    }

    public ImmutableSet<String> getOverloadedMethodNames() {
        return overloadedMethodNames;
    }

    public boolean isEmpty() {
        return methodsByName.isEmpty();
    }

    public ImmutableSet<? extends String> getNames() {
        return methodsByName.keySet();
    }

    public ImmutableList<Method> get(String methodName) {
        return methodsByName.get(methodName);
    }

    public Class<?>[] getParameterTypes(String methodName) {
        return getFirst(methodName).getParameterTypes();
    }

    public Method getFirst(String methodName) {
        return methodsByName.get(methodName).get(0);
    }

    public Type getGenericReturnType(String methodName) {
        return getFirst(methodName).getGenericReturnType();
    }

    public Iterator<Method> iterator() {
        return methodsByName.values().iterator();
    }

    public boolean hasMethod(String setterName) {
        return methodsByName.containsKey(setterName);
    }

    public ImmutableSet<ModelType<?>> getDeclaredBy(String methodName) {
        Iterable<ModelType<?>> declaredBy = Iterables.transform(methodsByName.get(methodName), new Function<Method, ModelType<?>>() {
            public ModelType<?> apply(Method method) {
                return ModelType.of(method.getDeclaringClass());
            }
        });
        return ImmutableSet.copyOf(declaredBy);
    }

    private static class MethodSignatureEquivalence extends Equivalence<Method> {

        @Override
        protected boolean doEquivalent(Method a, Method b) {
            return new EqualsBuilder()
                    .append(a.getName(), b.getName())
                    .append(a.getGenericReturnType(), b.getGenericReturnType())
                    .append(a.getGenericParameterTypes(), b.getGenericParameterTypes())
                    .isEquals();
        }

        @Override
        protected int doHash(Method method) {
            return new HashCodeBuilder()
                    .append(method.getName())
                    .append(method.getGenericReturnType())
                    .append(method.getGenericParameterTypes())
                    .toHashCode();
        }
    }
}
