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

package org.gradle.tooling.provider.model.internal;

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scopes;

@NonNullApi
@EventScope(Scopes.Build.class)
public interface ToolingModelProjectDependencyListener {

    /**
     * Notified when model builders for a {@code consumer} project requests an intermediate model of some other {@code target} project.
     * <p>
     * The {@code consumer} and {@code target} might represent the same project, and the listener implementation
     * should handle this specifically, probably ignoring such calls.
     */
    void onToolingModelDependency(ProjectState consumer, ProjectState target);

}
