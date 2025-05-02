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
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

@EventScope(Scope.Build.class)
@ServiceScope(Scope.Build.class)
public interface OutputChangeListener {
    /**
     * Invoked when some locations on disk have been changed or are about to be changed.
     * This happens for example just before and after the task actions are executed or the outputs are loaded from the cache.
     *
     * @param affectedOutputPaths The files which are affected by the change.
     */
    void invalidateCachesFor(Iterable<String> affectedOutputPaths);
}
