/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.plugin.devel.internal.precompiled;

import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.SettingsInternal;

public class SettingsPluginAdapter extends PreCompiledScriptRunner {

    public SettingsPluginAdapter(Settings settings, Class<?> precompiledScriptClass, String hashCode) {
        super(settings,
            ((SettingsInternal) settings).getGradle().getServices(),
            ((SettingsInternal) settings).getClassLoaderScope(),
            precompiledScriptClass,
            hashCode);
    }

}
