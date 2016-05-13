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
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.plugins.ide.eclipse.model.internal.FileReferenceFactory;
import org.gradle.util.DeprecationLogger;

import java.util.Map;

/**
 * Common superclass for the library elements.
 */
public abstract class AbstractLibrary extends AbstractClasspathEntry {

    private static final String DEPRECATED_DECLAREDCONFIGNAME_FIELD = "AbstractLibrary.declaredConfigurationName";

    private FileReference sourcePath;
    private FileReference javadocPath;
    private FileReference library;
    private String declaredConfigurationName;
    private ModuleVersionIdentifier moduleVersion;

    public AbstractLibrary(Node node, FileReferenceFactory fileReferenceFactory) {
        super(node);
        String javadocLocation = (String) getEntryAttributes().get("javadoc_location");
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
        String location = path != null ? path.getJarURL() : null;
        getEntryAttributes().put("javadoc_location", location);
    }

    public FileReference getLibrary() {
        return library;
    }

    public void setLibrary(FileReference library) {
        this.library = library;
        setPath(library.getPath());
    }

    @Deprecated
    public String getDeclaredConfigurationName() {
        DeprecationLogger.nagUserOfDeprecated(DEPRECATED_DECLAREDCONFIGNAME_FIELD);
        return declaredConfigurationName;
    }

    @Deprecated
    public void setDeclaredConfigurationName(String declaredConfigurationName) {
        DeprecationLogger.nagUserOfDeprecated(DEPRECATED_DECLAREDCONFIGNAME_FIELD);
        this.declaredConfigurationName = declaredConfigurationName;
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
        return Objects.equal(getExported(), that.getExported())
            && Objects.equal(getAccessRules(), that.getAccessRules())
            && Objects.equal(getJavadocPath(), that.getJavadocPath())
            && Objects.equal(getNativeLibraryLocation(), that.getNativeLibraryLocation())
            && Objects.equal(getPath(), that.getPath())
            && Objects.equal(getSourcePath(), that.getSourcePath());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getPath(), getNativeLibraryLocation(), getExported(), getAccessRules(), getSourcePath(), getJavadocPath());
    }

    @Override
    public String toString() {
        return "{path='" + getPath() + "', nativeLibraryLocation='" + getNativeLibraryLocation() + "', exported=" + getExported()
            + ", accessRules=" + getAccessRules() + ", sourcePath='" + sourcePath + "', javadocPath='" + javadocPath + "', id='" + moduleVersion + "'}";
    }

}
