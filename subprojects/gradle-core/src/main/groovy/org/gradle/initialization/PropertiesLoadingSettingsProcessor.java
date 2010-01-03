/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.StartParameter;

import java.net.URLClassLoader;

public class PropertiesLoadingSettingsProcessor implements SettingsProcessor {
    private final SettingsProcessor processor;

    public PropertiesLoadingSettingsProcessor(SettingsProcessor processor) {
        this.processor = processor;
    }

    public SettingsInternal process(GradleInternal gradle,
                                    SettingsLocation settingsLocation,
                                    URLClassLoader buildSourceClassLoader,
                                    StartParameter startParameter,
                                    IGradlePropertiesLoader propertiesLoader) {
        propertiesLoader.loadProperties(settingsLocation.getSettingsDir(), startParameter);
        return processor.process(gradle, settingsLocation, buildSourceClassLoader, startParameter, propertiesLoader);
    }
}
