/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.plugin.use.internal;

import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scopes.Build.class)
public interface PluginRepositoryHandlerProvider {
    /**
     * Returns the {@link RepositoryHandler} that will contain the shared repositories to be used to resolve plugins for all project of this build.
     * Should be mutated only during settings configuration.
     */
    RepositoryHandler getPluginRepositoryHandler();
}
