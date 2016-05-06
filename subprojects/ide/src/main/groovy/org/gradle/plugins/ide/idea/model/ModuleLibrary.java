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

import com.google.common.collect.ImmutableMap;
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

    private Set<Path> classes = Sets.newLinkedHashSet();
    private Set<JarDirectory> jarDirectories = Sets.newLinkedHashSet();
    private Set<Path> javadoc = Sets.newLinkedHashSet();
    private Set<Path> sources = Sets.newLinkedHashSet();
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
     * The scope of this library. If <tt>null</tt>, the scope attribute is not added.
     */
    public String getScope() {
        return scope;
    }

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
        Map<String, Object> attributes = ImmutableMap.<String, Object>builder().put("type", "module-library").putAll(getAttributeMapForScopeAndExported()).build();
        Node libraryNode = parentNode.appendNode("orderEntry", attributes).appendNode("library");
        Node classesNode = libraryNode.appendNode("CLASSES");
        Node javadocNode = libraryNode.appendNode("JAVADOC");
        Node sourcesNode = libraryNode.appendNode("SOURCES");
        for (Path path : classes) {
            classesNode.appendNode("root", ImmutableMap.of("url", path.getUrl()));
        }
        for (Path path : javadoc) {
            javadocNode.appendNode("root", ImmutableMap.of("url", path.getUrl()));
        }
        for (Path path : sources) {
            sourcesNode.appendNode("root", ImmutableMap.of("url", path.getUrl()));
        }
        for (JarDirectory jarDirectory : jarDirectories) {
            ImmutableMap<String, String> jarDirectoryAttributes = ImmutableMap.of("url", jarDirectory.getPath().getUrl(), "recursive", String.valueOf(jarDirectory.getRecursive()));
            libraryNode.appendNode("jarDirectory", jarDirectoryAttributes);
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
        if (!classes.equals(that.classes)) {
            return false;
        }
        if (!jarDirectories.equals(that.jarDirectories)) {
            return false;
        }
        if (!javadoc.equals(that.javadoc)) {
            return false;
        }
        if (!scopeEquals(scope, that.scope)) {
            return false;
        }
        if (!sources.equals(that.sources)) {
            return false;
        }
        return true;
    }

    private boolean scopeEquals(String lhs, String rhs) {
        if (isNullOrEmpty(lhs) || lhs.equals("COMPILE")) {
            return isNullOrEmpty(rhs) || rhs.equals("COMPILE");
        } else if (isNullOrEmpty(rhs) || rhs.equals("COMPILE")) {
            return isNullOrEmpty(lhs) || lhs.equals("COMPILE");
        } else {
            return lhs.equals(rhs);
        }
    }

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
        return scope != null && !scope.equals("COMPILE") ? scope.hashCode() : 0;
    }

    public String toString() {
        return "ModuleLibrary{" + "classes=" + classes + ", jarDirectories=" + jarDirectories + ", javadoc=" + javadoc + ", sources=" + sources + ", scope='" + scope + "\'" + "}";
    }


}
