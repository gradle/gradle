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

/**
 * This attribute describes how dependencies of a component are found.
 * There are currently 3 supported modes:
 * <ul>
 *     <li>{@code external}, the default, where dependencies, if any, are found transitively</li>
 *     <li>{@code embedded}, where dependencies are found inside the component, but using the same namespace as the original dependencies</li>
 *     <li>{@code shadowed}, where dependencies are found inside the component, but within a different namespace to avoid name clashes</li>
 * </ul>
 * <p>
 * As a practical example, let's consider the Java ecosystem:
 * <ul>
 *     <li>
 *         Jar component:
 *         <ul>
 *             <li>{@code external} indicates that transitive dependencies are themselves component jars</li>
 *             <li>{@code embedded} indicates that transitive dependencies have been included inside the component jar, without modifying their packages</li>
 *             <li>{@code shadowed} indicates that transitive dependencies have been included inside the component jar, under different packages to prevent conflicts</li>
 *         </ul>
 *     </li>
 *     <li>
 *         Sources component:
 *         <ul>
 *             <li>{@code external} indicates that the source of transitive dependencies are themselves source jars</li>
 *             <li>{@code embedded} indicates that the source of transitive dependencies have been included inside the component source jar, without modifying their packages</li>
 *             <li>{@code shadowed} indicates that the source of transitive dependencies have been included inside the component source jar, under different packages</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * @since 5.3
 */
@Incubating
public interface Bundling extends Named {
    Attribute<Bundling> BUNDLING_ATTRIBUTE = Attribute.of("org.gradle.dependency.bundling", Bundling.class);

    /**
     * The most common case: dependencies are provided as individual components.
     */
    String EXTERNAL = "external";

    /**
     * Dependencies are packaged <i>within</i> the main component artifact.
     */
    String EMBEDDED = "embedded";

    /**
     * Dependencies are packaged <i>within</i> the main component artifact
     * but also in a different namespace to prevent conflicts.
     */
    String SHADOWED = "shadowed";

}
