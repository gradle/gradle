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

package org.gradle.tooling.provider.model.internal;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.jspecify.annotations.NullMarked;

/**
 * Registrant that registers one or more {@link org.gradle.tooling.provider.model.ToolingModelBuilder} to {@link ToolingModelBuilderRegistry}.
 *
 * It's used to register internal builders at the project scope that can run even if the project is not configured (e.g., Kotlin DSL scripts model).
 *
 * Normally builders should be registered at project configuration time and {@link ToolingModelBuilderRegistrant} should be used just for exceptional cases.
 */
@NullMarked
@ServiceScope(Scope.Project.class)
public interface ToolingModelBuilderRegistrant {

    void registerForProject(ToolingModelBuilderRegistry registry, boolean isRootProject);
}
