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

import org.gradle.api.Nullable
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.plugins.ide.eclipse.model.internal.FileReferenceFactory

abstract class AbstractLibrary extends AbstractClasspathEntry {
    FileReference sourcePath
    FileReference javadocPath
    FileReference library
    String declaredConfigurationName
    @Nullable
    ModuleVersionIdentifier moduleVersion

    AbstractLibrary(Node node, FileReferenceFactory fileReferenceFactory) {
        super(node)
        javadocPath = fileReferenceFactory.fromJarURI(entryAttributes.javadoc_location)
    }

    AbstractLibrary(FileReference library) {
        super(library.path)
        this.library = library
    }

    void setLibrary(FileReference library) {
        this.library = library
        path = library.path
    }

    void setJavadocPath(FileReference path) {
        this.javadocPath = path
        entryAttributes.javadoc_location = path ? path.jarURL : null
    }

    void appendNode(Node node) {
        addClasspathEntry(node, [sourcepath: sourcePath?.path])
    }

    boolean equals(o) {
        if (this.is(o)) { return true }

        if (o == null || getClass() != o.class) { return false }

        AbstractLibrary that = (AbstractLibrary) o;

        if (exported != that.exported) { return false }
        if (accessRules != that.accessRules) { return false }
        if (javadocPath != that.javadocPath) { return false }
        if (nativeLibraryLocation != that.nativeLibraryLocation) { return false }
        if (path != that.path) { return false }
        if (sourcePath != that.sourcePath) { return false }

        return true
    }

    int hashCode() {
        int result;

        result = path.hashCode();
        result = 31 * result + (nativeLibraryLocation != null ? nativeLibraryLocation.hashCode() : 0);
        result = 31 * result + (exported ? 1 : 0);
        result = 31 * result + accessRules.hashCode();
        result = 31 * result + (sourcePath != null ? sourcePath.hashCode() : 0);
        result = 31 * result + (javadocPath != null ? javadocPath.hashCode() : 0);
        return result;
    }

    public String toString() {
        return "{" +
                "path='" + path + '\'' +
                ", nativeLibraryLocation='" + nativeLibraryLocation + '\'' +
                ", exported=" + exported +
                ", accessRules=" + accessRules +
                ", sourcePath='" + sourcePath + '\'' +
                ", javadocPath='" + javadocPath + '\'' +
                ", id='" + moduleVersion + '\'' +
                '}';
    }
}
