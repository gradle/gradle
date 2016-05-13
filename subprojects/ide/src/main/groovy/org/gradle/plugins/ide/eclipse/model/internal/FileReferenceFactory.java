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

package org.gradle.plugins.ide.eclipse.model.internal;

import com.google.common.collect.Maps;
import org.gradle.internal.UncheckedException;
import org.gradle.plugins.ide.eclipse.model.FileReference;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

public class FileReferenceFactory {
    private final Map<String, File> variables = Maps.newHashMap();

    /**
     * Adds a path variable
     */
    public void addPathVariable(String name, File dir) {
        variables.put(name, dir);
    }

    /**
     * Creates a reference to the given file. Returns null for a null file.
     */
    public FileReference fromFile(File file) {
        if (file == null) {
            return null;
        }
        String path = null;
        boolean usedVar = false;
        for (Map.Entry<String, File> entry : variables.entrySet()) {
            String rootDirPath = entry.getValue().getAbsolutePath();
            String filePath = file.getAbsolutePath();
            if (filePath.equals(rootDirPath)) {
                path = entry.getKey();
                usedVar = true;
                break;
            }
            if (filePath.startsWith(rootDirPath + File.separator)) {
                int len = rootDirPath.length();
                path = entry.getKey() + filePath.substring(len);
                usedVar = true;
                break;
            }
        }
        path = PathUtil.normalizePath(path != null ? path : file.getAbsolutePath());
        return new FileReferenceImpl(file, path, usedVar);
    }

    /**
     * Creates a reference to the given path. Returns null for null path
     */
    public FileReference fromPath(String path) {
        if (path == null) {
            return null;
        }
        return new FileReferenceImpl(new File(path), path, false);
    }

    /**
     * Creates a reference to the given path. Returns null for null path
     */
    public FileReference fromJarURI(String jarURI) {
        if (jarURI== null) {
            return null;
        }
        //cut the pre and postfix of this url
        URI fileURI = null;
        try {
            fileURI = new URI(jarURI.replace("jar:", "").replace("!/", ""));
        } catch (URISyntaxException e) {
            UncheckedException.throwAsUncheckedException(e);
        }
        File file = new File(fileURI);
        String path = PathUtil.normalizePath(file.getAbsolutePath());
        return new FileReferenceImpl(file, path, false);
    }
    /**
     * Creates a reference to the given path containing a variable reference. Returns null for null variable path
     */
    public FileReference fromVariablePath(String path) {
        if (path == null) {
            return null;
        }
        for (Map.Entry<String, File> entry : variables.entrySet()) {
            String prefix = entry.getKey() + "/";
            if (path.startsWith(prefix)) {
                File file = new File(entry.getValue(), path.substring(prefix.length()));
                return new FileReferenceImpl(file, path, true);
            }
        }
        return fromPath(path);
    }

    private static class FileReferenceImpl implements FileReference {
        final File file;
        final String path;
        final boolean relativeToPathVariable;

        FileReferenceImpl(File file, String path, boolean relativeToPathVariable) {
            this.file = file;
            this.path = path;
            this.relativeToPathVariable = relativeToPathVariable;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            FileReference ref = (FileReference) obj;
            return file.equals(ref.getFile());
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public String getJarURL(){
            //windows needs an additional backslash in jar urls
            return  "jar:" + file.toURI() + "!/";

        }

        @Override
        public boolean isRelativeToPathVariable() {
            return relativeToPathVariable;
        }

        public String toString() {
            return "{file='" + file + "'path='" + path  + "', jarUrl='" + getJarURL() + "'}";
        }

        @Override
        public int hashCode() {
            return file.hashCode();
        }
    }
}
