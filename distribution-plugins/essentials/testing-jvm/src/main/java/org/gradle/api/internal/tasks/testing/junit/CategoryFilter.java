/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.junit;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.InvalidUserDataException;
import org.junit.experimental.categories.Category;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * This filter is used for filtering classes and methods that are annotated with the @Category annotation.
 */
class CategoryFilter extends Filter {
    private final ClassLoader applicationClassLoader;
    private final Set<String> inclusions;
    private final Set<String> exclusions;

    CategoryFilter(final Set<String> inclusions, final Set<String> exclusions, final ClassLoader applicationClassLoader) {
        this.inclusions = inclusions;
        this.exclusions = exclusions;
        this.applicationClassLoader = applicationClassLoader;
    }

    @Override
    public boolean shouldRun(final Description description) {
        Class<?> testClass = description.getTestClass();
        verifyCategories(testClass);
        Description parent = description.isSuite() || testClass == null ? null : Description.createSuiteDescription(testClass);
        return shouldRun(description, parent);
    }

    private void verifyCategories(Class<?> testClass) {
        if (testClass == null) {
            return;
        }
        for (String cls : inclusions) {
            loadClass(testClass.getClassLoader(), cls);
        }
        for (String cls : exclusions) {
            loadClass(testClass.getClassLoader(), cls);
        }
    }

    private boolean shouldRun(final Description description, final Description parent) {
        if (hasCorrectCategoryAnnotation(description, parent)) {
            return true;
        }
        for (Description each : description.getChildren()) {
            if (shouldRun(each, description)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCorrectCategoryAnnotation(Description description, Description parent) {
        final Set<Class<?>> categories = new HashSet<Class<?>>();
        Category annotation = description.getAnnotation(Category.class);
        if (annotation != null) {
            categories.addAll(Arrays.asList(annotation.value()));
        }

        if (parent != null) {
            annotation = parent.getAnnotation(Category.class);
            if (annotation != null) {
                categories.addAll(Arrays.asList(annotation.value()));
            }
        }

        boolean result = inclusions.isEmpty();

        for (Class<?> category : categories) {
            if (matches(category, inclusions)) {
                result = true;
                break;
            }
        }

        if (result) {
            for (Class<?> category : categories) {
                if (matches(category, exclusions)) {
                    result = false;
                    break;
                }
            }
        }
        return result;
    }

    private boolean matches(final Class<?> category, final Set<String> categories) {
        ClassLoader classLoader = category.getClassLoader();
        for (String cls : categories) {
            if (loadClass(classLoader, cls).isAssignableFrom(category)) {
                return true;
            }
        }
        return false;
    }

    private Class<?> loadClass(ClassLoader classLoader, String className) {
        try {
            if (classLoader == null) {
                // some implementation uses null to represent bootstrap classloader
                // i.e. Object.class.getClassLoader()==null
                classLoader = applicationClassLoader;
            }
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new InvalidUserDataException(String.format("Can't load category class [%s].", className), e);
        }
    }

    @Override
    public final String describe() {
        StringBuilder sb = new StringBuilder();
        if (!inclusions.isEmpty()) {
            sb.append("(");
            sb.append(StringUtils.join(inclusions, " OR "));
            sb.append(")");
            if (!exclusions.isEmpty()) {
                sb.append(" AND ");
            }
        }
        if (!exclusions.isEmpty()) {
            sb.append("NOT (");
            sb.append(StringUtils.join(exclusions, " OR "));
            sb.append(")");
        }

        return sb.toString();
    }
}
