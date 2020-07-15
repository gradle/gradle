/*
 * Copyright 2019 the original author or authors.
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

public class DefaultSettingsPreparer implements SettingsPreparer {
    private final InitScriptHandler initScriptHandler;
    private final SettingsLoaderFactory settingsLoaderFactory;

    public DefaultSettingsPreparer(InitScriptHandler initScriptHandler, SettingsLoaderFactory settingsLoaderFactory) {
        this.initScriptHandler = initScriptHandler;
        this.settingsLoaderFactory = settingsLoaderFactory;
    }

    @Override
    public void prepareSettings(GradleInternal gradle) {
        // Evaluate init scripts
        initScriptHandler.executeScripts(gradle);
        // Build `buildSrc`, load settings.gradle, and construct composite (if appropriate)
        SettingsLoader settingsLoader = gradle.isRootBuild() ? settingsLoaderFactory.forTopLevelBuild() : settingsLoaderFactory.forNestedBuild();
        settingsLoader.findAndLoadSettings(gradle);
    }
}
