/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.project;

import org.gradle.api.file.Directory;
import org.gradle.api.project.IsolatedProject;
import org.jspecify.annotations.Nullable;

public final class DefaultIsolatedProject implements IsolatedProject {

    private final ProjectState owner;

    public DefaultIsolatedProject(ProjectState owner) {
        this.owner = owner;
    }

    @Override
    public String getName() {
        return owner.getName();
    }

    @Override
    public String getPath() {
        return owner.getProjectPath().asString();
    }

    @Override
    public String getBuildTreePath() {
        return owner.getIdentityPath().asString();
    }

    @Override
    public Directory getProjectDirectory() {
        // TODO: avoid mutable model access and have a Directory-typed project dir available on ProjectState
        // This mutable state access is safe, because the project directory is immutable in the layout.
        return owner.getMutableModel().getLayout().getProjectDirectory();
    }

    @Override
    public IsolatedProject getRootProject() {
        return owner.isRootProject() ? this : owner.getOwner().getRootProject().getIsolated();
    }

    @Override
    public int hashCode() {
        return owner.getIdentity().hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof DefaultIsolatedProject)) {
            return false;
        }
        DefaultIsolatedProject that = (DefaultIsolatedProject) obj;
        return this.owner.getIdentity().equals(that.owner.getIdentity());
    }

    @Override
    public String toString() {
        return "DefaultIsolatedProject{" + owner.getDisplayName() + '}';
    }
}
