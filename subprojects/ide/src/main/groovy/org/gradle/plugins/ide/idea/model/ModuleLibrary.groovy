/*
 * Copyright 2010 the original author or authors.
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
 * Represents an orderEntry of type module-library in the iml XML.
 */
class ModuleLibrary implements Dependency {
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

    /**
     * The scope of this library. If <tt>null</tt>, the scope attribute is not added.
     */
    String scope

    /**
     * Whether the library is exported to dependent modules.
     */
    boolean exported

    def ModuleLibrary(Collection<Path> classes, Collection<Path> javadoc, Collection<Path> sources, Collection<JarDirectory> jarDirectories, String scope) {
        this.classes = classes as Set;
        this.jarDirectories = jarDirectories as Set;
        this.javadoc = javadoc as Set;
        this.sources = sources as Set;
        this.scope = scope
        this.exported = !scope || scope == 'COMPILE' || scope == 'RUNTIME'
    }

    void addToNode(Node parentNode) {
        Node libraryNode = parentNode.appendNode('orderEntry', [type: 'module-library'] + getAttributeMapForScopeAndExported()).appendNode('library')
        Node classesNode = libraryNode.appendNode('CLASSES')
        Node javadocNode = libraryNode.appendNode('JAVADOC')
        Node sourcesNode = libraryNode.appendNode('SOURCES')
        classes.each { Path path ->
            classesNode.appendNode('root', [url: path.url])
        }
        javadoc.each { Path path ->
            javadocNode.appendNode('root', [url: path.url])
        }
        sources.each { Path path ->
            sourcesNode.appendNode('root', [url: path.url])
        }
        jarDirectories.each { JarDirectory jarDirectory ->
            libraryNode.appendNode('jarDirectory', [url: jarDirectory.path.url, recursive: jarDirectory.recursive])
        }
    }

    private Map getAttributeMapForScopeAndExported() {
        return (exported ? [exported: ""] : [:]) + ((scope && scope != 'COMPILE') ? [scope: scope] : [:])
    }

    boolean equals(o) {
        if (this.is(o)) { return true }

        if (getClass() != o.class) { return false }

        ModuleLibrary that = (ModuleLibrary) o;

        if (classes != that.classes) { return false }
        if (jarDirectories != that.jarDirectories) { return false }
        if (javadoc != that.javadoc) { return false }
        if (!scopeEquals(scope, that.scope)) { return false }
        if (sources != that.sources) { return false }

        return true;
    }

    private boolean scopeEquals(String lhs, String rhs) {
        if (lhs == 'COMPILE') {
            return !rhs || rhs == 'COMPILE'
        } else if (rhs == 'COMPILE') {
            return !lhs || lhs == 'COMPILE'
        } else {
            return lhs == rhs
        }
    }


    int hashCode() {
        int result;

        result = classes.hashCode();
        result = 31 * result + jarDirectories.hashCode();
        result = 31 * result + javadoc.hashCode();
        result = 31 * result + sources.hashCode();
        result = 31 * result + getScopeHash()
        return result;
    }

    private int getScopeHash() {
        (scope && scope != 'COMPILE' ? scope.hashCode() : 0)
    }

    public String toString() {
        return "ModuleLibrary{" +
                "classes=" + classes +
                ", jarDirectories=" + jarDirectories +
                ", javadoc=" + javadoc +
                ", sources=" + sources +
                ", scope='" + scope + '\'' +
                '}';
    }
}
