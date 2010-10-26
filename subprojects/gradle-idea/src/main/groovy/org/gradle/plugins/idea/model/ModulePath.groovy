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

class ModulePath extends Path {
    /**
     * The path string of this path.
     */
    final String path

    def ModulePath(File rootDir, String rootDirString, File file) {
        super(rootDir, rootDirString, file)
        path = relPath
    }

    def ModulePath(String url, String path) {
        super(url)
        assert path != null
        this.path = path;
    }

    boolean equals(o) {
        if (this.is(o)) { return true }

        if (o== null || getClass() != o.class) { return false }
        if (!super.equals(o)) { return false }

        ModulePath that = (ModulePath) o;

        if (path != that.path) { return false }

        return true;
    }

    int hashCode() {
        int result = super.hashCode();

        result = 31 * result + path.hashCode();
        return result;
    }                                


    public String toString() {
        return "ModulePath{" +
                "url='" + url +
                ", path='" + path + '\'' +
                '}';
    }
}