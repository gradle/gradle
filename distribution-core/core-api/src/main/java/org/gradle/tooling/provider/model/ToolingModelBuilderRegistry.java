/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.tooling.provider.model;

import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * A registry of tooling model builders. Adding a builder to this registry makes a model (or models) available via the tooling API.
 *
 * <p>An instance of this type can be injected into a task, plugin or other object by annotating a public constructor or property getter method with {@code javax.inject.Inject}.
 */
@ServiceScope(Scopes.Build.class)
public interface ToolingModelBuilderRegistry {
    void register(ToolingModelBuilder builder);

    ToolingModelBuilder getBuilder(String modelName) throws UnknownModelException;
}
