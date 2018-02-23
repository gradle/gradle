/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleReports
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.util.GFileUtils
import org.gradle.util.SingleMessageLogger

abstract class CheckstyleInvoker {
    private final static String FAILURE_PROPERTY_NAME = 'org.gradle.checkstyle.violations'
    private final static String CONFIG_LOC_PROPERTY = "config_loc"

    static void invoke(Checkstyle checkstyleTask) {
        def antBuilder = checkstyleTask.antBuilder
        def checkstyleClasspath = checkstyleTask.checkstyleClasspath
        def source = checkstyleTask.source
        def classpath = checkstyleTask.classpath
        def showViolations = checkstyleTask.showViolations
        def maxErrors = checkstyleTask.maxErrors
        def maxWarnings = checkstyleTask.maxWarnings
        def reports = checkstyleTask.reports
        def configProperties = checkstyleTask.configProperties
        def ignoreFailures = checkstyleTask.ignoreFailures
        def logger = checkstyleTask.logger
        def config = checkstyleTask.config
        def configDir = checkstyleTask.configDir
        def xmlDestination = reports.xml.destination

        if (isHtmlReportEnabledOnly(reports)) {
            xmlDestination = new File(checkstyleTask.temporaryDir, reports.xml.destination.name)
        }

        antBuilder.withClasspath(checkstyleClasspath).execute {
            try {
                ant.taskdef(name: 'checkstyle', classname: 'com.puppycrawl.tools.checkstyle.CheckStyleTask')
            } catch (RuntimeException ignore) {
                ant.taskdef(name: 'checkstyle', classname: 'com.puppycrawl.tools.checkstyle.ant.CheckstyleAntTask')
            }

            ant.checkstyle(config: config.asFile(), failOnViolation: false,
                    maxErrors: maxErrors, maxWarnings: maxWarnings, failureProperty: FAILURE_PROPERTY_NAME) {

                source.addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)
                classpath.addToAntBuilder(ant, 'classpath')

                if (showViolations) {
                    formatter(type: 'plain', useFile: false)
                }

                if (reports.xml.enabled || reports.html.enabled) {
                    formatter(type: 'xml', toFile: xmlDestination)
                }

                // User provided their own config_loc
                def userProvidedConfigLoc = configProperties[CONFIG_LOC_PROPERTY]

                if (userProvidedConfigLoc) {
                    SingleMessageLogger.nagUserOfDeprecated("Adding 'config_loc' to checkstyle.configProperties", "Use checkstyle.configDir instead as this will behave better with up-to-date checks")
                } else if (configDir) {
                    // Use configDir for config_loc
                    property(key: CONFIG_LOC_PROPERTY, value: configDir.toString())
                }

                configProperties.each { key, value ->
                    property(key: key, value: value.toString())
                }
            }

            if (reports.html.enabled) {
                def stylesheet = reports.html.stylesheet ? reports.html.stylesheet.asString() :
                    Checkstyle.getClassLoader().getResourceAsStream('checkstyle-noframes-sorted.xsl').text
                ant.xslt(in: xmlDestination, out: reports.html.destination) {
                    style {
                        string(value: stylesheet)
                    }
                }
            }

            if (isHtmlReportEnabledOnly(reports)) {
                GFileUtils.deleteQuietly(xmlDestination)
            }

            if (ant.project.properties[FAILURE_PROPERTY_NAME]) {
                def message = "Checkstyle rule violations were found."
                def report = reports.html.enabled ? reports.html : reports.xml.enabled ? reports.xml : null
                if (report) {
                    def reportUrl = new ConsoleRenderer().asClickableFileUrl(report.destination)
                    message += " See the report at: $reportUrl"
                }
                if (ignoreFailures) {
                    logger.warn(message)
                } else {
                    throw new GradleException(message)
                }
            }
        }
    }

    private static boolean isHtmlReportEnabledOnly(CheckstyleReports reports) {
        return !reports.xml.enabled && reports.html.enabled
    }
}
