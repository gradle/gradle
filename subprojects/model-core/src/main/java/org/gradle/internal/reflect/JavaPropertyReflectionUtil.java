/*
 * Copyright 2019 the original author or authors.
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

import com.google.common.reflect.TypeToken;
import org.apache.commons.lang.reflect.MethodUtils;
import org.gradle.internal.UncheckedException;
import org.gradle.util.internal.CollectionUtils;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;

public class JavaPropertyReflectionUtil {

    private static final WeakHashMap<Class<?>, Set<String>> PROPERTY_CACHE = new WeakHashMap<Class<?>, Set<String>>();

    /**
     * Locates the property with the given name as a readable property. Searches only public properties.
     *
     * @throws NoSuchPropertyException when the given property does not exist.
     */
    public static <T, F> PropertyAccessor<T, F> readableProperty(Class<T> target, Class<F> returnType, String property) throws NoSuchPropertyException {
        final Method getterMethod = findGetterMethod(target, property);
        if (getterMethod == null) {
            throw new NoSuchPropertyException(String.format("Could not find getter method for property '%s' on class %s.", property, target.getSimpleName()));
        }
        return new GetterMethodBackedPropertyAccessor<T, F>(property, returnType, getterMethod);
    }

    /**
     * Locates the property with the given name as a readable property. Searches only public properties.
     *
     * @throws NoSuchPropertyException when the given property does not exist.
     */
    public static <T, F> PropertyAccessor<T, F> readableProperty(T target, Class<F> returnType, String property) throws NoSuchPropertyException {
        @SuppressWarnings("unchecked")
        Class<T> targetClass = (Class<T>) target.getClass();
        return readableProperty(targetClass, returnType, property);
    }

    private static Method findGetterMethod(Class<?> target, String property) {
        Method[] methods = target.getMethods();
        String getter = toMethodName("get", property);
        String iser = toMethodName("is", property);
        for (Method method : methods) {
            String methodName = method.getName();
            if (getter.equals(methodName) && PropertyAccessorType.of(method) == PropertyAccessorType.GET_GETTER) {
                return method;
            }
            if (iser.equals(methodName) && PropertyAccessorType.of(method) == PropertyAccessorType.IS_GETTER) {
                return method;
            }
        }
        return null;
    }

    /**
     * Locates the property with the given name as a writable property. Searches only public properties.
     *
     * @throws NoSuchPropertyException when the given property does not exist.
     */
    public static PropertyMutator writeableProperty(Class<?> target, String property, @Nullable Class<?> valueType) throws NoSuchPropertyException {
        PropertyMutator mutator = writeablePropertyIfExists(target, property, valueType);
        if (mutator != null) {
            return mutator;
        }
        throw new NoSuchPropertyException(String.format("Could not find setter method for property '%s' %s on class %s.",
            property, valueType == null ? "accepting null value" : "of type " + valueType.getSimpleName(), target.getSimpleName()));
    }

    /**
     * Locates the property with the given name as a writable property. Searches only public properties.
     *
     * Returns null if no such property exists.
     */
    public static PropertyMutator writeablePropertyIfExists(Class<?> target, String property, @Nullable Class<?> valueType) throws NoSuchPropertyException {
        String setterName = toMethodName("set", property);
        Method method = MethodUtils.getMatchingAccessibleMethod(target, setterName, new Class<?>[]{valueType});
        if (method != null) {
            return new MethodBackedPropertyMutator(property, method);
        }
        return null;
    }

    private static String toMethodName(String prefix, String propertyName) {
        assert !propertyName.isEmpty();
        return prefix + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }

    public static Set<String> propertyNames(Object target) {
        Class<?> targetType = target.getClass();
        synchronized (PROPERTY_CACHE) {
            Set<String> cached = PROPERTY_CACHE.get(targetType);
            if (cached == null) {
                cached = ClassInspector.inspect(targetType).getPropertyNames();
                PROPERTY_CACHE.put(targetType, cached);
            }
            return cached;
        }
    }

    @Nullable
    public static <A extends Annotation> A getAnnotation(Class<?> type, Class<A> annotationType) {
        return getAnnotation(type, annotationType, true);
    }

    @Nullable
    private static <A extends Annotation> A getAnnotation(Class<?> type, Class<A> annotationType, boolean checkType) {
        A annotation;
        if (checkType) {
            annotation = type.getAnnotation(annotationType);
            if (annotation != null) {
                return annotation;
            }
        }

        if (annotationType.getAnnotation(Inherited.class) != null) {
            for (Class<?> anInterface : type.getInterfaces()) {
                annotation = getAnnotation(anInterface, annotationType, true);
                if (annotation != null) {
                    return annotation;
                }
            }
        }

        if (type.isInterface() || type.equals(Object.class)) {
            return null;
        } else {
            return getAnnotation(type.getSuperclass(), annotationType, false);
        }
    }

    public static boolean hasDefaultToString(Object object) {
        try {
            return object.getClass().getMethod("toString").getDeclaringClass() == Object.class;
        } catch (java.lang.NoSuchMethodException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    /**
     * Resolves the return type of a method in a given class.
     *
     * For example, for {@code MyList implements List<String>}, resolving the return type of {@link List#get(int)} in {@code MyList} yields {@link String}.
     */
    public static <T> Type resolveMethodReturnType(Class<T> type, Method method) {
        Type returnType = method.getGenericReturnType();
        if (type.equals(method.getDeclaringClass())) {
            // No need to resolve type parameters if the method is from the same class.
            return returnType;
        }
        // Checking if there is a type variable to resolve, since resolving the type variable via `TypeToken` is quite expensive.
        return hasTypeVariable(returnType) ? TypeToken.of(type).method(method).getReturnType().getType() : returnType;
    }

    /**
     * Checks if a type has a type variable which may require resolving.
     */
    public static boolean hasTypeVariable(Type type) {
        // do some checks up-front, so we avoid creating the queue in most cases
        // Cases we want to handle:
        // - List<String>
        // - Class<?>
        // - List<Class<?>>
        // - Integer[]
        // - ? extends BaseType
        // - Class<?>[]
        if (doesNotHaveTypeVariable(type)) {
            return false;
        }
        if (type instanceof TypeVariable) {
            return true;
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            boolean noTypeVariables = true;
            for (Type actualTypeArgument : parameterizedType.getActualTypeArguments()) {
                if (actualTypeArgument instanceof TypeVariable) {
                    return true;
                }
                noTypeVariables &= doesNotHaveTypeVariable(actualTypeArgument);
            }
            if (noTypeVariables) {
                return false;
            }
        }
        if (type instanceof GenericArrayType) {
            GenericArrayType genericArrayType = (GenericArrayType) type;
            if (genericArrayType.getGenericComponentType() instanceof TypeVariable) {
                return true;
            }
        }

        // Type is more complicated, need to check everything.
        Queue<Type> typesToInspect = new ArrayDeque<Type>();
        typesToInspect.add(type);
        while (!typesToInspect.isEmpty()) {
            Type typeToInspect = typesToInspect.remove();
            if (typeToInspect instanceof Class) {
                continue;
            }
            if (typeToInspect instanceof TypeVariable) {
                return true;
            }
            if (typeToInspect instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) typeToInspect;
                CollectionUtils.addAll(typesToInspect, parameterizedType.getActualTypeArguments());
            } else if (typeToInspect instanceof GenericArrayType) {
                GenericArrayType arrayType = (GenericArrayType) typeToInspect;
                typesToInspect.add(arrayType.getGenericComponentType());
            } else if (typeToInspect instanceof WildcardType) {
                WildcardType wildcardType = (WildcardType) typeToInspect;
                CollectionUtils.addAll(typesToInspect, wildcardType.getLowerBounds());
                CollectionUtils.addAll(typesToInspect, wildcardType.getUpperBounds());
            } else {
                // We don't know what the type is - let Guava take care of it.
                return true;
            }
        }
        return false;
    }

    /**
     * Quick check if a type does not have any type variables.
     *
     * Handled cases:
     * - raw Class
     * - Wildcard type with Class bounds, e.g. ? extends BaseType
     */
    private static boolean doesNotHaveTypeVariable(Type type) {
        if (type instanceof Class) {
            return true;
        }
        if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            for (Type lowerBound : wildcardType.getLowerBounds()) {
                if (!(lowerBound instanceof Class)) {
                    return false;
                }
            }
            for (Type upperBound : wildcardType.getUpperBounds()) {
                if (!(upperBound instanceof Class)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static class GetterMethodBackedPropertyAccessor<T, F> implements PropertyAccessor<T, F> {
        private final String property;
        private final Method method;
        private final Class<F> returnType;

        GetterMethodBackedPropertyAccessor(String property, Class<F> returnType, Method method) {
            this.property = property;
            this.method = method;
            this.returnType = returnType;
        }

        @Override
        public String toString() {
            return "property " + method.getDeclaringClass().getSimpleName() + "." + property;
        }

        @Override
        public String getName() {
            return property;
        }

        @Override
        public Class<F> getType() {
            return returnType;
        }

        @Override
        public F getValue(T target) {
            try {
                return returnType.cast(method.invoke(target));
            } catch (InvocationTargetException e) {
                throw UncheckedException.unwrapAndRethrow(e);
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }

    private static class MethodBackedPropertyMutator implements PropertyMutator {
        private final String property;
        private final Method method;

        MethodBackedPropertyMutator(String property, Method method) {
            this.property = property;
            this.method = method;
        }

        @Override
        public String toString() {
            return "property " + method.getDeclaringClass().getSimpleName() + "." + property;
        }

        @Override
        public String getName() {
            return property;
        }

        @Override
        public Class<?> getType() {
            return method.getParameterTypes()[0];
        }

        @Override
        public void setValue(Object target, Object value) {
            try {
                method.invoke(target, value);
            } catch (InvocationTargetException e) {
                throw UncheckedException.unwrapAndRethrow(e);
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }
}
