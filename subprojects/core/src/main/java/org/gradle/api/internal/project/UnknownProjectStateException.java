/*
 * Copyright 2026 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.exceptions.ResolutionProvider;

import java.util.List;

/**
 * Thrown when a lookup for a {@link org.gradle.api.internal.project.ProjectState ProjectState} by
 * {@link ProjectComponentIdentifier} fails.
 * <p>
 * One common cause is that a task action queried an artifact whose producing project was not
 * declared as a task input, leaving the project off the execution plan.  This is particularly
 * likely to occur with {@code ArtifactTransform}s and the Configuration Cache.
 * <p>
 * Extends {@link IllegalArgumentException} for source-level backward compatibility with any
 * existing {@code catch (IllegalArgumentException)} sites in callers.
 */
public final class UnknownProjectStateException extends IllegalArgumentException implements ResolutionProvider {
    private static final List<String> RESOLUTIONS = ImmutableList.of(
        "Declare the files or artifacts produced by the configuration using the transform as a task input to properly wire it into the execution plan.",
        "Consult the upgrading guide for further information: "
            + new DocumentationRegistry().getDocumentationFor("upgrading_version_9", "undeclared_artifact_transform_input")
    );

    private final ProjectComponentIdentifier identifier;

    public UnknownProjectStateException(ProjectComponentIdentifier identifier) {
        super(buildMessage(identifier));
        this.identifier = identifier;
    }

    public ProjectComponentIdentifier getIdentifier() {
        return identifier;
    }

    private static String buildMessage(ProjectComponentIdentifier identifier) {
        return "Could not access " + identifier.getDisplayName() + ". "
            + "No task declared this project as part of an input, so it was not scheduled. "
            + "Properly declare all task inputs (including the result of any dependency resolutions) to ensure this project is scheduled for execution.";
    }

    @Override
    public List<String> getResolutions() {
        return RESOLUTIONS;
    }
}
