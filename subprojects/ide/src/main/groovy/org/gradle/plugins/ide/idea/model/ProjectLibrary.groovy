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

import org.gradle.api.Incubating

/**
 * A project-level IDEA library.
 */
@Incubating
class ProjectLibrary {
    /**
     * The name of the library.
     */
    String name

    /**
     * A set of Jar files or directories containing compiled code.
     */
    Set<File> classes = [] as LinkedHashSet

    /**
     * A set of Jar files or directories containing Javadoc.
     */
    Set<File> javadoc = [] as LinkedHashSet

    /**
     * A set of Jar files or directories containing source code.
     */
    Set<File> sources = [] as LinkedHashSet

    void addToNode(Node parentNode, PathFactory pathFactory) {
        def builder = new NodeBuilder()

        def attributes = [name: name]

        def library = builder.library(attributes) {
            CLASSES {
                for (file in classes) {
                    root(url: pathFactory.path(file).url)
                }
            }
            JAVADOC {
                for (file in javadoc) {
                    root(url: pathFactory.path(file).url)
                }
            }
            SOURCES {
                for (file in sources) {
                    root(url: pathFactory.path(file).url)
                }
            }
        }

        parentNode.append(library)
    }

    boolean equals(Object obj) {
        if (this.is(obj)) {
            return true
        }
        if (!(obj instanceof ProjectLibrary)) {
            return false
        }

        ProjectLibrary that = (ProjectLibrary) obj

        if (classes != that.classes) {
            return false
        }
        if (javadoc != that.javadoc) {
            return false
        }
        if (name != that.name) {
            return false
        }
        if (sources != that.sources) {
            return false
        }

        return true
    }

    int hashCode() {
        int result
        result = name.hashCode()
        result = 31 * result + classes.hashCode()
        result = 31 * result + javadoc.hashCode()
        result = 31 * result + sources.hashCode()
        return result
    }
}
