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
 * Represents an orderEntry of type module in the iml XML.
 */
class ModuleDependency implements Dependency {
    /**
     * The name of the module the module depends on. Must not be null.
     */
    String name

    /**
     * The scope for this dependency. If null the scope attribute is not added.
     */
    String scope

    boolean exported

    def ModuleDependency(name, scope) {
        this.name = name;
        this.scope = scope;
        this.exported = !scope || scope == 'COMPILE' || scope == 'RUNTIME'
    }

    void addToNode(Node parentNode) {
        parentNode.appendNode('orderEntry', [type: 'module', 'module-name': name] + getAttributeMapForScopeAndExported())
    }

    private Map getAttributeMapForScopeAndExported() {
        return (exported ? [exported: ""] : [:]) + ((scope && scope != 'COMPILE') ? [scope: scope] : [:])
    }

    boolean equals(o) {
        if (this.is(o)) { return true }

        if (getClass() != o.class) { return false }

        ModuleDependency that = (ModuleDependency) o;

        if (name != that.name) { return false }
        if (!scopeEquals(scope, that.scope)) { return false }

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

        result = name.hashCode();
        result = 31 * result + getScopeHash();
        return result;
    }

    private int getScopeHash() {
        (scope && scope != 'COMPILE' ? scope.hashCode() : 0)
    }

    public String toString() {
        return "ModuleDependency{" +
                "name='" + name + '\'' +
                ", scope='" + scope + '\'' +
                '}';
    }
}
