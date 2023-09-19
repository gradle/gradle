/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import groovy.util.Node;
import groovy.util.NodeList;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.eclipse.model.internal.FileReferenceFactory;
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

/**
 * Represents the customizable elements of an eclipse classpath file. (via XML hooks everything is customizable).
 */
public class Classpath extends XmlPersistableConfigurationObject {
    private final FileReferenceFactory fileReferenceFactory;
    private List<ClasspathEntry> entries = Lists.newArrayList();

    public Classpath(XmlTransformer xmlTransformer, FileReferenceFactory fileReferenceFactory) {
        super(xmlTransformer);
        this.fileReferenceFactory = fileReferenceFactory;
    }

    public Classpath(FileReferenceFactory fileReferenceFactory) {
        this(new XmlTransformer(), fileReferenceFactory);
    }

    public Classpath() {
        this(new FileReferenceFactory());
    }

    public List<ClasspathEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<ClasspathEntry> entries) {
        this.entries = entries;
    }

    @Override
    protected String getDefaultResourceName() {
        return "defaultClasspath.xml";
    }

    @Override
    protected void load(Node xml) {
        for (Object e : (NodeList)xml.get("classpathentry")) {
            Node entryNode = (Node) e;
            Object kind = entryNode.attribute("kind");
            if ("src".equals(kind)) {
                String path = (String) entryNode.attribute("path");
                entries.add(path.startsWith("/") ? new ProjectDependency(entryNode) : new SourceFolder(entryNode));
            } else if ("var".equals(kind)) {
                entries.add(new Variable(entryNode, fileReferenceFactory));
            } else if ("con".equals(kind)) {
                entries.add(new Container(entryNode));
            } else if ("lib".equals(kind)) {
                entries.add(new Library(entryNode, fileReferenceFactory));
            } else if ("output".equals(kind)) {
                entries.add(new Output(entryNode));
            }
        }
    }

    @SuppressWarnings({"unchecked"}) // TODO: Change this signature once we can break compatibility
    public Object configure(List<?> newEntries) {
        List<SourceFolder> newSourceFolders = newEntries.stream()
            .filter(SourceFolder.class::isInstance)
            .map(SourceFolder.class::cast)
            .collect(toList());

        Set<ClasspathEntry> updatedEntries = entries.stream()
            .filter(entry -> shouldKeepEntry(newSourceFolders, entry))
            .collect(toCollection(LinkedHashSet::new));

        updatedEntries.addAll((List<ClasspathEntry>)newEntries); //merge new and old entries with matching path entries
        return entries = Lists.newArrayList(updatedEntries);
    }

    private boolean shouldKeepEntry(List<SourceFolder> newEntries, ClasspathEntry entry) {
        return !isDependency(entry)
            && !isJreContainer(entry)
            && !isOutputLocation(entry)
            && !isExistingEntryDuplicate(newEntries, entry);
    }

    private static boolean isExistingEntryDuplicate(List<SourceFolder> newSourceFolders, ClasspathEntry existingEntry) {
        if (!(existingEntry instanceof SourceFolder)) {
            return false;
        }

        SourceFolder sourceFolder = (SourceFolder) existingEntry;
        return newSourceFolders.stream().anyMatch(newSourceFolder -> {
            return Objects.equal(sourceFolder.getKind(), newSourceFolder.getKind())
                && Objects.equal(sourceFolder.getPath(), newSourceFolder.getPath())
                && Objects.equal(sourceFolder.getExcludes(), newSourceFolder.getExcludes())
                && Objects.equal(sourceFolder.getIncludes(), newSourceFolder.getIncludes());
        });
    }

    @Override
    protected void store(Node xml) {
        NodeList classpathEntryNodes = (NodeList)xml.get("classpathentry");
        for (Object classpathEntry : classpathEntryNodes) {
            xml.remove((Node) classpathEntry);
        }
        for (ClasspathEntry entry : filterDuplicateProjectDependencies(entries)) {
            entry.appendNode(xml);
        }
    }

    /*
     * Gradle 5.6 introduced closed project substitution for Buildship: https://github.com/gradle/gradle/pull/9405
     * The feature is built upon the EclipseProject TAPI model which is based on the result of the Eclipse plugin.
     *
     * To distinguish between different task dependencies the closed project substitution feature had to change
     * the equals/hashCode implementation of ProjectDependency which lead to duplicate project dependencies
     * in the .classpath file when the 'eclipse' task is invoked (which - btw - the Buildship plugin does not do).
     *
     * What we do here is a quick workaround to remove the duplication from the generated .classpath files. The
     * proper solution has a much larger scope: we'd need to decouple the EclipseProject TAPI model generation
     * from the files generated by the 'eclipse' Gradle plugin.
     */
    private static List<ClasspathEntry> filterDuplicateProjectDependencies(List<ClasspathEntry> entries) {
        List<ClasspathEntry> filtered = new ArrayList<>(entries.size());
        Set<String> paths = new HashSet<>();
        for (ClasspathEntry entry : entries) {
            if (entry instanceof ProjectDependency) {
                String path = ((ProjectDependency)entry).getPath();
                if (!paths.contains(path)) {
                    paths.add(path);
                    filtered.add(entry);
                }
            } else {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Classpath classpath = (Classpath) o;
        return Objects.equal(entries, classpath.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(entries);
    }

    @Override
    public String toString() {
        return "Classpath{entries=" + entries + "}";
    }

    private boolean isDependency(ClasspathEntry entry) {
        return entry instanceof ProjectDependency || entry instanceof AbstractLibrary;
    }

    private boolean isJreContainer(ClasspathEntry entry) {
        return entry instanceof Container && ((Container) entry).getPath().startsWith("org.eclipse.jdt.launching.JRE_CONTAINER");
    }

    private boolean isOutputLocation(ClasspathEntry entry) {
        return entry instanceof Output;
    }

    /**
     * Creates a new {@link FileReference} instance.
     * <p>
     * The created object can be used to configure custom source or javadoc location on {@link Library} and on {@link Variable} objects.
     * <p>
     * This method can receive either String or File instances.
     *
     * @param reference The object to transform into a new file reference. Can be instance of File or String.
     * @return The new file reference.
     * @see AbstractLibrary#setJavadocPath(FileReference)
     * @see AbstractLibrary#setSourcePath(FileReference)
     */
    public FileReference fileReference(Object reference) {
        if (reference instanceof File) {
            return fileReferenceFactory.fromFile((File) reference);
        } else if (reference instanceof String) {
            return fileReferenceFactory.fromVariablePath((String) reference);
        } else {
            String type = reference == null ? "null" : reference.getClass().getName();
            throw new RuntimeException("File reference can only be created from File or String instances but " + type + " was passed");
        }
    }

}
