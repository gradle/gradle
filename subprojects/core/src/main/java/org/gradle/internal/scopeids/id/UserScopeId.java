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
 * A persistent ID of a user.
 *
 * It is effectively the Gradle user home dir.
 * That is, two builds by the same operating system user, potentially of different “projects”,
 * share the same user ID.
 *
 * This ID is persisted in the Gradle user home dir.
 * If this directory is destroyed, or a build is run with a different gradle user home,
 * a new ID will be issued.
 */
public final class UserScopeId extends ScopeId {

    public UserScopeId(UniqueId id) {
        super(id);
    }

}
