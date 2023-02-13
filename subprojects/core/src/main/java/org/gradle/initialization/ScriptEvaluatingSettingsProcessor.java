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
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.configuration.ScriptPlugin;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.groovy.scripts.TextResourceScriptSource;
import org.gradle.internal.resource.TextFileResourceLoader;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ScriptEvaluatingSettingsProcessor implements SettingsProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptEvaluatingSettingsProcessor.class);

    private final SettingsFactory settingsFactory;
    private final GradleProperties gradleProperties;
    private final ScriptPluginFactory configurerFactory;
    private final TextFileResourceLoader textFileResourceLoader;

    public ScriptEvaluatingSettingsProcessor(
        ScriptPluginFactory configurerFactory,
        SettingsFactory settingsFactory,
        GradleProperties gradleProperties,
        TextFileResourceLoader textFileResourceLoader
    ) {
        this.configurerFactory = configurerFactory;
        this.settingsFactory = settingsFactory;
        this.gradleProperties = gradleProperties;
        this.textFileResourceLoader = textFileResourceLoader;
    }

    @Override
    public SettingsState process(
        GradleInternal gradle,
        SettingsLocation settingsLocation,
        ClassLoaderScope baseClassLoaderScope,
        StartParameter startParameter
    ) {
        Timer settingsProcessingClock = Time.startTimer();
        TextResourceScriptSource settingsScript = new TextResourceScriptSource(textFileResourceLoader.loadFile("settings file", settingsLocation.getSettingsFile()));
        SettingsState state = settingsFactory.createSettings(gradle, settingsLocation.getSettingsDir(), settingsScript, gradleProperties, startParameter, baseClassLoaderScope);

        SettingsInternal settings = state.getSettings();
        gradle.getBuildListenerBroadcaster().beforeSettings(settings);
        settings.getCaches().finalizeConfiguration(gradle);
        applySettingsScript(settingsScript, settings);
        LOGGER.debug("Timing: Processing settings took: {}", settingsProcessingClock.getElapsed());
        return state;
    }

    private void applySettingsScript(TextResourceScriptSource settingsScript, final SettingsInternal settings) {
        ScriptPlugin configurer = configurerFactory.create(settingsScript, settings.getBuildscript(), settings.getClassLoaderScope(), settings.getBaseClassLoaderScope(), true);
        configurer.apply(settings);
    }
}
