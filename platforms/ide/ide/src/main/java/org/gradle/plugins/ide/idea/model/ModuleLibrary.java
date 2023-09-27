/*
 * Copyright 2011 the original author or authors.
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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import groovy.util.Node;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Represents an orderEntry of type module-library in the iml XML.
 */
public class ModuleLibrary implements Dependency {

    private Set<Path> classes;
    private Set<JarDirectory> jarDirectories;
    private Set<Path> javadoc;
    private Set<Path> sources;
    private String scope;
    private boolean exported;

    public ModuleLibrary(Collection<? extends Path> classes, Collection<? extends Path> javadoc, Collection<? extends Path> sources, Collection<JarDirectory> jarDirectories, String scope) {
        this.classes = Sets.newLinkedHashSet(classes);
        this.jarDirectories = Sets.newLinkedHashSet(jarDirectories);
        this.javadoc = Sets.newLinkedHashSet(javadoc);
        this.sources = Sets.newLinkedHashSet(sources);
        this.scope = scope;
        this.exported = false;
    }

    /**
     * A set of Jar files or directories containing compiled code.
     */
    public Set<Path> getClasses() {
        return classes;
    }

    public void setClasses(Set<Path> classes) {
        this.classes = classes;
    }

    /**
     * A set of directories containing Jar files.
     */
    public Set<JarDirectory> getJarDirectories() {
        return jarDirectories;
    }

    public void setJarDirectories(Set<JarDirectory> jarDirectories) {
        this.jarDirectories = jarDirectories;
    }

    /**
     * A set of Jar files or directories containing Javadoc.
     */
    public Set<Path> getJavadoc() {
        return javadoc;
    }

    public void setJavadoc(Set<Path> javadoc) {
        this.javadoc = javadoc;
    }

    /**
     * A set of Jar files or directories containing source code.
     */
    public Set<Path> getSources() {
        return sources;
    }

    public void setSources(Set<Path> sources) {
        this.sources = sources;
    }

    /**
     * The scope of this library. If <code>null</code>, the scope attribute is not added.
     */
    @Override
    public String getScope() {
        return scope;
    }

    @Override
    public void setScope(String scope) {
        this.scope = scope;
    }

    /**
     * Whether the library is exported to dependent modules.
     */
    public boolean isExported() {
        return exported;
    }

    public void setExported(boolean exported) {
        this.exported = exported;
    }

    @Override
    public void addToNode(Node parentNode) {
        Map<String, Object> orderEntryAttributes = Maps.newLinkedHashMap();
        orderEntryAttributes.put("type", "module-library");
        orderEntryAttributes.putAll(getAttributeMapForScopeAndExported());
        Node libraryNode = parentNode.appendNode("orderEntry", orderEntryAttributes).appendNode("library");
        Node classesNode = libraryNode.appendNode("CLASSES");
        Node javadocNode = libraryNode.appendNode("JAVADOC");
        Node sourcesNode = libraryNode.appendNode("SOURCES");
        for (Path path : classes) {
            Map<String, Object> attributes = Maps.newLinkedHashMap();
            attributes.put("url", path.getUrl());
            classesNode.appendNode("root", attributes);
        }
        for (Path path : javadoc) {
            Map<String, Object> attributes = Maps.newLinkedHashMap();
            attributes.put("url", path.getUrl());
            javadocNode.appendNode("root", attributes);
        }
        for (Path path : sources) {
            Map<String, Object> attributes = Maps.newLinkedHashMap();
            attributes.put("url", path.getUrl());
            sourcesNode.appendNode("root", attributes);
        }
        for (JarDirectory jarDirectory : jarDirectories) {
            Map<String, Object> attributes = Maps.newLinkedHashMap();
            attributes.put("url", jarDirectory.getPath().getUrl());
            attributes.put("recursive", String.valueOf(jarDirectory.isRecursive()));
            libraryNode.appendNode("jarDirectory", attributes);
        }
    }

    private Map<String, Object> getAttributeMapForScopeAndExported() {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        if (exported) {
            builder.put("exported", "");
        }
        if (scope != null && !"COMPILE".equals(scope)) {
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
        ModuleLibrary that = (ModuleLibrary) o;
        return Objects.equal(classes, that.classes)
            && Objects.equal(jarDirectories, that.jarDirectories)
            && Objects.equal(javadoc, that.javadoc)
            && scopeEquals(scope, that.scope)
            && Objects.equal(sources, that.sources);
    }

    private boolean scopeEquals(String lhs, String rhs) {
        if ("COMPILE".equals(lhs)) {
            return isNullOrEmpty(rhs) || "COMPILE".equals(rhs);
        } else if ("COMPILE".equals(rhs)) {
            return isNullOrEmpty(lhs) || "COMPILE".equals(lhs);
        } else {
            return Objects.equal(lhs, rhs);
        }
    }

    @Override
    public int hashCode() {
        int result;
        result = classes.hashCode();
        result = 31 * result + jarDirectories.hashCode();
        result = 31 * result + javadoc.hashCode();
        result = 31 * result + sources.hashCode();
        result = 31 * result + getScopeHash();
        return result;
    }

    private int getScopeHash() {
        return !isNullOrEmpty(scope) && !scope.equals("COMPILE") ? scope.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "ModuleLibrary{"
            + "classes=" + classes
            + ", jarDirectories=" + jarDirectories
            + ", javadoc=" + javadoc
            + ", sources=" + sources
            + ", scope='" + scope
            + "\'" + "}";
    }
}
