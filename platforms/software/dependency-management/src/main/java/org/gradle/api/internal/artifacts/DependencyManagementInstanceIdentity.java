/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.internal.DisplayName;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Identifies and describes a single dependency management instance.
 * <p>
 * Each dependency management instance is given exactly one identity. Identities
 * compare by reference. Two instances are distinct even if they share the same
 * display name.
 */
@ServiceScope(Scope.Project.class)
public final class DependencyManagementInstanceIdentity implements DisplayName {

    private final DisplayName displayName;

    public DependencyManagementInstanceIdentity(DisplayName displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getDisplayName() {
        return displayName.getDisplayName();
    }

    @Override
    public String getCapitalizedDisplayName() {
        return displayName.getCapitalizedDisplayName();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

}
