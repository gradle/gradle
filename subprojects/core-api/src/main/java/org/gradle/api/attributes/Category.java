/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.attributes;

import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.internal.scan.UsedByScanPlugin;

/**
 * This attribute describes the categories of variants for a given module.
 * <p>
 * Three values are found in published components:
 * <ul>
 *     <li>{@code library}: Indicates that the variant is a library, that usually means a binary and a set of dependencies</li>
 *     <li>{@code platform}: Indicates that the variant is a platform, that usually means a definition of dependency constraints</li>
 *     <li>{@code documentation}: Indicates that the variant is documentation of the software module</li>
 *     <li>{@code verification}: Indicates that the variant is output from a testing or code quality verification tool</li>
 * </ul>
 * One value is used for derivation. A {@code platform} variant can be consumed as a {@code enforced-platform} which means all the dependency
 * information it provides is applied as {@code forced}.
 *
 * @since 5.3
 */
@UsedByScanPlugin
public interface Category extends Named {

    Attribute<Category> CATEGORY_ATTRIBUTE = Attribute.of("org.gradle.category", Category.class);

    /**
     * The library category
     */
    String LIBRARY = "library";

    /**
     * The platform category
     */
    String REGULAR_PLATFORM = "platform";

    /**
     * The enforced platform, usually a synthetic variant derived from the {@code platform}
     */
    String ENFORCED_PLATFORM = "enforced-platform";

    /**
     * The documentation category
     *
     * @since 5.6
     */
    String DOCUMENTATION = "documentation";

    /**
     * The verification category, for variants which contain the results of running verification tasks (e.g. Test, Jacoco).
     * <p>
     * Note that this category <strong>can not be published</strong>, and an error will result if any attempt is made to publish a configuration containing 'org.gradle.category=verification'.
     *
     * @since 7.4
     */
    @Incubating
    String VERIFICATION = "verification";
}
