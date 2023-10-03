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

import java.util.List;

/**
 * A category is a component of a {@link Problem} that helps group related problems together.
 *
 * The category string is a ":" separated string that starts either with a specific problem category or with "gradle-plugin"
 * and the plugin id where the problem is raised followed by the specific category.
 * <pre>{@code
 *  structure:
 *  <category>:<detailed-info>:<detailed-info>:...
 *  gradle-plugin:<plugin-id>:<category>:<detailed-info>:<detailed-info>:...
 * }</pre>
 *
 * <p>examples of valid category strings:
 * <pre>{@code
 * deprecation
 * gradle-plugin:deprecation
 * gradle-plugin:deprecation:<detailed-info>
 * gradle-plugin:deprecation:<detailed-info>:<detailed-info>:...
 * }</pre>
 *
 * @since 8.5
 */
@Incubating
public interface ProblemCategory {
    boolean hasPluginId();

    String getPluginId();

    String getNamespace();

    String getCategory();
    List<String> getSubCateogies();
}
