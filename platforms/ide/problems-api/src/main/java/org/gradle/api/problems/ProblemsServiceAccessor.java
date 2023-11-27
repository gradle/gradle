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

package org.gradle.api.problems;

import org.gradle.api.Incubating;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Provides access to the Problems service.
 *
 * @since 8.6
 */
@Incubating
@ServiceScope(Scopes.BuildTree.class)
public interface ProblemsServiceAccessor {

    /**
     * Configures the Problems service to associate problems to a particular plugin.
     *
     * @param namespace the Plugin ID.
     * @return this
     * @since 8.6
     */
    ProblemsServiceAccessor withPluginNamespace(String namespace);

    /**
     * Returns the Problems service.
     * @return the service.
     */
    Problems get();
}
