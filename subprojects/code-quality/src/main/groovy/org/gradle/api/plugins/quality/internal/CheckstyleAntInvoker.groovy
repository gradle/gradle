/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.plugins.quality.internal

import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.quality.Checkstyle

class CheckstyleAntInvoker extends Closure<Object> {

    private final static String FAILURE_PROPERTY_NAME = 'org.gradle.checkstyle.violations'

    private Checkstyle.CheckstyleActionParameters parameters

    CheckstyleAntInvoker(Object owner, Object thisObject, Checkstyle.CheckstyleActionParameters parameters) {
        super(owner, thisObject)
        this.parameters = parameters;
    }

    @SuppressWarnings("UnusedDeclaration")
    public Object doCall(Object ant) {

//        def source = checkstyleTask.source
//        def classpath = checkstyleTask.classpath
        def showViolations = parameters.showViolations.get()
        def maxErrors = parameters.maxErrors.get()
        def maxWarnings = parameters.maxWarnings.get()
//        def reports = checkstyleTask.reports
//        def configProperties = checkstyleTask.configProperties
        def ignoreFailures = parameters.ignoreFailures.get()
//        def logger = checkstyleTask.logger
        def config = parameters.config.get()
        def configDir = parameters.configDirectory.asFile.getOrNull()
//        def xmlDestination = reports.xml.outputLocation.asFile.get()

        try {
            ant.taskdef(name: 'checkstyle', classname: 'com.puppycrawl.tools.checkstyle.CheckStyleTask')
        } catch (RuntimeException ignore) {
            ant.taskdef(name: 'checkstyle', classname: 'com.puppycrawl.tools.checkstyle.ant.CheckstyleAntTask')
        }

        ant.checkstyle(config: config.asFile, failOnViolation: false,
            maxErrors: maxErrors, maxWarnings: maxWarnings, failureProperty: FAILURE_PROPERTY_NAME) {

            source.addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)
            classpath.addToAntBuilder(ant, 'classpath')

            if (showViolations) {
                formatter(type: 'plain', useFile: false)
            }

            if (reports.xml.required.get() || reports.html.required.get()) {
                formatter(type: 'xml', toFile: xmlDestination)
            }

            configProperties.each { key, value ->
                property(key: key, value: value.toString())
            }

            if (configDir) {
                // User provided their own config_loc
                def userProvidedConfigLoc = configProperties[CONFIG_LOC_PROPERTY]
                if (userProvidedConfigLoc) {
                    throw new InvalidUserDataException("Cannot add config_loc to checkstyle.configProperties. Please configure the configDirectory on the checkstyle task instead.")
                }
                // Use configDir for config_loc
                property(key: CONFIG_LOC_PROPERTY, value: configDir.toString())
            }
        }

        return null;
    }
}
