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

import org.gradle.api.Named;

/**
 * Attribute representing the technical elements of a library variant.
 *
 * @since 5.6
 */
public interface LibraryElements extends Named {
    Attribute<LibraryElements> LIBRARY_ELEMENTS_ATTRIBUTE = Attribute.of("org.gradle.libraryelements", LibraryElements.class);

    /**
     * The JVM classes format
     */
    String CLASSES = "classes";

    /**
     * The JVM archive format
     */
    String JAR = "jar";

    /**
     * JVM resources
     */
    String RESOURCES = "resources";

    /**
     * The JVM class files and resources
     */
    String CLASSES_AND_RESOURCES = "classes+resources";

    /**
     * Header files for C++
     */
    String HEADERS_CPLUSPLUS = "headers-cplusplus";

    /**
     * Link archives for native modules
     */
    String LINK_ARCHIVE = "link-archive";

    /**
     * Objects for native modules
     */
    String OBJECTS = "objects";

    /**
     * Dynamic libraries for native modules
     */
    String DYNAMIC_LIB = "dynamic-lib";
}
