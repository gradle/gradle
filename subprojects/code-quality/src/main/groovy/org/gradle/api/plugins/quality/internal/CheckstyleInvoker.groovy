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
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.util.GFileUtils

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
        def configDir = checkstyleTask.configDirectory.getAsFile().getOrNull()
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

                configProperties.each { key, value ->
                    property(key: key, value: value.toString())
                }

                if (configDir) {
                    // User provided their own config_loc
                    def userProvidedConfigLoc = configProperties[CONFIG_LOC_PROPERTY]
                    if (userProvidedConfigLoc) {
                        DeprecationLogger.deprecateIndirectUsage("Adding 'config_loc' to checkstyle.configProperties")
                            .withAdvice("This property is now ignored and the value of configDirectory is always used for 'config_loc'.")
                            .willBeRemovedInGradle7()
                            .withUpgradeGuideSection(5, "user_provided_config_loc_properties_are_ignored_by_checkstyle")
                            .nagUser()
                    }
                    // Use configDir for config_loc
                    property(key: CONFIG_LOC_PROPERTY, value: configDir.toString())
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

            if (ant.project.properties[FAILURE_PROPERTY_NAME] && !ignoreFailures) {
                throw new GradleException(getMessage(reports, parseCheckstyleXml(reports)))
            } else {
                def reportXml = parseCheckstyleXml(reports)
                if (violationsExist(reportXml)) {
                    logger.warn(getMessage(reports, reportXml))
                }
            }
        }
    }

    private static boolean violationsExist(Node reportXml) {
        return reportXml != null && getErrorFileCount(reportXml) > 0
    }

    private static parseCheckstyleXml(CheckstyleReports reports) {
        return reports.xml.enabled ? new XmlParser().parse(reports.xml.destination) : null
    }

    private static String getMessage(CheckstyleReports reports, Node reportXml) {
        return "Checkstyle rule violations were found.${getReportUrlMessage(reports)}${getViolationMessage(reportXml)}"
    }

    private static int getErrorFileCount(Node reportXml) {
        return reportXml.file.error.groupBy { it.parent().@name }.keySet().size()
    }

    private static String getReportUrlMessage(CheckstyleReports reports) {
        def report = reports.html.enabled ? reports.html : reports.xml.enabled ? reports.xml : null
        return report ? " See the report at: ${new ConsoleRenderer().asClickableFileUrl(report.destination)}" : "\n"
    }

    private static String getViolationMessage(Node reportXml) {
        if (violationsExist(reportXml)) {
            def errorFileCount = getErrorFileCount(reportXml)
            def violations = reportXml.file.error.countBy { it.@severity }
            return """
                    Checkstyle files with violations: $errorFileCount
                    Checkstyle violations by severity: ${violations}
                    """.stripIndent()
        }
        return "\n"
    }

    private static boolean isHtmlReportEnabledOnly(CheckstyleReports reports) {
        return !reports.xml.enabled && reports.html.enabled
    }
}
