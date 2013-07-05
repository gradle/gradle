/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.util.Message;
import org.gradle.internal.Factory;

/**
 * @author Hans Dockter
 */
public class DefaultSettingsConverter implements SettingsConverter {
    private final Factory<IvySettings> settingsFactory;
    private IvySettings publishSettings;
    private IvySettings resolveSettings;

    public DefaultSettingsConverter(Factory<IvySettings> settingsFactory) {
        this.settingsFactory = settingsFactory;
        Message.setDefaultLogger(new IvyLoggingAdaper());
    }

    public IvySettings convertForPublish() {
        if (publishSettings == null) {
            publishSettings = settingsFactory.create();
        } else {
            publishSettings.getResolvers().clear();
        }
        return publishSettings;
    }

    public IvySettings convertForResolve() {
        if (resolveSettings == null) {
            resolveSettings = settingsFactory.create();
        } else {
            resolveSettings.getResolvers().clear();
        }

        return resolveSettings;
    }

    public IvySettings getForResolve() {
        if (resolveSettings == null) {
            resolveSettings = settingsFactory.create();
        }
        return resolveSettings;
    }
}
