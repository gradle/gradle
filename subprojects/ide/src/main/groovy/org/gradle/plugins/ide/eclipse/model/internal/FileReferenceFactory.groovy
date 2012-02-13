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
package org.gradle.plugins.ide.eclipse.model.internal

import org.gradle.plugins.ide.eclipse.model.FileReference

class FileReferenceFactory {
    private final Map<String, File> variables = [:]

    /**
     * Adds a path variable
     */
    void addPathVariable(String name, File dir) {
        variables[name] = dir
    }

    /**
     * Creates a reference to the given file. Returns null for a null file.
     */
    FileReference fromFile(File file) {
        if (!file) {
            return null
        }
        def path = null
        def usedVar = false
        for (entry in variables.entrySet()) {
            def rootDirPath = entry.value.absolutePath
            def filePath = file.absolutePath
            if (filePath == rootDirPath) {
                path = entry.key
                usedVar = true
                break
            }
            if (filePath.startsWith(rootDirPath + File.separator)) {
                int len = rootDirPath.length()
                path = entry.key + filePath.substring(len)
                usedVar = true
                break
            }
        }
        path = PathUtil.normalizePath(path ?: file.absolutePath)
        return new FileReferenceImpl(file, path, usedVar)
    }

    /**
     * Creates a reference to the given path. Returns null for for null path
     */
    FileReference fromPath(String path) {
        if (path == null) {
            return null
        }
        new FileReferenceImpl(new File(path), path, false)
    }

    /**
     * Creates a reference to the given path. Returns null for for null path
     */
    FileReference fromJarURI(String jarURI) {
        if (jarURI== null) {
            return null
        }
        //cut the pre and postfix of this url
        URI fileURI = new URI(jarURI - "jar:" - "!/");
        File file = new File(fileURI);
        String path = PathUtil.normalizePath(file.absolutePath)
        new FileReferenceImpl(file, path, false);
    }
    /**
     * Creates a reference to the given path containing a variable reference. Returns null for null variable path
     */
    FileReference fromVariablePath(String path) {
        if (path == null) {
            return null
        }
        for (entry in variables.entrySet()) {
            def prefix = "$entry.key/"
            if (path.startsWith(prefix)) {
                def file = new File(entry.value, path.substring(prefix.length()))
                return new FileReferenceImpl(file, path, true)
            }
        }
        return fromPath(path)
    }

    private static class FileReferenceImpl implements FileReference {
        final File file
        final String path
        final boolean relativeToPathVariable

        FileReferenceImpl(File file, String path, boolean relativeToPathVariable) {
            this.file = file
            this.path = path
            this.relativeToPathVariable = relativeToPathVariable
        }

        @Override
        boolean equals(Object obj) {
            if (obj.is(this)) {
                return true
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false
            }

            return file.equals(obj.file)
        }

        public String getJarURL(){
            //windows needs an additional backslash in jar urls
            return "jar:${file.toURI()}!/"
        }

        public String toString() {
            return "{" +
                    "file='" + file + '\'' +
                    "path='" + path + '\'' +
                    ", jarUrl='" + getJarURL() + '\'' +
                    '}';
        }

        @Override
        int hashCode() {
            return file.hashCode()
        }
    }
}
