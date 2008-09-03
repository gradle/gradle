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
import org.gradle.api.DependencyManager;
import org.gradle.api.GradleException;
import org.gradle.api.GradleScriptException;
import org.gradle.api.Project;
import org.gradle.api.internal.dependencies.DependencyManagerFactory;
import org.gradle.api.internal.project.ImportsReader;
import org.gradle.groovy.scripts.IScriptProcessor;
import org.gradle.groovy.scripts.ISettingsScriptMetaData;
import org.gradle.groovy.scripts.ImportsScriptSource;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.util.Clock;
import org.gradle.util.GUtil;
import org.gradle.util.GradleUtil;
import org.gradle.util.PathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;


/**
* @author Hans Dockter
*/
public class SettingsProcessor {
    private static  Logger logger = LoggerFactory.getLogger(SettingsProcessor.class);

    private ImportsReader importsReader;

    private SettingsFactory settingsFactory;

    private DependencyManagerFactory dependencyManagerFactory;

    private BuildSourceBuilder buildSourceBuilder;

    private File buildResolverDir;

    private IScriptProcessor scriptProcessor;

    private ISettingsScriptMetaData settingsScriptMetaData;

    public SettingsProcessor() {

    }

    public SettingsProcessor(ISettingsScriptMetaData settingsScriptMetaData, IScriptProcessor scriptProcessor, ImportsReader importsReader,
                      SettingsFactory settingsFactory, DependencyManagerFactory dependencyManagerFactory,
                      BuildSourceBuilder buildSourceBuilder, File buildResolverDir) {
        this.settingsScriptMetaData = settingsScriptMetaData;
        this.scriptProcessor = scriptProcessor;
        this.importsReader = importsReader;
        this.settingsFactory = settingsFactory;
        this.dependencyManagerFactory = dependencyManagerFactory;
        this.buildSourceBuilder = buildSourceBuilder;
        this.buildResolverDir = buildResolverDir;
    }

    public DefaultSettings process(ISettingsFinder settingsFinder, StartParameter startParameter) {
        Clock settingsProcessingClock = new Clock();
        initDependencyManagerFactory(settingsFinder);
        DefaultSettings settings = settingsFactory.createSettings(dependencyManagerFactory, buildSourceBuilder, settingsFinder, startParameter);
        ScriptSource source = new ImportsScriptSource(settingsFinder.getSettingsScript(), importsReader, settingsFinder.getSettingsDir());
        try {
            Script settingsScript = scriptProcessor.createScript(
                    source,
                    Thread.currentThread().getContextClassLoader(),
                    Script.class);
            settingsScriptMetaData.applyMetaData(settingsScript, settings);
            Clock clock = new Clock();
            settingsScript.run();
            logger.debug("Timing: Evaluating settings file took: {}", clock.getTime());
        } catch (GradleException e) {
            e.setScriptSource(source);
            throw e;
        } catch (Throwable t) {
            throw new GradleScriptException(t, source);
        }
        logger.debug("Timing: Processing settings took: {}", settingsProcessingClock.getTime());
        if (startParameter.getCurrentDir() != settingsFinder.getSettingsDir() && !isCurrentDirIncluded(settings)) {
            return createBasicSettings(settingsFinder, startParameter);
        }
        return settings;
    }

    private void initDependencyManagerFactory(ISettingsFinder settingsFinder) {
        File buildResolverDir = GUtil.elvis(this.buildResolverDir, new File(settingsFinder.getSettingsDir(), Project.TMP_DIR_NAME + "/" +
                DependencyManager.BUILD_RESOLVER_NAME));
        GradleUtil.deleteDir(buildResolverDir);
        dependencyManagerFactory.setBuildResolverDir(buildResolverDir);
        logger.debug("Set build resolver dir to: {}", dependencyManagerFactory.getBuildResolverDir());
    }

    public DefaultSettings createBasicSettings(ISettingsFinder settingsFinder, StartParameter startParameter) {
        initDependencyManagerFactory(settingsFinder);
        return settingsFactory.createSettings(dependencyManagerFactory, buildSourceBuilder, settingsFinder, startParameter);
    }

    private boolean isCurrentDirIncluded(DefaultSettings settings) {
        settings.getProjectPaths().contains(
                PathHelper.getCurrentProjectPath(settings.getRootDir(), settings.getStartParameter().getCurrentDir()).substring(1));
        return true;
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

    public DependencyManagerFactory getDependencyManagerFactory() {
        return dependencyManagerFactory;
    }

    public void setDependencyManagerFactory(DependencyManagerFactory dependencyManagerFactory) {
        this.dependencyManagerFactory = dependencyManagerFactory;
    }

    public BuildSourceBuilder getBuildSourceBuilder() {
        return buildSourceBuilder;
    }

    public void setBuildSourceBuilder(BuildSourceBuilder buildSourceBuilder) {
        this.buildSourceBuilder = buildSourceBuilder;
    }

    public File getBuildResolverDir() {
        return buildResolverDir;
    }

    public void setBuildResolverDir(File buildResolverDir) {
        this.buildResolverDir = buildResolverDir;
    }

    public IScriptProcessor getScriptProcessor() {
        return scriptProcessor;
    }

    public void setScriptProcessor(IScriptProcessor scriptProcessor) {
        this.scriptProcessor = scriptProcessor;
    }

    public ISettingsScriptMetaData getSettingsScriptMetaData() {
        return settingsScriptMetaData;
    }

    public void setSettingsScriptMetaData(ISettingsScriptMetaData settingsScriptMetaData) {
        this.settingsScriptMetaData = settingsScriptMetaData;
    }
}