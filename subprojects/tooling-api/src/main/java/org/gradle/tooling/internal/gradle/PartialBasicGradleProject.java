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

import java.io.File;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

public class PartialBasicGradleProject implements Serializable, GradleProjectIdentity {
    private String name;
    private DefaultProjectIdentifier projectIdentifier;
    private PartialBasicGradleProject parent;
    private Set<PartialBasicGradleProject> children = new LinkedHashSet<PartialBasicGradleProject>();

    @Override
    public String toString() {
        return "GradleProject{path='" + getPath() + "\'}";
    }

    public String getPath() {
        return projectIdentifier.getProjectPath();
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

    public DefaultProjectIdentifier getProjectIdentifier() {
        return projectIdentifier;
    }

    @Override
    public String getProjectPath() {
        return projectIdentifier.getProjectPath();
    }

    @Override
    public File getRootDir() {
        return projectIdentifier.getBuildIdentifier().getRootDir();
    }

    public PartialBasicGradleProject setProjectIdentifier(DefaultProjectIdentifier projectIdentifier) {
        this.projectIdentifier = projectIdentifier;
        return this;
    }
}
