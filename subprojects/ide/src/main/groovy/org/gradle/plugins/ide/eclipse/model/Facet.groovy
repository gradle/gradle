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


class Facet {

    enum FacetType { installed, fixed }

    FacetType type
    String name
    String version

    def Facet() {
        type = FacetType.installed
    }

    def Facet(Node node) {
        this(FacetType.valueOf(node.name()), node.@facet, node.@version)
    }

    def Facet(String name, String version) {
        this(FacetType.installed, name, version)
    }

    def Facet(FacetType type, String name, String version) {
        assert type != null && name != null
        if (!type) {
            type = FacetType.installed
        }
        if (type == FacetType.installed) {
            assert version != null
        } else {
            assert version == null
        }
        this.type = type
        this.name = name
        this.version = version
    }

    void appendNode(Node node) {
        if (type == FacetType.installed) {
            node.appendNode(type as String, [facet: name, version: version])
        } else {
            node.appendNode(type as String, [facet: name])
        }
    }

    boolean equals(o) {
        if (this.is(o)) { return true }

        if (getClass() != o.class) { return false }

        Facet facet = (Facet) o;

        if (type != facet.type) { return false }
        if (name != facet.name) { return false }
        if (version != facet.version) { return false }

        return true
    }

    int hashCode() {
        int result;

        result = type.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + ((version == null) ? 0 : version.hashCode());
        return result;
    }

    public String toString() {
        return "Facet{" +
                "type='" + type + '\'' +
                "name='" + name + '\'' +
                ", version='" + version + '\'' +
                '}';
    }
}