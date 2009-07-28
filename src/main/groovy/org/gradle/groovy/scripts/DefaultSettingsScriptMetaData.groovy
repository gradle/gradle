/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.groovy.scripts;

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.gradle.api.initialization.Settings

/**
 * @author Hans Dockter
 */
class DefaultSettingsScriptMetaData implements ISettingsScriptMetaData {
    Logger logger = LoggerFactory.getLogger(DefaultSettingsScriptMetaData)
    
    public void applyMetaData(Script script, Settings settings) {
        ExpandoMetaClass settingsScriptExpandoMetaclass = new ExpandoMetaClass(script.class, false)
        settingsScriptExpandoMetaclass.methodMissing = {String name, args ->
            logger.debug("Method {} not found in script! Delegating to settings.", name)
            settings.invokeMethod(name, args)
        }
        settingsScriptExpandoMetaclass.propertyMissing = {String name ->
            if (name == 'out') {
                return System.out
            }
            logger.debug("Property {} not found in script! Delegating to settings.", name)
            settings."$name"
        }
        settingsScriptExpandoMetaclass.initialize()
        script.metaClass = settingsScriptExpandoMetaclass
    }
}
