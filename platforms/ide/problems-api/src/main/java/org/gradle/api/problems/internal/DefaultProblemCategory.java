/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems.internal;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import org.gradle.api.problems.ProblemCategory;
import org.gradle.util.Path;
import org.gradle.util.internal.CollectionUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Default implementation for {@link ProblemCategory}
 */
public class DefaultProblemCategory implements ProblemCategory {

    public static final String GRADLE_PLUGIN_MARKER = "gradle-plugin";
    public static final String DEPRECATION = "deprecation";
    public static final String VALIDATION = "validation";

    public static final String SEPARATOR = Path.SEPARATOR;

    private final String namespace;
    private final String category;
    private final List<String> subcategories;

    private  DefaultProblemCategory(String namespace, String category, String... subcategories) {
        this.namespace = namespace;
        this.category = category;
        this.subcategories = Arrays.asList(subcategories);
    }
    public static DefaultProblemCategory category(String namespace, String category, String... subcategories) {
        return new DefaultProblemCategory(namespace, category, subcategories);
    }

    public String segment(int i) {
        switch (i) {
            case 0: return namespace;
            case 1: return category;
            default: return subcategories.get(i - 2);
        }
    }

    public int segmentCount() {
        return subcategories.size() + 2;
    }

    public String toString() {
        return namespace + SEPARATOR + category + CollectionUtils.join(SEPARATOR, subcategories);
    }

    @Override
    public boolean hasPluginId() {
        return namespace.startsWith(GRADLE_PLUGIN_MARKER);
    }

    @Override
    public String getPluginId() {
        return namespace.substring(GRADLE_PLUGIN_MARKER.length() + 1); // TODO (donat) add static factories for gradle core and gradle plugin categories
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public String getCategory() {
        return category;
    }

    @Override
    public List<String> getSubCategories() {
        return ImmutableList.copyOf(subcategories);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultProblemCategory that = (DefaultProblemCategory) o;
        return Objects.equal(namespace, that.namespace) && Objects.equal(category, that.category) && Objects.equal(subcategories, that.subcategories);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(namespace, category, subcategories);
    }
}
