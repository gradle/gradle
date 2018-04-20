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

package org.gradle.api.internal.project;

import org.gradle.api.Project;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Encapsulates the identity and state of a particular project in a build tree.
 */
@ThreadSafe
public interface ProjectState {
    /**
     * Returns the parent of this project in the project tree. Note that this isn't the same as {@link Project#getParent()}.
     */
    @Nullable
    ProjectState getParent();

    /**
     * Returns the name of this project (which may not necessarily be unique).
     */
    String getName();

    /**
     * Returns the identifier of the default component produced by this project.
     */
    ProjectComponentIdentifier getComponentIdentifier();
}
