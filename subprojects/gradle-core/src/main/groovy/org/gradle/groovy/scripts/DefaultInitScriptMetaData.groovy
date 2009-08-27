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
import org.gradle.api.invocation.Gradle

/**
 * @author Hans Dockter
 */
class DefaultInitScriptMetaData implements InitScriptMetaData {
    Logger logger = LoggerFactory.getLogger(DefaultInitScriptMetaData)
    
    public void applyMetaData(Script script, Gradle gradle) {
        ExpandoMetaClass initScriptExpandoMetaclass = new ExpandoMetaClass(script.class, false)
        initScriptExpandoMetaclass.methodMissing = {String name, args ->
            logger.debug("Method {} not found in script! Delegating to gradle.", name)
            gradle.invokeMethod(name, args)
        }
        initScriptExpandoMetaclass.propertyMissing = {String name ->
            if (name == 'out') {
                return System.out
            }
            logger.debug("Property {} not found in script! Delegating to gradle.", name)
            gradle."$name"
        }
        initScriptExpandoMetaclass.initialize()
        script.metaClass = initScriptExpandoMetaclass
    }
}
