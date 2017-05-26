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

package org.gradle.internal.scopeids.id;

import org.gradle.internal.id.UniqueId;

/**
 * The persistent ID of a potential build on disk.
 *
 * It is effectively the root dir of a build.
 * That is, two builds with the same root dir share the same workspace.
 *
 * In practice, this generally maps to what users would think of as “checkout” of a project.
 * Builds of the same checkout over time will share the same workspace ID.
 *
 * This ID is persisted in the root build's project cache dir.
 * If this cache directory is destroyed, a new ID will be issued.
 */
public final class WorkspaceScopeId extends ScopeId {

    public WorkspaceScopeId(UniqueId id) {
        super(id);
    }

}
