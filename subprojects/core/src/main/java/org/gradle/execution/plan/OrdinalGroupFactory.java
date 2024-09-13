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

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Preserves identity of {@link OrdinalGroup} instances so there's a 1-to-1 mapping of ordinals to groups allowing groups
 * to be freely compared by identity.
 */
@ServiceScope(Scope.Build.class)
public class OrdinalGroupFactory {

    private final List<OrdinalGroup> groups = new ArrayList<>();

    public final OrdinalGroup group(int ordinal) {
        growTo(ordinal);
        return groups.get(ordinal);
    }

    public List<OrdinalGroup> getAllGroups() {
        return groups;
    }

    public void reset() {
        groups.clear();
    }

    private void growTo(int ordinal) {
        for (int i = groups.size(); i <= ordinal; ++i) {
            groups.add(new OrdinalGroup(i, previous(i)));
        }
    }

    @Nullable
    private OrdinalGroup previous(int i) {
        if (i == 0) {
            return null;
        } else {
            return groups.get(i - 1);
        }
    }
}
