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

import groovy.lang.Script;
import org.gradle.api.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.gradle.api.internal.project.ProjectScript

/**
 * @author Hans Dockter
 */
public class DefaultProjectScriptMetaData implements IProjectScriptMetaData {
    Logger logger = LoggerFactory.getLogger(DefaultProjectScriptMetaData);
    
    public void applyMetaData(Script script, Project project) {
        ExpandoMetaClass projectScriptExpandoMetaclass = new ExpandoMetaClass(script.class, false)
        projectScriptExpandoMetaclass.methodMissing = {String name, args ->
            logger.debug("Project: {} Method {} not found in script! Delegating to project.", project.path, name)
            project.invokeMethod(name, args)
        }
        projectScriptExpandoMetaclass.propertyMissing = {String name ->
            if (name == 'out') {
                return System.out
            }
             logger.debug("Project: {} Method {} not found in script! Delegating to project.", project.path, name)
            project."$name"
        }
        projectScriptExpandoMetaclass.setProperty = {String name, value ->
            logger.debug("Project: {} Property {} set a project property.", project.path, name)
            project."$name" = value
        }
        projectScriptExpandoMetaclass.initialize()
        script.metaClass = projectScriptExpandoMetaclass
    }
}
