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
package org.gradle.plugins.ide.idea.model;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import groovy.util.Node;

import java.util.Map;

/**
 * Represents an orderEntry of type module in the iml XML.
 */
public class ModuleDependency implements Dependency {

    private String name;
    private String scope;
    private boolean exported;

    public ModuleDependency(String name, String scope) {
        this.name = name;
        this.scope = scope;
        this.exported = false;
    }

    @Deprecated
    public ModuleDependency(Object name, Object scope) {
        this((String)name, (String)scope);
    }

    /**
     * The name of the module the module depends on.
     * Must not be null.
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * The scope for this dependency. If null the scope attribute is not added.
     */
    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public boolean isExported() {
        return exported;
    }

    public void setExported(boolean exported) {
        this.exported = exported;
    }

    @Override
    public void addToNode(Node parentNode) {
        Map<String, Object> attributes = Maps.newHashMap(ImmutableMap.<String, Object>builder()
            .put("type", "module")
            .put("module-name", name)
            .putAll(getAttributeMapForScopeAndExported())
            .build());
        parentNode.appendNode("orderEntry", attributes);
    }

    private Map<String, Object> getAttributeMapForScopeAndExported() {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        if (exported) {
            builder.put("exported", "");
        }
        if (!Strings.isNullOrEmpty(scope) && !"COMPILE".equals(scope)) {
            builder.put("scope", scope);
        }
        return builder.build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!getClass().equals(o.getClass())) {
            return false;
        }
        ModuleDependency that = (ModuleDependency) o;
        return Objects.equal(name, that.name) && scopeEquals(scope, that.scope);
    }

    private boolean scopeEquals(String lhs, String rhs) {
        if ("COMPILE".equals(lhs)) {
            return Strings.isNullOrEmpty(rhs) || "COMPILE".equals(rhs);
        } else if ("COMPILE".equals(rhs)) {
            return Strings.isNullOrEmpty(lhs) || "COMPILE".equals(lhs);
        } else {
            return Objects.equal(lhs, rhs);
        }
    }

    @Override
    public int hashCode() {
        int result;
        result = name.hashCode();
        result = 31 * result + getScopeHash();
        return result;
    }

    private int getScopeHash() {
        return (!Strings.isNullOrEmpty(scope) && !scope.equals("COMPILE")) ? scope.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "ModuleDependency{" + "name='" + name + "\'" + ", scope='" + scope + "\'" + "}";
    }
}
