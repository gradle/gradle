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
 * The ID of a single build invocation.
 *
 * Here, the term "build" is used to represent the overall invocation.
 * For example, buildSrc shares the same build scope ID as the overall build.
 * All composite participants also share the same build scope ID.
 * That is, all “nested” builds (in terms of GradleLauncher etc.) share the same build ID.
 *
 * This ID is, by definition, not persistent.
 */
public final class BuildInvocationScopeId extends ScopeId {

    public BuildInvocationScopeId(UniqueId id) {
        super(id);
    }

}
