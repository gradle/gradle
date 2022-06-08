/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.execution.plan;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Preserves identity of {@see OrdinalGroup} instances so there's a 1-to-1 mapping of ordinals to groups allowing groups
 * to be freely compared by identity.
 */
@ServiceScope(Scopes.Build.class)
public class OrdinalGroupFactory {

    private final Int2ObjectOpenHashMap<OrdinalGroup> groups = new Int2ObjectOpenHashMap<>();

    public final OrdinalGroup group(int ordinal) {
        return groups.computeIfAbsent(ordinal, OrdinalGroup::new);
    }
}
