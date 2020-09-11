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

package org.gradle.internal.execution;

import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

@EventScope(Scopes.Build.class)
@ServiceScope(Scopes.Build.class)
public interface OutputChangeListener {
    /**
     * Invoked when the outputs of a work item are about to change.
     * This happens for example just before the task actions are executed or the outputs are loaded from the cache.
     *
     * @param affectedOutputPaths The files which are affected by the change.
     */
    void beforeOutputChange(Iterable<String> affectedOutputPaths);
}
