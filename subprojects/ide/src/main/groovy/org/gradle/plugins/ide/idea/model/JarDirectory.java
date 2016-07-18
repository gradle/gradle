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

/**
 * Represents a jar directory element of an idea module library.
 */
public class JarDirectory {

    private Path path;
    private boolean recursive;

    public JarDirectory(Path path, boolean recursive) {
        this.path = path;
        this.recursive = recursive;
    }

    @Deprecated
    public JarDirectory(Object path, Object recursive) {
        this((Path)path, ((Boolean)recursive).booleanValue());
    }

    /**
     * The path of the jar directory
     */
    public Path getPath() {
        return path;
    }

    /**
     * The value for the recursive attribute of the jar directory element.
     */
    public void setPath(Path path) {
        this.path = path;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    @Override
    public String toString() {
        return "JarDirectory{" + "path=" + path + ", recursive=" + recursive + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!getClass().equals(o.getClass())) {
            return false;
        }
        JarDirectory that = (JarDirectory) o;
        return recursive == that.recursive
            && Objects.equal(path, that.path);
    }

    @Override
    public int hashCode() {
        int result;
        result = path.hashCode();
        result = 31 * result + (recursive ? 1 : 0);
        return result;
    }
}
