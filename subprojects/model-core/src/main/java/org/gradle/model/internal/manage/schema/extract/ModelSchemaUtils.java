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
import org.gradle.internal.reflect.MethodSignatureEquivalence;
import org.gradle.model.Managed;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.*;

public class ModelSchemaUtils {
    private static final Equivalence<Method> METHOD_EQUIVALENCE = new MethodSignatureEquivalence();

    private static final Set<Equivalence.Wrapper<Method>> IGNORED_METHODS = ImmutableSet.copyOf(
        Iterables.transform(
            Iterables.concat(
                Arrays.asList(Object.class.getMethods()),
                Arrays.asList(GroovyObject.class.getMethods())
            ), new Function<Method, Equivalence.Wrapper<Method>>() {
                public Equivalence.Wrapper<Method> apply(@Nullable Method input) {
                    return METHOD_EQUIVALENCE.wrap(input);
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
    public static Multimap<String, Method> getCandidateMethods(Class<?> clazz) {
        final ImmutableListMultimap.Builder<String, Method> methodsBuilder = ImmutableListMultimap.builder();
        walkTypeHierarchy(clazz, new TypeVisitor() {
            @Override
            public void visitType(Class<?> type) {
                for (Method method : type.getDeclaredMethods()) {
                    int modifiers = method.getModifiers();
                    if (method.isSynthetic() || Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers)) {
                        continue;
                    }

                    // Ignore overrides of Object and GroovyObject methods
                    if (IGNORED_METHODS.contains(METHOD_EQUIVALENCE.wrap(method))) {
                        continue;
                    }

                    methodsBuilder.put(method.getName(), method);
                }
            }
        });
        return methodsBuilder.build();
    }

    /**
     * Visits all types in a type hierarchy in breadth-first order, super-classes first and then implemented interfaces.
     *
     * @param clazz the type of whose type hierarchy to visit.
     * @param visitor the visitor to call for each type in the hierarchy.
     */
    public static void walkTypeHierarchy(Class<?> clazz, TypeVisitor visitor) {
        Set<Class<?>> seenInterfaces = Sets.newHashSet();
        Queue<Class<?>> queue = new ArrayDeque<Class<?>>();
        queue.add(clazz);
        Class<?> type;
        while ((type = queue.poll()) != null) {
            // Do not process Object's or GroovyObject's methods
            if (type.equals(Object.class) || type.equals(GroovyObject.class)) {
                continue;
            }

            visitor.visitType(type);

            Class<?> superclass = type.getSuperclass();
            if (superclass != null) {
                queue.add(superclass);
            }
            for (Class<?> iface : type.getInterfaces()) {
                if (seenInterfaces.add(iface)) {
                    queue.add(iface);
                }
            }
        }
    }

    public interface TypeVisitor {
        void visitType(Class<?> type);
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

    /**
     * Returns the different overloaded versions of a method, or null if there are no overloads.
     */
    @Nullable
    public static List<Method> getOverloadedMethods(Collection<Method> methods) {
        if (methods.size() > 1) {
            List<Method> deduped = CollectionUtils.dedup(methods, METHOD_EQUIVALENCE);
            if (deduped.size() > 1) {
                return deduped;
            }
        }
        return null;
    }
}
