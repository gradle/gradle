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
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import groovy.util.Node;
import org.gradle.api.Incubating;

import java.io.File;
import java.util.Map;
import java.util.Set;

/**
 * A project-level IDEA library.
 */
@Incubating
public class ProjectLibrary {

    private String name;
    private String type;
    private Set<File> compilerClasspath = Sets.newLinkedHashSet();
    private Set<File> classes = Sets.newLinkedHashSet();
    private Set<File> javadoc = Sets.newLinkedHashSet();
    private Set<File> sources = Sets.newLinkedHashSet();

    /**
     * The name of the library.
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * The type of the library.
     */
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * A set of Jar files containing compiler classes.
     */
    public Set<File> getCompilerClasspath() {
        return compilerClasspath;
    }

    public void setCompilerClasspath(Set<File> compilerClasspath) {
        this.compilerClasspath = compilerClasspath;
    }

    /**
     * A set of Jar files or directories containing compiled code.
     */
    public Set<File> getClasses() {
        return classes;
    }

    /**
     * A set of Jar files or directories containing source code.
     */
    public void setClasses(Set<File> classes) {
        this.classes = classes;
    }

    /**
     * A set of Jar files or directories containing javadoc.
     */
    public Set<File> getJavadoc() {
        return javadoc;
    }

    public void setJavadoc(Set<File> javadoc) {
        this.javadoc = javadoc;
    }

    /**
     * A set of directories containing sources.
     */
    public Set<File> getSources() {
        return sources;
    }

    public void setSources(Set<File> sources) {
        this.sources = sources;
    }

    public void addToNode(Node parentNode, PathFactory pathFactory) {
        Map<String, Object> libraryAttributes = Maps.newHashMapWithExpectedSize(2);
        libraryAttributes.put("name", name);
        if (type != null) {
            libraryAttributes.put("type", type);
        }
        Node libraryNode = parentNode.appendNode("library", libraryAttributes);
        Node classesNode = libraryNode.appendNode("CLASSES");
        for (File file : classes) {
            Map<String, Object> attributes = Maps.newHashMapWithExpectedSize(1);
            attributes.put("url", pathFactory.path(file).getUrl());
            classesNode.appendNode("root", attributes);
        }
        Node javadocNode = libraryNode.appendNode("JAVADOC");
        for (File file : javadoc) {
            Map<String, Object> attributes = Maps.newHashMapWithExpectedSize(1);
            attributes.put("url", pathFactory.path(file).getUrl());
            javadocNode.appendNode("root", attributes);
        }
        Node sourcesNode = libraryNode.appendNode("SOURCES");
        for (File file : sources) {
            Map<String, Object> attributes = Maps.newHashMapWithExpectedSize(1);
            attributes.put("url", pathFactory.path(file).getUrl());
            sourcesNode.appendNode("root", attributes);
        }

        if (compilerClasspath.size() > 0) {
            Node properties = libraryNode.appendNode("properties");
            Node compilerClasspathNode = properties.appendNode("compiler-classpath");
            for (File file : compilerClasspath) {
                Map<String, Object> attributes = Maps.newHashMapWithExpectedSize(1);
                attributes.put("url", pathFactory.path(file, true).getUrl());
                compilerClasspathNode.appendNode("root", attributes);
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ProjectLibrary)) {
            return false;
        }
        ProjectLibrary that = (ProjectLibrary) obj;
        return Objects.equal(name, that.name)
            && Objects.equal(type, that.type)
            && Objects.equal(compilerClasspath, that.compilerClasspath)
            && Objects.equal(classes, that.classes)
            && Objects.equal(javadoc, that.javadoc)
            && Objects.equal(sources, that.sources);
    }

    @Override
    public int hashCode() {
        int result;
        result = name.hashCode();
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + compilerClasspath.hashCode();
        result = 31 * result + classes.hashCode();
        result = 31 * result + javadoc.hashCode();
        result = 31 * result + sources.hashCode();
        return result;
    }
}
