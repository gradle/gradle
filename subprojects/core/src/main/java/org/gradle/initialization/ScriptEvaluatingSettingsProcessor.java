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

import org.gradle.StartParameter;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.configuration.ScriptApplicator;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

import static org.gradle.configuration.ScriptApplicator.Extensions.applyScriptTo;


public class ScriptEvaluatingSettingsProcessor implements SettingsProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptEvaluatingSettingsProcessor.class);

    private final SettingsFactory settingsFactory;
    private final IGradlePropertiesLoader propertiesLoader;
    private final ScriptApplicator scriptApplicator;

    public ScriptEvaluatingSettingsProcessor(SettingsFactory settingsFactory,
                                             IGradlePropertiesLoader propertiesLoader,
                                             ScriptApplicator scriptApplicator) {
        this.settingsFactory = settingsFactory;
        this.propertiesLoader = propertiesLoader;
        this.scriptApplicator = scriptApplicator;
    }

    public SettingsInternal process(GradleInternal gradle,
                                    SettingsLocation settingsLocation,
                                    ClassLoaderScope buildRootClassLoaderScope,
                                    StartParameter startParameter) {
        Timer timer = Time.startTimer();

        SettingsInternal settings = createSettings(gradle, settingsLocation, buildRootClassLoaderScope, startParameter);
        applySettingsScript(settingsLocation, settings);

        LOGGER.debug("Timing: Processing settings took: {}", timer.getElapsed());
        return settings;
    }

    private SettingsInternal createSettings(GradleInternal gradle, SettingsLocation settingsLocation, ClassLoaderScope buildRootClassLoaderScope, StartParameter startParameter) {
        return settingsFactory.createSettings(gradle, settingsLocation.getSettingsDir(), settingsLocation.getSettingsScriptSource(), loadProperties(), startParameter, buildRootClassLoaderScope);
    }

    private void applySettingsScript(SettingsLocation settingsLocation, SettingsInternal settings) {
        applyScriptTo(
            settings,
            scriptApplicator,
            settingsLocation.getSettingsScriptSource(),
            settings.getClassLoaderScope(),
            settings.getRootClassLoaderScope(),
            true);
    }

    private Map<String, String> loadProperties() {
        return propertiesLoader.mergeProperties(Collections.<String, String>emptyMap());
    }

}
