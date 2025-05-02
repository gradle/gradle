/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling.model;

import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class BasicGradleProject extends PartialBasicGradleProject {
    private File projectDirectory;
    private Set<BasicGradleProject> children = new LinkedHashSet<BasicGradleProject>();
    private String buildTreePath;


    public File getProjectDirectory() {
        return projectDirectory;
    }

    public BasicGradleProject setProjectDirectory(File projectDirectory) {
        this.projectDirectory = projectDirectory;
        return this;
    }

    @Override
    public BasicGradleProject setProjectIdentifier(DefaultProjectIdentifier projectIdentifier) {
        super.setProjectIdentifier(projectIdentifier);
        return this;
    }

    @Override
    public BasicGradleProject setName(String name) {
        super.setName(name);
        return this;
    }

    @Override
    public Set<? extends BasicGradleProject> getChildren() {
        return children;
    }

    public BasicGradleProject addChild(BasicGradleProject child) {
        children.add(child);
        return this;
    }

    public String getBuildTreePath() {
        return buildTreePath;
    }

    public BasicGradleProject setBuildTreePath(String path) {
        buildTreePath = path;
        return this;
    }
}
