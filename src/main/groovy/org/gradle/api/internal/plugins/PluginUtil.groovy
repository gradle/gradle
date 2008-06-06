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
 
package org.gradle.api.internal.plugins

import org.gradle.api.plugins.Convention
import org.slf4j.LoggerFactory
import org.slf4j.Logger

/**
 * @author Hans Dockter
 */
class PluginUtil {
    static Logger logger = LoggerFactory.getLogger(PluginUtil)
    
    static void applyCustomValues(Convention convention, def newPluginConvention, Map customValues) {
        customValues.each { String key, value ->
            if (convention.plugins.keySet().contains(key) && value instanceof Map) {
                println key
                println value
                value.each { String property, propertyValue ->
                    convention.plugins[key]."$property" = propertyValue
                }
                return
            }
            try {
                newPluginConvention."$key" = value
                return
            } catch (MissingPropertyException ) {
                logger.debug("Property $key is not found in new plugin convention object of type: ${newPluginConvention.getClass()}")
            }
            convention."$key" = value
        }
    }
}
