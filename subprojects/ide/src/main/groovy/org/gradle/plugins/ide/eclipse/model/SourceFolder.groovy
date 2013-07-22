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

/**
 * SourceFolder.path contains only project relative path.
 */
class SourceFolder extends AbstractClasspathEntry {
    String output
    List<String> includes
    List<String> excludes
    //optional
    File dir

    SourceFolder(Node node) {
        super(node)
        this.output = normalizePath(node.@output)
        this.includes = node.@including?.split('\\|') ?: []
        this.excludes = node.@excluding?.split('\\|') ?: []
    }

    SourceFolder(String projectRelativePath, String output) {
        super(projectRelativePath)
        this.output = normalizePath(output);
        this.includes = []
        this.excludes = []
    }

    String getKind() {
        'src'
    }

    String getName() {
        dir.name
    }

    String getAbsolutePath() {
        dir.absolutePath
    }

    void trimPath() {
        path = name
    }

    void appendNode(Node node) {
        addClasspathEntry(node, [including: includes.join("|"), excluding: excludes.join("|"), output: output])
    }

    boolean equals(o) {
        if (this.is(o)) { return true }

        if (getClass() != o.class) { return false }

        SourceFolder that = (SourceFolder) o;

        if (exported != that.exported) { return false }
        if (accessRules != that.accessRules) { return false }
        if (excludes != that.excludes) { return false }
        if (includes != that.includes) { return false }
        if (nativeLibraryLocation != that.nativeLibraryLocation) { return false }
        if (output != that.output) { return false }
        if (path != that.path) { return false }

        return true
    }

    int hashCode() {
        int result;

        result = path.hashCode();
        result = 31 * result + (nativeLibraryLocation != null ? nativeLibraryLocation.hashCode() : 0);
        result = 31 * result + (exported ? 1 : 0);
        result = 31 * result + accessRules.hashCode();
        result = 31 * result + (output != null ? output.hashCode() : 0);
        result = 31 * result + excludes.hashCode();
        result = 31 * result + includes.hashCode();
        return result;
    }

    public String toString() {
        return "SourceFolder{" +
                "path='" + path + '\'' +
                ", dir='" + dir + '\'' +
                ", nativeLibraryLocation='" + nativeLibraryLocation + '\'' +
                ", exported=" + exported +
                ", accessRules=" + accessRules +
                ", output='" + output + '\'' +
                ", excludes=" + excludes +
                ", includes=" + includes +
                '}';
    }
}
