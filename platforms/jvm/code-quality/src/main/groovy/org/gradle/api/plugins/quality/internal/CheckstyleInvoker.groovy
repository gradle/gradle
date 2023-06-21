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

import groovy.xml.XmlParser
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.exceptions.MarkedVerificationException
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.util.internal.GFileUtils
import org.gradle.util.internal.VersionNumber
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class CheckstyleInvoker implements Action<AntBuilderDelegate> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CheckstyleInvoker.class)

    private static final String FAILURE_PROPERTY_NAME = "org.gradle.checkstyle.violations"
    private static final String CONFIG_LOC_PROPERTY = "config_loc"

    private final CheckstyleActionParameters parameters

    CheckstyleInvoker(CheckstyleActionParameters parameters) {
        this.parameters = parameters
    }

    @SuppressWarnings("UnusedDeclaration")
    void execute(AntBuilderDelegate ant) {
        def source = parameters.source.asFileTree
        def showViolations = parameters.showViolations.get()
        def maxErrors = parameters.maxErrors.get()
        def maxWarnings = parameters.maxWarnings.get()
        def configProperties = parameters.configProperties.getOrElse([:])
        def ignoreFailures = parameters.ignoreFailures.get()
        def config = parameters.config.get()
        def configDir = parameters.configDirectory.asFile.getOrNull()
        def isXmlRequired = parameters.isXmlRequired.get()
        def isHtmlRequired = parameters.isHtmlRequired.get()
        def isSarifRequired = parameters.isSarifRequired.get()
        def xmlOutputLocation = parameters.xmlOuputLocation.asFile.getOrElse(null)
        def stylesheetString = parameters.stylesheetString
        def htmlOutputLocation = parameters.htmlOuputLocation.asFile.getOrElse(null)
        def sarifOutputLocation = parameters.sarifOutputLocation.asFile.getOrElse(null)
        VersionNumber currentToolVersion = determineCheckstyleVersion(Thread.currentThread().getContextClassLoader())
        def sarifSupported = isSarifSupported(currentToolVersion)

        if (isHtmlReportEnabledOnly(isXmlRequired, isHtmlRequired)) {
            xmlOutputLocation = new File(parameters.temporaryDir.asFile.get(), xmlOutputLocation.name)
        }

        if (configDir) {
            // User provided their own config_loc
            def userProvidedConfigLoc = configProperties[CONFIG_LOC_PROPERTY]
            if (userProvidedConfigLoc) {
                throw new InvalidUserDataException("Cannot add config_loc to checkstyle.configProperties. Please configure the configDirectory on the checkstyle task instead.")
            }
        }

        try {
            ant.taskdef(name: 'checkstyle', classname: 'com.puppycrawl.tools.checkstyle.CheckStyleTask')
        } catch (RuntimeException ignore) {
            ant.taskdef(name: 'checkstyle', classname: 'com.puppycrawl.tools.checkstyle.ant.CheckstyleAntTask')
        }

        try {
            ant.checkstyle(config: config.asFile, failOnViolation: false,
                    maxErrors: maxErrors, maxWarnings: maxWarnings, failureProperty: FAILURE_PROPERTY_NAME) {

                source.addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)

                if (showViolations) {
                    formatter(type: 'plain', useFile: false)
                }

                if (isXmlRequired || isHtmlRequired) {
                    formatter(type: 'xml', toFile: xmlOutputLocation)
                }

                if (isSarifRequired) {
                    if (sarifSupported) {
                        formatter(type: 'sarif', toFile: sarifOutputLocation)
                    } else {
                        assertUnsupportedReportFormatSARIF(currentToolVersion)
                    }
                }

                configProperties.each { key, value ->
                    property(key: key, value: value.toString())
                }

                // Use configDir for config_loc
                property(key: CONFIG_LOC_PROPERTY, value: configDir.toString())
            }
        } catch (Exception e) {
            throw new CheckstyleInvocationException("An unexpected error occurred configuring and executing Checkstyle.", e)
        }

        if (isHtmlRequired) {
            def stylesheet = stylesheetString.isPresent()
                ? stylesheetString.get()
                : Checkstyle.getClassLoader().getResourceAsStream('checkstyle-noframes-sorted.xsl').text
            ant.xslt(in: xmlOutputLocation, out: htmlOutputLocation) {
                style {
                    string(value: stylesheet)
                }
            }
        }

        if (isHtmlReportEnabledOnly(isXmlRequired, isHtmlRequired)) {
            GFileUtils.deleteQuietly(xmlOutputLocation)
        }

        def reportXml = parseCheckstyleXml(isXmlRequired, xmlOutputLocation)

        def message = getMessage(isXmlRequired, xmlOutputLocation, isHtmlRequired, htmlOutputLocation, isSarifRequired, sarifOutputLocation, reportXml)
        if (ant.project.properties[FAILURE_PROPERTY_NAME] && !ignoreFailures) {
            throw new MarkedVerificationException(message)
        } else {
            if (violationsExist(reportXml)) {
                LOGGER.warn(message)
            }
        }
    }

    private static VersionNumber determineCheckstyleVersion(ClassLoader antLoader) {
        Class checkstyleTaskClass
        try {
            checkstyleTaskClass = antLoader.loadClass("com.puppycrawl.tools.checkstyle.CheckStyleTask")
        } catch (ClassNotFoundException e) {
            checkstyleTaskClass = antLoader.loadClass("com.puppycrawl.tools.checkstyle.ant.CheckstyleAntTask")
        }
        return VersionNumber.parse(checkstyleTaskClass.getPackage().getImplementationVersion())
    }

    private static boolean isSarifSupported(VersionNumber versionNumber) {
        return versionNumber >= VersionNumber.parse("10.3.3")
    }

    private static void assertUnsupportedReportFormatSARIF(VersionNumber version) {
        throw new GradleException("SARIF report format is supported on Checkstyle versions 10.3.3 and newer. Please upgrade from Checkstyle " + version +" or disable the SARIF format.")
    }

    private static parseCheckstyleXml(Boolean isXmlRequired, File xmlOutputLocation) {
        return isXmlRequired ? new XmlParser().parse(xmlOutputLocation) : null
    }

    private static String getMessage(Boolean isXmlRequired, File xmlOutputLocation, Boolean isHtmlRequired, File htmlOutputLocation, Boolean isSarifRequired, File sarifOutputLocation, Node reportXml) {
        return "Checkstyle rule violations were found." +
            "${getReportUrlMessage(isXmlRequired, xmlOutputLocation, isHtmlRequired, htmlOutputLocation, isSarifRequired, sarifOutputLocation)}${getViolationMessage(reportXml)}"
    }

    private static String getReportUrlMessage(Boolean isXmlRequired, File xmlOutputLocation, Boolean isHtmlRequired, File htmlOutputLocation, Boolean isSarifRequired, File sarifOutputLocation) {
        File outputLocation
        if (isHtmlRequired) {
            outputLocation = htmlOutputLocation
        } else if (isXmlRequired) {
            outputLocation = xmlOutputLocation
        } else if (isSarifRequired) {
            outputLocation = sarifOutputLocation
        } else {
            outputLocation = null
        }
        return outputLocation ? " See the report at: ${new ConsoleRenderer().asClickableFileUrl(outputLocation)}" : "\n"
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

    private static boolean violationsExist(Node reportXml) {
        return reportXml != null && getErrorFileCount(reportXml) > 0
    }

    private static int getErrorFileCount(Node reportXml) {
        return reportXml.file.error.groupBy { it.parent().@name }.keySet().size()
    }

    private static boolean isHtmlReportEnabledOnly(Boolean isXmlRequired, Boolean isHtmlRequired) {
        return !isXmlRequired && isHtmlRequired
    }
}
