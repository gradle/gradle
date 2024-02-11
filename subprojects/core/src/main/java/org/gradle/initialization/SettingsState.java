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

package org.gradle.initialization;

import org.gradle.api.internal.SettingsInternal;
import org.gradle.internal.service.scopes.SettingsScopeServices;

import java.io.Closeable;

/**
 * A container that controls the lifecycle of a {@link org.gradle.api.initialization.Settings} object and its associated services.
 */
public class SettingsState implements Closeable {
    private final SettingsInternal settings;
    private final SettingsScopeServices services;

    public SettingsState(SettingsInternal settings, SettingsScopeServices services) {
        this.settings = settings;
        this.services = services;
    }

    public SettingsInternal getSettings() {
        return settings;
    }

    @Override
    public void close() {
        services.close();
    }
}
