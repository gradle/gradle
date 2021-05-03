/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.internal.build.BuildModelController;

public class ConfigurationCacheAwareBuildModelController implements BuildModelController {
    private enum State {
        Created, Loaded, DoNotReuse
    }

    private final GradleInternal model;
    private final BuildModelController delegate;
    private final ConfigurationCache configurationCache;
    private State state = State.Created;

    public ConfigurationCacheAwareBuildModelController(GradleInternal model, BuildModelController delegate, ConfigurationCache configurationCache) {
        this.model = model;
        this.delegate = delegate;
        this.configurationCache = configurationCache;
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        if (maybeLoadFromCache()) {
            return model.getSettings();
        } else {
            return delegate.getLoadedSettings();
        }
    }

    @Override
    public GradleInternal getConfiguredModel() {
        if (maybeLoadFromCache()) {
            throw new IllegalStateException("Cannot query configured model when model has been loaded from configuration cache.");
        } else {
            return delegate.getConfiguredModel();
        }
    }

    @Override
    public void scheduleTasks(Iterable<String> tasks) {
        if (maybeLoadFromCache()) {
            throw new IllegalStateException("Cannot schedule specific tasks when model has been loaded from configuration cache.");
        } else {
            delegate.scheduleTasks(tasks);
        }
    }

    @Override
    public void scheduleRequestedTasks() {
        if (!maybeLoadFromCache()) {
            delegate.scheduleRequestedTasks();
            configurationCache.save();
        } // Else, already scheduled
    }

    private boolean maybeLoadFromCache() {
        synchronized (this) {
            switch (state) {
                case Created:
                    if (configurationCache.canLoad()) {
                        configurationCache.load();
                        state = State.Loaded;
                        return true;
                    } else {
                        configurationCache.prepareForConfiguration();
                        state = State.DoNotReuse;
                        return false;
                    }
                case Loaded:
                    return true;
                case DoNotReuse:
                    return false;
                default:
                    throw new IllegalStateException();
            }
        }
    }
}
