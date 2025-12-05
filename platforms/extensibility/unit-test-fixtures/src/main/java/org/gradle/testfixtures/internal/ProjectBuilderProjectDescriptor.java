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

package org.gradle.testfixtures.internal;

import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.internal.project.ImmutableProjectDescriptor;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.unmodifiableList;

public class ProjectBuilderProjectDescriptor implements ImmutableProjectDescriptor {

    private final ProjectIdentity identity;
    private final File projectDir;
    private final File buildFile;
    private final @Nullable ProjectIdentity parent;
    private final List<ProjectIdentity> children = new ArrayList<>();

    public ProjectBuilderProjectDescriptor(
        ProjectIdentity identity,
        File projectDir,
        File buildFile,
        @Nullable ProjectIdentity parent
    ) {
        this.identity = identity;
        this.projectDir = projectDir;
        this.buildFile = buildFile;
        this.parent = parent;
    }

    /**
     * Allows late mutation of the children list.
     * <p>
     * This is only required for {@code ProjectBuilder}, because it allows
     * creating children after the parent project has been created.
     * In a normal build, all project descriptors are created at the same time.
     */
    public void addChild(ProjectIdentity child) {
        children.add(child);
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
        return unmodifiableList(children);
    }

    @Override
    public String toString() {
        return identity.getProjectPath().toString();
    }
}
