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
package org.gradle.plugins.ide.eclipse.model

/**
 * @author Hans Dockter
 */

class Facet {
    String name
    String version

    def Facet() {
    }

    def Facet(Node node) {
        this(node.@facet, node.@version)
    }

    def Facet(String name, String version) {
        assert name != null && version != null
        this.name = name
        this.version = version
    }

    void appendNode(Node node) {
        node.appendNode("installed", [facet: name, version: version])
    }

    boolean equals(o) {
        if (this.is(o)) { return true }

        if (getClass() != o.class) { return false }

        Facet facet = (Facet) o;

        if (name != facet.name) { return false }
        if (version != facet.version) { return false }

        return true
    }

    int hashCode() {
        int result;

        result = name.hashCode();
        result = 31 * result + version.hashCode();
        return result;
    }

    public String toString() {
        return "Facet{" +
                "name='" + name + '\'' +
                ", version='" + version + '\'' +
                '}';
    }
}