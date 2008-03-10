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
import org.gradle.api.GradleScriptException
import org.gradle.api.Project
import org.gradle.initialization.DefaultSettings
import org.gradle.util.PathHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
* @author Hans Dockter
*/
class SettingsProcessor {
    static Logger logger = LoggerFactory.getLogger(SettingsProcessor)
    
    final static String DEFAULT_SETUP_FILE = "gradlesettings.groovy"

    SettingsFileHandler settingsFileHandler

    DependencyManager dependencyManager

    SettingsProcessor() {

    }

    SettingsProcessor(SettingsFileHandler settingsFileHandler, DependencyManager dependencyManager) {
        this.settingsFileHandler = settingsFileHandler
        this.dependencyManager = dependencyManager
    }

    DefaultSettings process(File currentDir, boolean searchUpwards) {
        settingsFileHandler.find(currentDir, searchUpwards)
        DefaultSettings settings = new DefaultSettings(currentDir, settingsFileHandler.rootDir, dependencyManager)
        try {
            Script settingsScript = new GroovyShell().parse(settingsFileHandler.settingsText, DEFAULT_SETUP_FILE)
            replaceMetaclass(settingsScript, settings)
            settingsScript.run()
        } catch (Throwable t) {
            throw new GradleScriptException(t, DEFAULT_SETUP_FILE)
        }
        if (currentDir != settings.rootDir && !isCurrentDirIncluded(settings)) {
            return new DefaultSettings(currentDir, currentDir, dependencyManager)
        }
        settings
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