/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.plugins.ide.idea.model

/**
 * An IDEA library configured at the project or module level.
 */
abstract class Library implements Dependency {
    /**
     * A set of Jar files or directories containing compiled code.
     */
    Set<Path> classes = [] as LinkedHashSet

    /**
     * A set of directories containing Jar files.
     */
    Set<JarDirectory> jarDirectories = [] as LinkedHashSet

    /**
     * A set of Jar files or directories containing Javadoc.
     */
    Set<Path> javadoc = [] as LinkedHashSet

    /**
     * A set of Jar files or directories containing source code.
     */
    Set<Path> sources = [] as LinkedHashSet
}
