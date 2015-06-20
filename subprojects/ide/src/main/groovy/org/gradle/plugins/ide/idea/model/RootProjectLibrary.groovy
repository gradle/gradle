/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.Incubating

/**
 * A root project-level IDEA library.
 */
@Incubating
class RootProjectLibrary extends ProjectLibrary {
    /**
     * The type of the library.
     */
    String type

    /**
     * A set of Jar files or directories containing compiled code.
     */
    Set<File> compilerClasses = [] as LinkedHashSet

    Node addToNode(Node parentNode, PathFactory pathFactory) {
        def library = super.addToNode(parentNode, pathFactory)
        library.attributes() << [type: type]

        def properties = library.appendNode("properties")
        def compilerClasspath = properties.appendNode("compiler-classpath")

        for (file in compilerClasses) {
            compilerClasspath.appendNode("root", [url: pathFactory.path(file).urlWithSchema("file")])
        }

        return library
    }

    boolean equals(o) {
        if (this.is(o)) {
            return true
        }
        if (getClass() != o.class) {
            return false
        }
        if (!super.equals(o)) {
            return false
        }

        RootProjectLibrary that = (RootProjectLibrary) o

        if (compilerClasses != that.compilerClasses) {
            return false
        }
        if (type != that.type) {
            return false
        }

        return true
    }

    int hashCode() {
        int result = super.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + compilerClasses.hashCode()
        return result
    }
}
