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

    public boolean getRecursive() {
        return recursive;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public String toString() {
        return "JarDirectory{" + "path=" + path + ", recursive=" + recursive + "}";
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!getClass().equals(o.getClass())) {
            return false;
        }
        JarDirectory that = (JarDirectory) o;
        if (!recursive == that.recursive) {
            return false;
        }
        if (!path.equals(that.path)) {
            return false;
        }
        return true;
    }

    public int hashCode() {
        int result;
        result = path.hashCode();
        result = 31 * result + (recursive ? 1 : 0);
        return result;
    }
}
