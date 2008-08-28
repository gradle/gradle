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

package org.gradle.initialization

import org.gradle.StartParameter
import org.gradle.api.DependencyManager
import org.gradle.api.GradleScriptException
import org.gradle.api.Project
import org.gradle.api.Settings
import org.gradle.api.internal.dependencies.DependencyManagerFactory
import org.gradle.api.internal.project.ImportsReader
import org.gradle.groovy.scripts.IScriptProcessor
import org.gradle.groovy.scripts.ISettingsScriptMetaData
import org.gradle.groovy.scripts.ImportsScriptSource
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.initialization.DefaultSettings
import org.gradle.util.Clock
import org.gradle.util.GradleUtil
import org.gradle.util.PathHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.gradle.api.GradleException


/**
* @author Hans Dockter
*/
class SettingsProcessor {
    private static  Logger logger = LoggerFactory.getLogger(SettingsProcessor)

    ImportsReader importsReader

    SettingsFactory settingsFactory

    DependencyManagerFactory dependencyManagerFactory

    BuildSourceBuilder buildSourceBuilder

    File buildResolverDir

    IScriptProcessor scriptProcessor

    ISettingsScriptMetaData settingsScriptMetaData

    SettingsProcessor() {

    }

    SettingsProcessor(ISettingsScriptMetaData settingsScriptMetaData, IScriptProcessor scriptProcessor, ImportsReader importsReader,
                      SettingsFactory settingsFactory, DependencyManagerFactory dependencyManagerFactory,
                      BuildSourceBuilder buildSourceBuilder, File buildResolverDir) {
        this.settingsScriptMetaData = settingsScriptMetaData
        this.scriptProcessor = scriptProcessor
        this.importsReader = importsReader
        this.settingsFactory = settingsFactory
        this.dependencyManagerFactory = dependencyManagerFactory
        this.buildSourceBuilder = buildSourceBuilder
        this.buildResolverDir = buildResolverDir
    }

    DefaultSettings process(RootFinder rootFinder, StartParameter startParameter) {
        Clock settingsProcessingClock = new Clock();
        initDependencyManagerFactory(rootFinder)
        DefaultSettings settings = settingsFactory.createSettings(dependencyManagerFactory, buildSourceBuilder, rootFinder, startParameter)
        ScriptSource source = new ImportsScriptSource(rootFinder.settingsScript, importsReader, rootFinder.rootDir);
        try {
            Script settingsScript = scriptProcessor.createScript(
                    source,
                    Thread.currentThread().contextClassLoader,
                    Script.class)
            settingsScriptMetaData.applyMetaData(settingsScript, settings)
            Clock clock = new Clock();
            settingsScript.run()
            logger.debug("Timing: Evaluating settings file took: {}", clock.time)
        } catch (GradleException e) {
            e.setScriptSource(source)
            throw e
        } catch (Throwable t) {
            throw new GradleScriptException(t, source)
        }
        logger.debug("Timing: Processing settings took: {}", settingsProcessingClock.time)
        if (startParameter.currentDir != rootFinder.rootDir && !isCurrentDirIncluded(settings)) {
            return createBasicSettings(rootFinder, startParameter)
        }
        settings
    }

    private def initDependencyManagerFactory(RootFinder rootFinder) {
        File buildResolverDir = this.buildResolverDir ?: new File(rootFinder.rootDir, Project.TMP_DIR_NAME + "/" +
                DependencyManager.BUILD_RESOLVER_NAME)
        GradleUtil.deleteDir(buildResolverDir)
        dependencyManagerFactory.buildResolverDir = buildResolverDir
        logger.debug("Set build resolver dir to: {}", dependencyManagerFactory.buildResolverDir)

    }

    DefaultSettings createBasicSettings(RootFinder rootFinder, StartParameter startParameter) {
        initDependencyManagerFactory(rootFinder)
        return settingsFactory.createSettings(dependencyManagerFactory, buildSourceBuilder, rootFinder, startParameter)
    }

    

    private boolean isCurrentDirIncluded(DefaultSettings settings) {
        settings.projectPaths.collect {Project.PATH_SEPARATOR + "$it" as String}.contains(
                PathHelper.getCurrentProjectPath(settings.rootFinder.rootDir, settings.startParameter.currentDir))
    }
}