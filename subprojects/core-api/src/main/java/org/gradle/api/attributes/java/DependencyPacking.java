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
package org.gradle.api.attributes.java;

import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;

/**
 * This attribute describes how dependencies of a Java component are found.
 * There are currently 3 supported modes:
 * <ul>
 *     <li>external, the default, where transitive dependencies are found as jars</li>
 *     <li>fatjar, where dependencies are found inside the component jar, but using the same packages as the original dependencies</li>
 *     <li>shadowed, where dependencies are found inside the component jar, but repackaged in different packages to avoid name clashes</li>
 * </ul>
 *
 * @since 5.3
 */
@Incubating
public interface DependencyPacking extends Named {
    Attribute<DependencyPacking> PACKING = Attribute.of("org.gradle.dependency.packing", DependencyPacking.class);

    /**
     * The most common case: dependencies are provided as individual jars.
     */
    String EXTERNAL = "external";

    /**
     * Dependencies are packaged as a fat jar.
     */
    String FATJAR = "fatjar";

    /**
     * Dependencies are packaged as a fat jar and repackaged in order to
     * avoid package name conflicts.
     */
    String SHADOWED = "shadowed";

}
