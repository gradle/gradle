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
import org.gradle.configuration.ScriptPlugin;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.util.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;


/**
 * @author Hans Dockter
 */
public class ScriptEvaluatingSettingsProcessor implements SettingsProcessor {
    private static Logger logger = LoggerFactory.getLogger(ScriptEvaluatingSettingsProcessor.class);

    private final SettingsFactory settingsFactory;
    private final IGradlePropertiesLoader propertiesLoader;
    private final ScriptPluginFactory configurerFactory;

    public ScriptEvaluatingSettingsProcessor(ScriptPluginFactory configurerFactory,
                                             SettingsFactory settingsFactory,
                                             IGradlePropertiesLoader propertiesLoader) {
        this.configurerFactory = configurerFactory;
        this.settingsFactory = settingsFactory;
        this.propertiesLoader = propertiesLoader;
    }

    public SettingsInternal process(GradleInternal gradle,
                                    SettingsLocation settingsLocation,
                                    ClassLoader buildSourceClassLoader,
                                    StartParameter startParameter) {
        Clock settingsProcessingClock = new Clock();
        Map<String, String> properties = propertiesLoader.mergeProperties(Collections.<String, String>emptyMap());
        SettingsInternal settings = settingsFactory.createSettings(gradle, settingsLocation.getSettingsDir(),
                settingsLocation.getSettingsScriptSource(), properties, startParameter, buildSourceClassLoader);
        applySettingsScript(settingsLocation, buildSourceClassLoader, settings);
        logger.debug("Timing: Processing settings took: {}", settingsProcessingClock.getTime());
        return settings;
    }

    private void applySettingsScript(SettingsLocation settingsLocation, ClassLoader buildSourceClassLoader, SettingsInternal settings) {
        ScriptPlugin configurer = configurerFactory.create(settingsLocation.getSettingsScriptSource());
        configurer.setClassLoader(buildSourceClassLoader);
        configurer.setScriptBaseClass(SettingsScript.class);
        configurer.apply(settings);
    }
}
