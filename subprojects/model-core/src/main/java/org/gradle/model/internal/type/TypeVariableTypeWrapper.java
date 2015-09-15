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

package org.gradle.model.internal.type;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.security.AccessControlException;
import java.util.Arrays;

/**
 * Wrapper for a {@link TypeVariable}, designed to be used only to represent a type variable in a
 * {@link org.gradle.model.internal.manage.schema.cache.WeakClassSet}. It ignores annotations on
 * on the type variable, and throws an {@link UnsupportedOperationException} if
 * {@link #getGenericDeclaration()} is called.
 */
class TypeVariableTypeWrapper<D extends GenericDeclaration> implements TypeWrapper {
    private static final Class<?>[] TYPE_VARIABLE_INTERFACE = {TypeVariable.class};

    private final String name;
    private final TypeWrapper[] bounds;
    private final int hashCode;

    public TypeVariableTypeWrapper(String name, TypeWrapper[] bounds, int hashCode) {
        this.name = name;
        this.bounds = bounds;
        this.hashCode = hashCode;
    }

    @Override
    public Type unwrap() {
        return (Type) Proxy.newProxyInstance(getClass().getClassLoader(), TYPE_VARIABLE_INTERFACE, new TypeVariableInvocationHandler(this));
    }

    @Override
    public void collectClasses(ImmutableList.Builder<Class<?>> builder) {
        for (TypeWrapper bound : bounds) {
            bound.collectClasses(builder);
        }
    }

    @Override
    public String getRepresentation(boolean full) {
        return name;
    }

    public String getName() {
        return name;
    }

    public Type[] getBounds() {
        return ModelType.unwrap(bounds);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TypeVariable)) {
            return false;
        } else {
            TypeVariable<?> var2 = (TypeVariable<?>) o;
            return Objects.equal(this.getName(), var2.getName())
                && Arrays.equals(this.getBounds(), var2.getBounds());
        }
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return false;
    }

    public Annotation[] getDeclaredAnnotations() {
        return new Annotation[0];
    }

    public Annotation[] getAnnotations() {
        return new Annotation[0];
    }

    public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
        return null;
    }

    public D getGenericDeclaration() {
        throw new UnsupportedOperationException();
    }

    // This is taken from Google Guava: https://github.com/google/guava/blob/master/guava/src/com/google/common/reflect/Types.java
    /*
     * Invocation handler to work around a compatibility problem between Java 7 and Java 8.
     *
     * <p>Java 8 introduced a new method {@code getAnnotatedBounds()} in the {@link TypeVariable}
     * interface, whose return type {@code AnnotatedType[]} is also new in Java 8. That means that we
     * cannot implement that interface in source code in a way that will compile on both Java 7 and
     * Java 8. If we include the {@code getAnnotatedBounds()} method then its return type means
     * it won't compile on Java 7, while if we don't include the method then the compiler will
     * complain that an abstract method is unimplemented. So instead we use a dynamic proxy to
     * get an implementation. If the method being called on the {@code TypeVariable} instance has
     * the same name as one of the public methods of {@link TypeVariableImpl}, the proxy calls
     * the same method on its instance of {@code TypeVariableImpl}. Otherwise it throws {@link
     * UnsupportedOperationException}; this should only apply to {@code getAnnotatedBounds()}. This
     * does mean that users on Java 8 who obtain an instance of {@code TypeVariable} from {@link
     * TypeResolver#resolveType} will not be able to call {@code getAnnotatedBounds()} on it, but that
     * should hopefully be rare.
     *
     * <p>This workaround should be removed at a distant future time when we no longer support Java
     * versions earlier than 8.
     */
    private static final class TypeVariableInvocationHandler implements InvocationHandler {
        private static final ImmutableMap<String, Method> TYPE_VARIABLE_METHODS;

        static {
            ImmutableMap.Builder<String, Method> builder = ImmutableMap.builder();
            for (Method method : TypeVariableTypeWrapper.class.getMethods()) {
                if (method.getDeclaringClass().equals(TypeVariableTypeWrapper.class)) {
                    try {
                        method.setAccessible(true);
                    } catch (AccessControlException e) {
                        // OK: the method is accessible to us anyway. The setAccessible call is only for
                        // unusual execution environments where that might not be true.
                    }
                    builder.put(method.getName(), method);
                }
            }
            TYPE_VARIABLE_METHODS = builder.build();
        }

        private final TypeVariableTypeWrapper<?> wrapper;

        TypeVariableInvocationHandler(TypeVariableTypeWrapper<?> wrapper) {
            this.wrapper = wrapper;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            Method typeVariableMethod = TYPE_VARIABLE_METHODS.get(methodName);
            if (typeVariableMethod == null) {
                throw new UnsupportedOperationException(methodName);
            } else {
                try {
                    return typeVariableMethod.invoke(wrapper, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        }
    }
}
