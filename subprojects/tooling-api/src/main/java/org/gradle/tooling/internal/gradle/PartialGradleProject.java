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

package org.gradle.tooling.internal.gradle;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * A partial implementation of {@link org.gradle.tooling.model.GradleProject}.
 */
public class PartialGradleProject implements Serializable {
    private String name;
    private String description;
    private String path;
    private PartialGradleProject parent;
    private List<? extends PartialGradleProject> children = new LinkedList<PartialGradleProject>();

    public String getName() {
        return name;
    }

    public PartialGradleProject setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public PartialGradleProject setDescription(String description) {
        this.description = description;
        return this;
    }

    public PartialGradleProject getParent() {
        return parent;
    }

    public PartialGradleProject setParent(PartialGradleProject parent) {
        this.parent = parent;
        return this;
    }

    public Collection<? extends PartialGradleProject> getChildren() {
        return children;
    }

    public PartialGradleProject setChildren(List<? extends PartialGradleProject> children) {
        this.children = children;
        return this;
    }

    public String getPath() {
        return path;
    }

    public PartialGradleProject setPath(String path) {
        this.path = path;
        return this;
    }

    public PartialGradleProject findByPath(String path) {
        if (path.equals(this.path)) {
            return this;
        }
        for (PartialGradleProject child : children) {
            PartialGradleProject found = child.findByPath(path);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    public String toString() {
        return "GradleProject{"
                + "path='" + path + '\''
                + '}';
    }
}
