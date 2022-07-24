/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.internal.DisplayName;

import javax.annotation.Nullable;

public class DefaultProjectPublication implements ProjectComponentPublication {
    private final DisplayName displayName;
    private final Object id;
    private final boolean legacy;

    public DefaultProjectPublication(DisplayName displayName, Object id, boolean legacy) {
        this.displayName = displayName;
        this.id = id;
        this.legacy = legacy;
    }

    @Override
    public DisplayName getDisplayName() {
        return displayName;
    }

    @Override
    public boolean isLegacy() {
        return legacy;
    }

    @Nullable
    @Override
    public <T> T getCoordinates(Class<T> type) {
        if (type.isInstance(id)) {
            return type.cast(id);
        }
        return null;
    }

    @Nullable
    @Override
    public SoftwareComponentInternal getComponent() {
        return null;
    }

    @Override
    public boolean isAlias() {
        return false;
    }
}
