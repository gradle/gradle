/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.build;

import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scopes;

/**
 * Listener for changes to the {@link BuildStateRegistry}.
 */
@EventScope(Scopes.BuildTree.class)
public interface BuildAddedListener {
    /**
     * Called every time a build is added to the {@link BuildStateRegistry}.
     *
     * The first call is always for the root build.
     */
    void buildAdded(BuildState buildState);
}
