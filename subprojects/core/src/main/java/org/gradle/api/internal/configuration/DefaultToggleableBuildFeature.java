/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.configuration;

import org.gradle.api.configuration.ToggleableBuildFeature;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

public class DefaultToggleableBuildFeature implements ToggleableBuildFeature {

    private final Property<Boolean> requested;
    private final Provider<Boolean> active;

    public DefaultToggleableBuildFeature(Property<Boolean> requested, Provider<Boolean> active) {
        this.requested = requested;
        this.active = active;
    }

    @Override
    public Property<Boolean> getRequested() {
        return requested;
    }

    @Override
    public Provider<Boolean> getActive() {
        return active;
    }
}
