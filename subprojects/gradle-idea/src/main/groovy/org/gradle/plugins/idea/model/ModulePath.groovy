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
package org.gradle.plugins.idea.model

/**
 * Represents a path in a format as used often in ipr and iml files.
 *
 * @author Hans Dockter
 */

class ModulePath {
    /**
     * The path string of this path.
     */
    final String filePath

    final Path path

    def ModulePath(Path path) {
        this.path = path
        filePath = path.relPath
    }

    def ModulePath(Path path, String filePath) {
        this.path = path
        this.filePath = filePath
    }

    String getUrl() {
        return path.url
    }
    
    boolean equals(o) {
        if (this.is(o)) { return true }

        if (o == null || getClass() != o.class) { return false }

        ModulePath that = (ModulePath) o;
        return path == that.path && filePath == that.filePath
    }

    int hashCode() {
        return path.hashCode() ^ filePath.hashCode()
    }

    public String toString() {
        return "ModulePath{" +
                "path='" + path +
                ", filePath='" + filePath + '\'' +
                '}';
    }
}