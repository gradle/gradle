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

package org.gradle.model.internal.manage.schema.extract;

import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import com.google.common.collect.*;
import groovy.lang.GroovyObject;
import org.gradle.api.Nullable;
import org.gradle.internal.Cast;
import org.gradle.model.Managed;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.gradle.internal.reflect.Methods.SIGNATURE_EQUIVALENCE;

public class ModelSchemaUtils {

    private static final Set<Equivalence.Wrapper<Method>> IGNORED_METHODS = ImmutableSet.copyOf(
        Iterables.transform(
            Iterables.concat(
                Arrays.asList(Object.class.getMethods()),
                Arrays.asList(GroovyObject.class.getMethods())
            ), new Function<Method, Equivalence.Wrapper<Method>>() {
                public Equivalence.Wrapper<Method> apply(@Nullable Method input) {
                    return SIGNATURE_EQUIVALENCE.wrap(input);
                }
            }
        )
    );

    /**
     * Returns all candidate methods for schema generation declared by the given type and its super-types indexed by name.
     *
     * <p>Overriding methods are <em>not</em> folded like in the case of {@link Class#getMethods()}. This allows
     * the caller to identify annotations declared at different levels in the hierarchy, and also to identify all
     * the classes declaring a certain method.</p>
     *
     * <p>Method candidates exclude:</p>
     * <ul>
     *     <li>methods defined by {@link Object} and their overrides</li>
     *     <li>methods defined by {@link GroovyObject} and their overrides</li>
     *     <li>synthetic methods</li>
     * </ul>
     *
     * <p>Methods are returned in the order of their specialization, most specialized methods first.</p>
     */
    public static <T> CandidateMethods getCandidateMethods(Class<T> clazz) {
        final ImmutableListMultimap.Builder<String, Method> methodsByNameBuilder = ImmutableListMultimap.builder();
        ModelSchemaUtils.walkTypeHierarchy(clazz, new ModelSchemaUtils.TypeVisitor<T>() {
            @Override
            public void visitType(Class<? super T> type) {
                Method[] declaredMethods = type.getDeclaredMethods();
                // Sort of determinism
                Arrays.sort(declaredMethods, Ordering.usingToString());
                for (Method method : declaredMethods) {
                    if (ModelSchemaUtils.isIgnoredMethod(method)) {
                        continue;
                    }
                    methodsByNameBuilder.put(method.getName(), method);
                }
            }
        });
        ImmutableListMultimap<String, Method> methodsByName = methodsByNameBuilder.build();
        ImmutableSortedMap.Builder<String, Map<Equivalence.Wrapper<Method>, Collection<Method>>> candidatesBuilder = ImmutableSortedMap.naturalOrder();
        for (String methodName : methodsByName.keySet()) {
            ImmutableList<Method> methodsWithSameName = methodsByName.get(methodName);
            ListMultimap<Equivalence.Wrapper<Method>, Method> equivalenceIndex = Multimaps.index(methodsWithSameName, new Function<Method, Equivalence.Wrapper<Method>>() {
                @Override
                public Equivalence.Wrapper<Method> apply(Method method) {
                    return SIGNATURE_EQUIVALENCE.wrap(method);
                }
            });
            candidatesBuilder.put(methodName, equivalenceIndex.asMap());
        }
        return new CandidateMethods(candidatesBuilder.build());
    }

    public static boolean isIgnoredMethod(Method method) {
        int modifiers = method.getModifiers();
        if (method.isSynthetic() || Modifier.isStatic(modifiers)) {
            return true;
        }

        // Ignore overrides of Object and GroovyObject methods
        return isObjectMethod(method);
    }

    /**
     * Is defined by Object or GroovyObject?
     */
    public static boolean isObjectMethod(Method method) {
        return IGNORED_METHODS.contains(SIGNATURE_EQUIVALENCE.wrap(method));
    }

    /**
     * Visits all types in a type hierarchy in breadth-first order, super-classes first and then implemented interfaces.
     *
     * @param clazz the type of whose type hierarchy to visit.
     * @param visitor the visitor to call for each type in the hierarchy.
     */
    public static <T> void walkTypeHierarchy(Class<T> clazz, TypeVisitor<? extends T> visitor) {
        Set<Class<?>> seenInterfaces = Sets.newHashSet();
        Queue<Class<? super T>> queue = new ArrayDeque<Class<? super T>>();
        queue.add(clazz);
        Class<? super T> type;
        while ((type = queue.poll()) != null) {
            // Do not process Object's or GroovyObject's methods
            if (type.equals(Object.class) || type.equals(GroovyObject.class)) {
                continue;
            }

            visitor.visitType(type);

            Class<? super T> superclass = type.getSuperclass();
            if (superclass != null) {
                queue.add(superclass);
            }
            for (Class<?> iface : type.getInterfaces()) {
                if (seenInterfaces.add(iface)) {
                    queue.add(Cast.<Class<? super T>>uncheckedCast(iface));
                }
            }
        }
    }

    public interface TypeVisitor<T> {
        void visitType(Class<? super T> type);
    }

    /**
     * Tries to find the most specific declaration of a method that is not declared in a {@link Proxy} class.
     * Mock objects generated via {@link Proxy#newProxyInstance(ClassLoader, Class[], java.lang.reflect.InvocationHandler)}
     * lose their generic type parameters and can confuse schema extraction. This way we can ignore these
     * declarations, and use the ones from the proxied interfaces instead.
     *
     * @param declaringMethods declarations of the same method from different types in the type hierarchy. They are
     *      expected to be in order of specificity, i.e. overrides preceding overridden declarations.
     * @return the most specific declaration of the method.
     * @throws IllegalArgumentException if no declaration can be found.
     */
    public static Method findMostSpecificMethod(Iterable<Method> declaringMethods) {
        for (Method method : declaringMethods) {
            if (Proxy.isProxyClass(method.getDeclaringClass())) {
                continue;
            }
            return method;
        }
        throw new IllegalArgumentException("Cannot find most-specific declaration of method. Declarations checked: " + declaringMethods);
    }

    /**
     * Returns whether the most specific of the given methods has been declared in a <code>@</code>{@link Managed} type or not.
     */
    public static boolean isMethodDeclaredInManagedType(Iterable<Method> declarations) {
        Method mostSpecificDeclaration = findMostSpecificMethod(declarations);
        return isMethodDeclaredInManagedType(mostSpecificDeclaration);
    }

    /**
     * Returns whether the method has been declared in a <code>@</code>{@link Managed} type or not.
     */
    public static boolean isMethodDeclaredInManagedType(Method method) {
        return method.getDeclaringClass().isAnnotationPresent(Managed.class);
    }
}
