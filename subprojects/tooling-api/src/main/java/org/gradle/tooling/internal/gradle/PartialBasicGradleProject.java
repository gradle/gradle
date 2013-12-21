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
import java.util.LinkedHashSet;
import java.util.Set;

public class PartialBasicGradleProject implements Serializable, GradleProjectIdentity {
    private String name;
    private String path;
    private PartialBasicGradleProject parent;
    private Set<PartialBasicGradleProject> children = new LinkedHashSet<PartialBasicGradleProject>();

    @Override
    public String toString() {
        return "GradleProject{path='" + path + "\'}";
    }

    public String getPath() {
        return path;
    }

    public PartialBasicGradleProject setPath(String path) {
        this.path = path;
        return this;
    }

    public PartialBasicGradleProject getParent() {
        return parent;
    }

    public PartialBasicGradleProject setParent(PartialBasicGradleProject parent) {
        this.parent = parent;
        return this;
    }

    public String getName() {
        return name;
    }

    public PartialBasicGradleProject setName(String name) {
        this.name = name;
        return this;
    }

    public Set<? extends PartialBasicGradleProject> getChildren() {
        return children;
    }

    public PartialBasicGradleProject addChild(PartialBasicGradleProject child) {
        children.add(child);
        return this;
    }
}
