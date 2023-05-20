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

package org.gradle.swiftpm.internal;

import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectComponentPublication;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.internal.DisplayName;

import javax.annotation.Nullable;

public class NativeProjectPublication implements ProjectComponentPublication {
    private final DisplayName displayName;
    private final SwiftPmTarget swiftPmTarget;

    public NativeProjectPublication(DisplayName displayName, SwiftPmTarget swiftPmTarget) {
        this.displayName = displayName;
        this.swiftPmTarget = swiftPmTarget;
    }

    @Override
    public DisplayName getDisplayName() {
        return displayName;
    }

    @Nullable
    @Override
    public <T> T getCoordinates(Class<T> type) {
        if (type.isAssignableFrom(SwiftPmTarget.class)) {
            return type.cast(swiftPmTarget);
        }
        return null;
    }

    @Nullable
    @Override
    public Provider<SoftwareComponentInternal> getComponent() {
        return Providers.notDefined();
    }

    @Override
    public boolean isAlias() {
        return false;
    }

    @Override
    public boolean isLegacy() {
        return false;
    }
}
