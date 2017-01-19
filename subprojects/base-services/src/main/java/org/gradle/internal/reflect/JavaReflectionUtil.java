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

import org.gradle.api.JavaVersion;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.util.CollectionUtils;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class JavaReflectionUtil {

    private static final WeakHashMap<Class<?>, ConcurrentMap<String, Boolean>> PROPERTY_CACHE = new WeakHashMap<Class<?>, ConcurrentMap<String, Boolean>>();

    /**
     * Locates the readable properties of the given type. Searches only public properties.
     */
    public static <T> Map<String, PropertyAccessor> readableProperties(Class<T> target) {
        HashMap<String, PropertyAccessor> properties = new HashMap<String, PropertyAccessor>();
        for (Method method : target.getMethods()) {
            if (method.getName().startsWith("get") && isGetter(method)) {
                String propertyName = method.getName().substring(3);
                propertyName = Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
                properties.put(propertyName, new GetterMethodBackedPropertyAccessor<T, Object>(propertyName, Object.class, method));
            } else if (method.getName().startsWith("is") && isBooleanGetter(method)) {
                String propertyName = method.getName().substring(2);
                propertyName = Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
                properties.put(propertyName, new GetterMethodBackedPropertyAccessor<T, Object>(propertyName, Object.class, method));
            }
        }
        return properties;
    }

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

    /**
     * Locates the field with the given name as a readable property.  Searches only public fields.
     */
    public static <T, F> PropertyAccessor<T, F> readableField(Class<T> target, Class<F> fieldType, String fieldName) throws NoSuchPropertyException {
        Field field = findField(target, fieldName);
        if (field == null) {
            throw new NoSuchPropertyException(String.format("Could not find field '%s' on class %s.", fieldName, target.getSimpleName()));
        }

        return new FieldBackedPropertyAccessor<T, F>(fieldName, fieldType, field);
    }

    /**
     * Locates the field with the given name as a readable property.  Searches only public fields.
     */
    public static <T, F> PropertyAccessor<T, F> readableField(T target, Class<F> fieldType, String fieldName) throws NoSuchPropertyException {
        @SuppressWarnings("unchecked")
        Class<T> targetClass = (Class<T>) target.getClass();
        return readableField(targetClass, fieldType, fieldName);
    }

    private static Method findGetterMethod(Class<?> target, String property) {
        Method[] methods = target.getMethods();
        String getter = toMethodName("get", property);
        String iser = toMethodName("is", property);
        for (Method method : methods) {
            String methodName = method.getName();
            if (getter.equals(methodName) && isGetter(method)) {
                return method;
            }
            if (iser.equals(methodName) && isBooleanGetter(method)) {
                return method;
            }
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
        PropertyMutator mutator = writeablePropertyIfExists(target, property);
        if (mutator != null) {
            return mutator;
        }
        throw new NoSuchPropertyException(String.format("Could not find setter method for property '%s' on class %s.", property, target.getSimpleName()));
    }

    /**
     * Locates the property with the given name as a writable property. Searches only public properties.
     *
     * Returns null if no such property exists.
     */
    public static PropertyMutator writeablePropertyIfExists(Class<?> target, String property) throws NoSuchPropertyException {
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
        return null;
    }

    /**
     * Locates the field with the given name as a writable property. Searches only public properties.
     *
     * @throws NoSuchPropertyException when the given property does not exist.
     */
    public static PropertyMutator writeableField(Class<?> target, String fieldName) throws NoSuchPropertyException {
        Field field = findField(target, fieldName);
        if (field != null) {
            return new FieldBackedPropertyMutator(fieldName, field);
        }
        throw new NoSuchPropertyException(String.format("Could not find writeable field '%s' on class %s.", fieldName, target.getSimpleName()));
    }

    private static Field findField(Class<?> target, String fieldName) {
        Field[] fields = target.getFields();
        for (Field field : fields) {
            if (fieldName.equals(field.getName())) {
                return field;
            }
        }
        return null;
    }

    private static String toMethodName(String prefix, String propertyName) {
        return prefix + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }

    public static Class<?> getWrapperTypeForPrimitiveType(Class<?> type) {
        if (type == Character.TYPE) {
            return Character.class;
        } else if (type == Boolean.TYPE) {
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
        throw new IllegalArgumentException(String.format("Don't know the wrapper type for primitive type %s.", type));
    }

    /**
     * Locates the given method. Searches all methods, including private methods.
     */
    public static <T, R> JavaMethod<T, R> method(Class<T> target, Class<R> returnType, String name, Class<?>... paramTypes) throws NoSuchMethodException {
        return new JavaMethod<T, R>(target, returnType, name, paramTypes);
    }

    /**
     * Locates the given static method. Searches all methods, including private methods.
     */
    public static <T, R> JavaMethod<T, R> staticMethod(Class<T> target, Class<R> returnType, String name, Class<?>... paramTypes) throws NoSuchMethodException {
        return new JavaMethod<T, R>(target, returnType, name, true, paramTypes);
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
    public static <T, R> JavaMethod<T, R> method(Class<R> returnType, Method method) throws NoSuchMethodException {
        return new JavaMethod<T, R>(returnType, method);
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
        ConcurrentMap<String, Boolean> cached;
        synchronized (PROPERTY_CACHE) {
            cached = PROPERTY_CACHE.get(targetType);
            if (cached == null) {
                cached = new ConcurrentHashMap<String, Boolean>();
                PROPERTY_CACHE.put(targetType, cached);
            }
        }
        Boolean res = cached.get(propertyName);
        if (res != null) {
            return res;
        }
        Method getterMethod = findGetterMethod(target.getClass(), propertyName);
        if (getterMethod == null) {
            if (findField(targetType, propertyName) == null) {
                cached.putIfAbsent(propertyName, false);
                return false;
            }
        }
        cached.putIfAbsent(propertyName, true);
        return true;
    }

    public static <T> T newInstanceOrFallback(String jdk7Type, ClassLoader loader, Class<? extends T> fallbackType) {
        // Use java 7 APIs, if available
        Class<?> handlerClass = null;
        if (JavaVersion.current().isJava7Compatible()) {
            try {
                handlerClass = loader.loadClass(jdk7Type);
            } catch (ClassNotFoundException e) {
                // Ignore
            }
        }
        if (handlerClass == null) {
            handlerClass = fallbackType;
        }
        try {
            return Cast.uncheckedCast(handlerClass.newInstance());
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
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

    public static <T> Factory<T> factory(final Instantiator instantiator, final Class<? extends T> type, final Object... args) {
        return new InstantiatingFactory<T>(instantiator, type, args);
    }

    public static boolean hasDefaultToString(Object object) {
        try {
            return object.getClass().getMethod("toString").getDeclaringClass() == Object.class;
        } catch (java.lang.NoSuchMethodException e) {
            throw new UncheckedException(e);
        }
    }

    private static class GetterMethodBackedPropertyAccessor<T, F> implements PropertyAccessor<T, F> {
        private final String property;
        private final Method method;
        private final Class<F> returnType;

        public GetterMethodBackedPropertyAccessor(String property, Class<F> returnType, Method method) {
            this.property = property;
            this.method = method;
            this.returnType = returnType;
        }

        @Override
        public String toString() {
            return "property " + method.getDeclaringClass().getSimpleName() + "." + property;
        }

        public String getName() {
            return property;
        }

        public Class<F> getType() {
            return returnType;
        }

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

    private static class FieldBackedPropertyAccessor<T, F> implements PropertyAccessor<T, F> {
        private final String property;
        private final Field field;
        private final Class<F> fieldType;

        public FieldBackedPropertyAccessor(String property, Class<F> fieldType, Field field) {
            this.property = property;
            this.field = field;
            this.fieldType = fieldType;
        }

        @Override
        public String getName() {
            return property;
        }

        @Override
        public Class<F> getType() {
            return fieldType;
        }

        @Override
        public F getValue(T target) {
            try {
                return fieldType.cast(field.get(target));
            } catch (IllegalAccessException e) {
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
            return "property " + method.getDeclaringClass().getSimpleName() + "." + property;
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

    private static class FieldBackedPropertyMutator implements PropertyMutator {
        private final String name;
        private final Field field;

        public FieldBackedPropertyMutator(String name, Field field) {
            this.name = name;
            this.field = field;
        }

        @Override
        public String toString() {
            return "field " + field.getDeclaringClass().getSimpleName() + "." + name;
        }

        public String getName() {
            return name;
        }

        public Class<?> getType() {
            return field.getType();
        }

        public void setValue(Object target, Object value) {
            try {
                field.set(target, value);
            } catch (IllegalAccessException e) {
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

    public static class CachedConstructor extends ReflectionCache.CachedInvokable<Constructor<?>> {
        public CachedConstructor(Constructor<?> ctor) {
            super(ctor);
        }
    }
}
