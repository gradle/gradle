/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.util.CollectionUtils;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class JavaReflectionUtil {
    /**
     * Locates the readable properties of the given type. Searches only public properties.
     */
    public static Map<String, PropertyAccessor> readableProperties(Class<?> target) {
        HashMap<String, PropertyAccessor> properties = new HashMap<String, PropertyAccessor>();
        for (Method method : target.getMethods()) {
            if (method.getName().startsWith("get") && isGetter(method)) {
                String propertyName = method.getName().substring(3);
                propertyName = Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
                properties.put(propertyName, new GetterMethodBackedPropertyAccessor(propertyName, method));
            } else if (method.getName().startsWith("is") && isBooleanGetter(method)) {
                String propertyName = method.getName().substring(2);
                propertyName = Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
                properties.put(propertyName, new GetterMethodBackedPropertyAccessor(propertyName, method));
            }
        }
        return properties;
    }

    /**
     * Locates the property with the given name as a readable property. Searches only public properties.
     *
     * @throws NoSuchPropertyException when the given property does not exist.
     */
    public static PropertyAccessor readableProperty(Class<?> target, String property) throws NoSuchPropertyException {
        final Method getterMethod = findGetterMethod(target, property);
        if (getterMethod == null) {
            throw new NoSuchPropertyException(String.format("Could not find getter method for property '%s' on class %s.", property, target.getSimpleName()));
        }
        return new GetterMethodBackedPropertyAccessor(property, getterMethod);
    }

    private static Method findGetterMethod(Class<?> target, String property) {
        try {
            Method getterMethod = target.getMethod(toMethodName("get", property));
            if (isGetter(getterMethod)) {
                return getterMethod;
            }
        } catch (java.lang.NoSuchMethodException e) {
            // Ignore
        }
        try {
            Method getterMethod = target.getMethod(toMethodName("is", property));
            if (isBooleanGetter(getterMethod)) {
                return getterMethod;
            }
        } catch (java.lang.NoSuchMethodException e2) {
            // Ignore
        }
        return null;
    }

    private static boolean isGetter(Method method) {
        return method.getParameterTypes().length == 0 && !Modifier.isStatic(method.getModifiers()) && !method.getReturnType().equals(Void.TYPE);
    }

    private static boolean isBooleanGetter(Method method) {
        Class<?> returnType = method.getReturnType();
        return method.getParameterTypes().length == 0 && !Modifier.isStatic(method.getModifiers()) && (returnType.equals(Boolean.TYPE) || returnType.equals(Boolean.class));
    }

    /**
     * Locates the property with the given name as a writable property. Searches only public properties.
     *
     * @throws NoSuchPropertyException when the given property does not exist.
     */
    public static PropertyMutator writeableProperty(Class<?> target, String property) throws NoSuchPropertyException {
        String setterName = toMethodName("set", property);
        for (final Method method : target.getMethods()) {
            if (!method.getName().equals(setterName)) {
                continue;
            }
            if (method.getParameterTypes().length != 1) {
                continue;
            }
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            return new MethodBackedPropertyMutator(property, method);
        }
        throw new NoSuchPropertyException(String.format("Could not find setter method for property '%s' on class %s.", property, target.getSimpleName()));
    }

    private static String toMethodName(String prefix, String propertyName) {
        return prefix + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }

    public static Class<?> getWrapperTypeForPrimitiveType(Class<?> type) {
        if (type == Boolean.TYPE) {
            return Boolean.class;
        } else if (type == Long.TYPE) {
            return Long.class;
        } else if (type == Integer.TYPE) {
            return Integer.class;
        } else if (type == Short.TYPE) {
            return Short.class;
        } else if (type == Byte.TYPE) {
            return Byte.class;
        } else if (type == Float.TYPE) {
            return Float.class;
        } else if (type == Double.TYPE) {
            return Double.class;
        }
        throw new IllegalArgumentException(String.format("Don't know how wrapper type for primitive type %s.", type));
    }

    /**
     * Locates the given method. Searches all methods, including private methods.
     */
    public static <T, R> JavaMethod<T, R> method(Class<T> target, Class<R> returnType, String name, Class<?>... paramTypes) throws NoSuchMethodException {
        return new JavaMethod<T, R>(target, returnType, name, paramTypes);
    }

    /**
     * Locates the given method. Searches all methods, including private methods.
     */
    public static <T, R> JavaMethod<T, R> method(T target, Class<R> returnType, String name, Class<?>... paramTypes) throws NoSuchMethodException {
        @SuppressWarnings("unchecked")
        Class<T> targetClass = (Class<T>) target.getClass();
        return method(targetClass, returnType, name, paramTypes);
    }

    /**
     * Locates the given method. Searches all methods, including private methods.
     */
    public static <T, R> JavaMethod<T, R> method(Class<T> target, Class<R> returnType, Method method) throws NoSuchMethodException {
        return new JavaMethod<T, R>(target, returnType, method);
    }

    /**
     * Locates the given method. Searches all methods, including private methods.
     */
    public static <T, R> JavaMethod<T, R> method(T target, Class<R> returnType, Method method) throws NoSuchMethodException {
        @SuppressWarnings("unchecked")
        Class<T> targetClass = (Class<T>) target.getClass();
        return new JavaMethod<T, R>(targetClass, returnType, method);
    }

    /**
     * Search methods in an inheritance aware fashion, stopping when stopIndicator returns true.
     */
    public static void searchMethods(Class<?> target, final Transformer<Boolean, Method> stopIndicator) {
        Spec<Method> stopIndicatorAsSpec = new Spec<Method>() {
            public boolean isSatisfiedBy(Method element) {
                return stopIndicator.transform(element);
            }
        };

        findAllMethodsInternal(target, stopIndicatorAsSpec, new MultiMap<String, Method>(), new ArrayList<Method>(1), true);
    }

    public static Method findMethod(Class<?> target, Spec<Method> predicate) {
        List<Method> methods = findAllMethodsInternal(target, predicate, new MultiMap<String, Method>(), new ArrayList<Method>(1), true);
        return methods.isEmpty() ? null : methods.get(0);
    }

    public static List<Method> findAllMethods(Class<?> target, Spec<Method> predicate) {
        return findAllMethodsInternal(target, predicate, new MultiMap<String, Method>(), new ArrayList<Method>(), false);
    }

    // Not hasProperty() because that's awkward with Groovy objects implementing it
    public static boolean propertyExists(Object target, String propertyName) {
        Class<?> targetType = target.getClass();
        Method getterMethod = findGetterMethod(target.getClass(), propertyName);
        if (getterMethod == null) {
            try {
                targetType.getField(propertyName);
                return true;
            } catch (NoSuchFieldException ignore) {
                // ignore
            }
        } else {
            return true;
        }

        return false;
    }

    private static class MultiMap<K, V> extends HashMap<K, List<V>> {
        @Override
        public List<V> get(Object key) {
            if (!containsKey(key)) {
                @SuppressWarnings("unchecked") K keyCast = (K) key;
                put(keyCast, new LinkedList<V>());
            }

            return super.get(key);
        }
    }

    private static List<Method> findAllMethodsInternal(Class<?> target, Spec<Method> predicate, MultiMap<String, Method> seen, List<Method> collector, boolean stopAtFirst) {
        for (final Method method : target.getDeclaredMethods()) {
            List<Method> seenWithName = seen.get(method.getName());
            Method override = CollectionUtils.findFirst(seenWithName, new Spec<Method>() {
                public boolean isSatisfiedBy(Method potentionOverride) {
                    return potentionOverride.getName().equals(method.getName())
                            && Arrays.equals(potentionOverride.getParameterTypes(), method.getParameterTypes());
                }
            });


            if (override == null) {
                seenWithName.add(method);
                if (predicate.isSatisfiedBy(method)) {
                    collector.add(method);
                    if (stopAtFirst) {
                        return collector;
                    }
                }
            }
        }

        Class<?> parent = target.getSuperclass();
        if (parent != null) {
            return findAllMethodsInternal(parent, predicate, seen, collector, stopAtFirst);
        }

        return collector;
    }

    public static <A extends Annotation> A getAnnotation(Class<?> type, Class<A> annotationType) {
        return getAnnotation(type, annotationType, true);
    }

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

    public static boolean isClassAvailable(String className) {
        try {
            JavaReflectionUtil.class.getClassLoader().loadClass(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static <T> Factory<T> factory(final Instantiator instantiator, final Class<? extends T> type, final Object... args) {
        return new InstantiatingFactory<T>(instantiator, type, args);
    }

    private static class GetterMethodBackedPropertyAccessor implements PropertyAccessor {
        private final String property;
        private final Method method;

        public GetterMethodBackedPropertyAccessor(String property, Method method) {
            this.property = property;
            this.method = method;
        }

        @Override
        public String toString() {
            return String.format("property %s.%s", method.getDeclaringClass().getSimpleName(), property);
        }

        public String getName() {
            return property;
        }

        public Class<?> getType() {
            return method.getClass();
        }

        public Object getValue(Object target) {
            try {
                return method.invoke(target);
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

        public MethodBackedPropertyMutator(String property, Method method) {
            this.property = property;
            this.method = method;
        }

        @Override
        public String toString() {
            return String.format("property %s.%s", method.getDeclaringClass().getSimpleName(), property);
        }

        public String getName() {
            return property;
        }

        public Class<?> getType() {
            return method.getParameterTypes()[0];
        }

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

    private static class InstantiatingFactory<T> implements Factory<T> {
        private final Instantiator instantiator;
        private final Class<? extends T> type;
        private final Object[] args;

        public InstantiatingFactory(Instantiator instantiator, Class<? extends T> type, Object... args) {
            this.instantiator = instantiator;
            this.type = type;
            this.args = args;
        }

        public T create() {
            return instantiator.newInstance(type, args);
        }
    }
}
