/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.catalog;

import groovy.lang.MissingPropertyException;
import org.gradle.api.internal.project.ProjectPathSuggestions;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Suggests existing type-safe project accessors close to a missing one.
 */
final class ProjectAccessorSuggestions {

    private ProjectAccessorSuggestions() {
    }

    /**
     * Creates the exception for a missing property of a project accessor.
     * <p>
     * A {@link MissingPropertyException} for the same property is used, so that Groovy can still fall back to
     * other lookups, for example to the owner of a closure configuring a project dependency.
     * As such lookups succeed, the suggestions are only computed when the message is read.
     *
     * @param rootType the type of the root accessor
     * @param receiverType the type of the accessor the property is missing from
     */
    static MissingPropertyException unknownAccessor(Class<?> rootType, Class<?> receiverType, String name) {
        return new UnknownProjectAccessorException(rootType, receiverType, name);
    }

    private static final class UnknownProjectAccessorException extends MissingPropertyException {
        private final Class<?> rootType;
        private transient @Nullable String message;

        UnknownProjectAccessorException(Class<?> rootType, Class<?> receiverType, String name) {
            super(name, receiverType);
            this.rootType = rootType;
        }

        @Override
        public String getMessageWithoutLocationText() {
            if (message == null) {
                message = formatMessage(rootType, getType(), getProperty());
            }
            return message;
        }
    }

    private static String formatMessage(Class<?> rootType, Class<?> receiverType, String name) {
        Map<Class<?>, String> accessors = accessorsOf(rootType);
        String receiver = accessors.get(receiverType);
        String requested = receiver == null ? name : receiver + "." + name;
        return String.format("Project accessor '%s' could not be found.", requested)
            + ProjectPathSuggestions.formatSuggestions(requested, accessors.values());
    }

    /**
     * Returns the shortest accessor chain of each accessor type reachable from the root, such as {@code app.child}.
     */
    private static Map<Class<?>, String> accessorsOf(Class<?> rootType) {
        Map<Class<?>, String> accessors = new LinkedHashMap<>();
        Deque<Class<?>> queue = new ArrayDeque<>();
        queue.add(rootType);
        while (!queue.isEmpty()) {
            Class<?> type = queue.remove();
            String prefix = accessors.containsKey(type) ? accessors.get(type) + "." : "";
            for (Method method : type.getMethods()) {
                Class<?> accessorType = method.getReturnType();
                if (isAccessor(method) && !accessors.containsKey(accessorType)) {
                    accessors.put(accessorType, prefix + propertyName(method.getName()));
                    queue.add(accessorType);
                }
            }
        }
        return accessors;
    }

    private static boolean isAccessor(Method method) {
        return method.getParameterCount() == 0
            && method.getName().length() > 3
            && method.getName().startsWith("get")
            && DelegatingProjectDependency.class.isAssignableFrom(method.getReturnType());
    }

    private static String propertyName(String getterName) {
        String name = getterName.substring(3);
        // Groovy keeps names starting with two upper case letters as is, like 'ABc' for 'getABc'
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
