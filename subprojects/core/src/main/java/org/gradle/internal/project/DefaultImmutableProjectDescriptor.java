/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.project;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.project.ProjectIdentity;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.List;

public class DefaultImmutableProjectDescriptor implements ImmutableProjectDescriptor {

    private final ProjectIdentity identity;
    private final File projectDir;
    private final File buildFile;
    private final @Nullable ProjectIdentity parent;
    private final List<ProjectIdentity> children;

    public DefaultImmutableProjectDescriptor(
        ProjectIdentity identity,
        File projectDir,
        File buildFile,
        @Nullable ProjectIdentity parent,
        List<ProjectIdentity> children
    ) {
        this.identity = identity;
        this.projectDir = projectDir;
        this.buildFile = buildFile;
        this.parent = parent;
        this.children = ImmutableList.copyOf(children);
    }

    @Override
    public ProjectIdentity getIdentity() {
        return identity;
    }

    @Override
    public File getProjectDir() {
        return projectDir;
    }

    @Override
    public File getBuildFile() {
        return buildFile;
    }

    @Override
    @Nullable
    public ProjectIdentity getParent() {
        return parent;
    }

    @Override
    public List<ProjectIdentity> getChildren() {
        return children;
    }

    @Override
    public String toString() {
        return identity.toString();
    }
}
