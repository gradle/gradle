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
import com.google.common.collect.Maps;
import groovy.util.Node;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.plugins.ide.eclipse.model.internal.FileReferenceFactory;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Common superclass for the library elements.
 */
public abstract class AbstractLibrary extends AbstractClasspathEntry {
    private static final String ATTRIBUTE_JAVADOC_LOCATION = "javadoc_location";

    private FileReference sourcePath;
    private FileReference javadocPath;
    private FileReference library;
    private ModuleVersionIdentifier moduleVersion;

    public AbstractLibrary(Node node, FileReferenceFactory fileReferenceFactory) {
        super(node);
        String javadocLocation = (String) getEntryAttributes().get(ATTRIBUTE_JAVADOC_LOCATION);
        javadocPath = fileReferenceFactory.fromJarURI(javadocLocation);
    }

    public AbstractLibrary(FileReference library) {
        super(library.getPath());
        this.library = library;
    }

    public FileReference getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(FileReference sourcePath) {
        this.sourcePath = sourcePath;
    }

    public FileReference getJavadocPath() {
        return javadocPath;
    }

    public void setJavadocPath(FileReference path) {
        this.javadocPath = path;
        if (path != null) {
            String location = path.getJarURL();
            getEntryAttributes().put(ATTRIBUTE_JAVADOC_LOCATION, location);
        } else {
            getEntryAttributes().remove(ATTRIBUTE_JAVADOC_LOCATION);
        }
    }

    public FileReference getLibrary() {
        return library;
    }

    public void setLibrary(FileReference library) {
        this.library = library;
        setPath(library.getPath());
    }

    @Nullable
    public ModuleVersionIdentifier getModuleVersion() {
        return moduleVersion;
    }

    public void setModuleVersion(@Nullable ModuleVersionIdentifier moduleVersion) {
        this.moduleVersion = moduleVersion;
    }

    @Override
    public void appendNode(Node node) {
        Map<String, Object> attributes = Maps.newHashMap();
        attributes.put("sourcepath", sourcePath == null ? null : sourcePath.getPath());
        addClasspathEntry(node, attributes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        AbstractLibrary that = (AbstractLibrary) o;
        return Objects.equal(isExported(), that.isExported())
            && Objects.equal(getAccessRules(), that.getAccessRules())
            && Objects.equal(getJavadocPath(), that.getJavadocPath())
            && Objects.equal(getNativeLibraryLocation(), that.getNativeLibraryLocation())
            && Objects.equal(getPath(), that.getPath())
            && Objects.equal(getSourcePath(), that.getSourcePath());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getPath(), getNativeLibraryLocation(), isExported(), getAccessRules(), getSourcePath(), getJavadocPath());
    }

    @Override
    public String toString() {
        return "{path='" + getPath() + "', nativeLibraryLocation='" + getNativeLibraryLocation() + "', exported=" + isExported()
            + ", accessRules=" + getAccessRules() + ", sourcePath='" + sourcePath + "', javadocPath='" + javadocPath + "', id='" + moduleVersion + "'}";
    }

}
