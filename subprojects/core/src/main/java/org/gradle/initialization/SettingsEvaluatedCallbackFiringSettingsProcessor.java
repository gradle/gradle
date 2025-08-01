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

package org.gradle.initialization;

import org.gradle.StartParameter;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.internal.initialization.BuildLocations;

public class SettingsEvaluatedCallbackFiringSettingsProcessor implements SettingsProcessor {

    private final SettingsProcessor delegate;

    public SettingsEvaluatedCallbackFiringSettingsProcessor(SettingsProcessor delegate) {
        this.delegate = delegate;
    }

    @Override
    public SettingsState process(GradleInternal gradle, BuildLocations buildLocations, ClassLoaderScope buildRootClassLoaderScope, StartParameter startParameter) {
        SettingsState state = delegate.process(gradle, buildLocations, buildRootClassLoaderScope, startParameter);
        SettingsInternal settings = state.getSettings();
        gradle.getBuildListenerBroadcaster().settingsEvaluated(settings);
        settings.preventFromFurtherMutation();
        return state;
    }
}
