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

package org.gradle.api.problems;

import org.gradle.api.Incubating;
import org.gradle.util.Path;

import static java.lang.String.join;

/**
 * Represents a category of problems.
 *
 * @since 8.5
 */
@Incubating
public class ProblemCategory {

    public static final int NAMESPACE_START_INDEX = 0;
    public static final String GRADLE_PLUGIN_MARKER = "gradle-plugin";
    public static final String DEPRECATION = "deprecation";
    public static final String VALIDATION = "validation";

    public static final String SEPARATOR = Path.SEPARATOR;
    protected Path category;

    public ProblemCategory(String category) {
        if (category.startsWith(SEPARATOR)) {
            throw new IllegalArgumentException("Problem category cannot start with '" + SEPARATOR + "'");
        }
        Path path = Path.path(category);
        if (path.segmentCount() < NAMESPACE_START_INDEX + 1) {
            throw new IllegalArgumentException("Problem category must have at least " + (NAMESPACE_START_INDEX + 1) + " segments");
        }
        this.category = path;
    }

    public static ProblemCategory category(String category, String... details) {
        if (details.length == 0) {
            return new ProblemCategory(category);
        }
        return new ProblemCategory(category + SEPARATOR + join(SEPARATOR, details));
    }

    public String segment(int i) {
        return category.segment(i);
    }

    public int segmentCount() {
        return category.segmentCount();
    }

    public String toString() {
        return category.toString();
    }

    public boolean hasPluginId() {
        return category.segmentCount() > NAMESPACE_START_INDEX + 1 && category.segment(NAMESPACE_START_INDEX).equals(GRADLE_PLUGIN_MARKER);
    }

    public String getPluginId() {
        return category.segment(NAMESPACE_START_INDEX + 1);
    }

    public String getNamespace() {
        return category.segment(NAMESPACE_START_INDEX);
    }
}
