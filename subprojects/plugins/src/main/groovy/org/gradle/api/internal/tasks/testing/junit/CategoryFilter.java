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
import org.junit.experimental.categories.Category;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * This filter is used for filtering classes and methods that are annotated with the @Category annotation.
 *
 */
class CategoryFilter extends Filter {
    // the way filters are implemented makes this unnecessarily complicated,
    // buggy, and difficult to specify.  A new way of handling filters could
    // someday enable a better new implementation.
    // https://github.com/junit-team/junit/issues/172

    private final Set<Class<?>> inclusions;
    private final Set<Class<?>> exclusions;

    public CategoryFilter(final Set<Class<?>> inclusions, final Set<Class<?>> exclusions) {
        this.inclusions = inclusions;
        this.exclusions = exclusions;
    }

    @Override
    public boolean shouldRun(final Description description) {
        return shouldRun(description, description.isSuite() ? null : Description.createSuiteDescription(description.getTestClass()));
    }

    private boolean shouldRun(final Description description, final Description parent) {
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

    private boolean matches(final Class<?> category, final Set<Class<?>> categories) {
        for (Class<?> cls : categories) {
            if (cls.isAssignableFrom(category)) {
                return true;
            }
        }
        return false;
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
