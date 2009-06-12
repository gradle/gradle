/*
 * Copyright 2007 the original author or authors.
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

import groovy.lang.Script;
import org.gradle.StartParameter;
import org.gradle.api.GradleScriptException;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.project.ImportsReader;
import org.gradle.groovy.scripts.ScriptProcessorFactory;
import org.gradle.groovy.scripts.*;
import org.gradle.util.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Hans Dockter
 */
public class ScriptEvaluatingSettingsProcessor implements SettingsProcessor {
    private static Logger logger = LoggerFactory.getLogger(ScriptEvaluatingSettingsProcessor.class);

    private ImportsReader importsReader;

    private SettingsFactory settingsFactory;

    private ScriptProcessorFactory scriptProcessorFactory;

    private ISettingsScriptMetaData settingsScriptMetaData;

    public ScriptEvaluatingSettingsProcessor() {

    }

    public ScriptEvaluatingSettingsProcessor(ISettingsScriptMetaData settingsScriptMetaData,
                                             ScriptProcessorFactory scriptProcessorFactory, ImportsReader importsReader,
                                             SettingsFactory settingsFactory) {
        this.settingsScriptMetaData = settingsScriptMetaData;
        this.scriptProcessorFactory = scriptProcessorFactory;
        this.importsReader = importsReader;
        this.settingsFactory = settingsFactory;
    }

    public SettingsInternal process(ISettingsFinder settingsFinder, StartParameter startParameter,
                                    IGradlePropertiesLoader propertiesLoader) {
        Clock settingsProcessingClock = new Clock();
        SettingsInternal settings = settingsFactory.createSettings(settingsFinder.getSettingsDir(),
                settingsFinder.getSettingsScriptSource(), propertiesLoader.getGradleProperties(), startParameter);
        applySettingsScript(settingsFinder, settings);
        logger.debug("Timing: Processing settings took: {}", settingsProcessingClock.getTime());
        return settings;
    }

    private void applySettingsScript(ISettingsFinder settingsFinder, SettingsInternal settings) {
        ScriptSource source = new ImportsScriptSource(settingsFinder.getSettingsScriptSource(), importsReader,
                settingsFinder.getSettingsDir());
        try {
            Script settingsScript = scriptProcessorFactory.createProcessor(source).process(ScriptWithSource.class);
            settingsScriptMetaData.applyMetaData(settingsScript, settings);
            Clock clock = new Clock();
            settingsScript.run();
            logger.debug("Timing: Evaluating settings file took: {}", clock.getTime());
        } catch (Throwable t) {
            throw new GradleScriptException("A problem occurred evaluating the settings file.", t, source);
        }
    }

    public ImportsReader getImportsReader() {
        return importsReader;
    }

    public void setImportsReader(ImportsReader importsReader) {
        this.importsReader = importsReader;
    }

    public SettingsFactory getSettingsFactory() {
        return settingsFactory;
    }

    public void setSettingsFactory(SettingsFactory settingsFactory) {
        this.settingsFactory = settingsFactory;
    }

    public void setScriptProcessor(ScriptProcessorFactory scriptProcessorFactory) {
        this.scriptProcessorFactory = scriptProcessorFactory;
    }

    public ScriptProcessorFactory getScriptProcessor() {
        return scriptProcessorFactory;
    }

    public ISettingsScriptMetaData getSettingsScriptMetaData() {
        return settingsScriptMetaData;
    }

    public void setSettingsScriptMetaData(ISettingsScriptMetaData settingsScriptMetaData) {
        this.settingsScriptMetaData = settingsScriptMetaData;
    }
}