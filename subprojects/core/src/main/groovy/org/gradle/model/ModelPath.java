/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model;

import org.gradle.api.Incubating;

@Incubating
public class ModelPath {

    public static final String SEPARATOR = ".";

    private final String path;

    public ModelPath(String path) {
        this.path = path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ModelPath modelPath = (ModelPath) o;

        if (!path.equals(modelPath.path)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return path;
    }

    public static ModelPath path(String path) {
        return new ModelPath(path);
    }

    public ModelPath child(String child) {
        return path(path + SEPARATOR + child);
    }

    public ModelPath getParent() {
        int lastIndex = path.lastIndexOf(SEPARATOR);
        if (lastIndex == -1) {
            return null;
        } else {
            return path(path.substring(0, lastIndex));
        }
    }

    public String getName() {
        int lastIndex = path.lastIndexOf(SEPARATOR);
        if (lastIndex == -1) {
            return path;
        } else {
            return path.substring(lastIndex + 1);
        }
    }

    public boolean isDirectChild(ModelPath other) {
        ModelPath otherParent = other.getParent();
        return otherParent == null ? false : otherParent.equals(this);
    }
}
