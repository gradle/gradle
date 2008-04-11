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

import org.gradle.api.DependencyManager
import org.gradle.api.DependencyManagerFactory
import org.gradle.api.GradleScriptException
import org.gradle.api.Project
import org.gradle.initialization.DefaultSettings
import org.gradle.util.GradleUtil
import org.gradle.util.PathHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.gradle.api.internal.project.ImportsReader

/**
* @author Hans Dockter
*/
class SettingsProcessor {
    private static  Logger logger = LoggerFactory.getLogger(SettingsProcessor)

    final static String DEFAULT_SETUP_FILE = "gradlesettings"

    SettingsFileHandler settingsFileHandler

    ImportsReader importsReader

    SettingsFactory settingsFactory

    DependencyManagerFactory dependencyManagerFactory

    BuildSourceBuilder buildSourceBuilder

    File gradleUserHomeDir

    File buildResolverDir

    SettingsProcessor() {

    }

    SettingsProcessor(SettingsFileHandler settingsFileHandler, ImportsReader importsReader, SettingsFactory settingsFactory,
                      DependencyManagerFactory dependencyManagerFactory,
                      BuildSourceBuilder buildSourceBuilder, File gradleUserHomeDir, File buildResolverDir) {
        this.settingsFileHandler = settingsFileHandler
        this.importsReader = importsReader
        this.settingsFactory = settingsFactory
        this.dependencyManagerFactory = dependencyManagerFactory
        this.buildSourceBuilder = buildSourceBuilder
        this.gradleUserHomeDir = gradleUserHomeDir
        this.buildResolverDir = buildResolverDir
    }

    DefaultSettings process(File currentDir, boolean searchUpwards) {
        settingsFileHandler.find(currentDir, searchUpwards)
        initDependencyManagerFactory()
        DefaultSettings settings = settingsFactory.createSettings(currentDir, settingsFileHandler.rootDir,
                dependencyManagerFactory, buildSourceBuilder, gradleUserHomeDir)
        Map importsResult = importsReader.getImports(settingsFileHandler.rootDir)
        try {
            Script settingsScript = new GroovyShell().parse(importsResult.text + settingsFileHandler.settingsText,
                    DEFAULT_SETUP_FILE)
            replaceMetaclass(settingsScript, settings)
            settingsScript.run()
        } catch (Throwable t) {
            throw new GradleScriptException(t, DEFAULT_SETUP_FILE, importsResult.importsLineCount)
        }
        if (currentDir != settingsFileHandler.rootDir && !isCurrentDirIncluded(settings)) {
            return createBasicSettings(currentDir)
        }
        settings
    }

    private def initDependencyManagerFactory() {
        File buildResolverDir = this.buildResolverDir ?: new File(settingsFileHandler.rootDir, DependencyManager.BUILD_RESOLVER_NAME)
        GradleUtil.deleteDir(buildResolverDir)
        dependencyManagerFactory.buildResolverDir = buildResolverDir
        logger.debug("Set build resolver dir to: $dependencyManagerFactory.buildResolverDir")

    }

    DefaultSettings createBasicSettings(File currentDir) {
        initDependencyManagerFactory()
        return settingsFactory.createSettings(currentDir, currentDir, dependencyManagerFactory, buildSourceBuilder, gradleUserHomeDir)
    }

    private void replaceMetaclass(Script script, DefaultSettings settings) {
        ExpandoMetaClass projectScriptExpandoMetaclass = new ExpandoMetaClass(script.class, false)
        projectScriptExpandoMetaclass.methodMissing = {String name, args ->
            logger.debug("Method $name not found in script! Delegating to settings.")
            settings.invokeMethod(name, args)
        }
        projectScriptExpandoMetaclass.initialize()
        script.metaClass = projectScriptExpandoMetaclass
    }

    private boolean isCurrentDirIncluded(DefaultSettings settings) {
        settings.projectPaths.collect {Project.PATH_SEPARATOR + "$it" as String}.contains(
                PathHelper.getCurrentProjectPath(settings.rootDir, settings.currentDir))
    }
}