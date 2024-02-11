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

package org.gradle.plugins.ide.internal;

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.TaskDependencyContainer;

import javax.annotation.Nullable;
import java.util.List;

/**
 * This should merge into {@link org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry}.
 */
public interface IdeArtifactRegistry {
    /**
     * Registers an IDE project model to be included in the IDE workspace.
     */
    void registerIdeProject(IdeProjectMetadata ideProjectMetadata);

    /**
     * Finds an IDE project with the given type in the given project. Does not execute tasks to build the project file.
     */
    @Nullable
    <T extends IdeProjectMetadata> T getIdeProject(Class<T> type, ProjectComponentIdentifier project);

    /**
     * Finds all known IDE projects with the given type that should be included in the IDE workspace. Does not execute tasks to build the artifact.
     */
    <T extends IdeProjectMetadata> List<Reference<T>> getIdeProjects(Class<T> type);

    /**
     * Returns a {@link FileCollection} containing the files for all IDE projects with the specified type that should be included in the IDE workspace.
     */
    FileCollection getIdeProjectFiles(Class<? extends IdeProjectMetadata> type);

    interface Reference<T extends IdeProjectMetadata> extends TaskDependencyContainer {
        T get();

        ProjectComponentIdentifier getOwningProject();
    }
}
