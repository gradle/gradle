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

package org.gradle.api.plugins.quality.internal;

import com.google.common.collect.ImmutableMap;
import groovy.namespace.QName;
import groovy.util.Node;
import groovy.util.NodeList;
import groovy.xml.XmlParser;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.exceptions.MarkedVerificationException;
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.provider.Property;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.util.GradleVersion;
import org.gradle.util.internal.GFileUtils;
import org.gradle.util.internal.VersionNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

class CheckstyleInvoker implements Action<AntBuilderDelegate> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CheckstyleInvoker.class);

    private static final String FAILURE_PROPERTY_NAME = "org.gradle.checkstyle.violations";
    private static final String CONFIG_LOC_PROPERTY = "config_loc";

    private final CheckstyleActionParameters parameters;

    CheckstyleInvoker(CheckstyleActionParameters parameters) {
        this.parameters = parameters;
    }

    @Override
    public void execute(AntBuilderDelegate ant) {
        FileTree source = parameters.getSource().getAsFileTree();
        boolean showViolations = parameters.getShowViolations().get();
        int maxErrors = parameters.getMaxErrors().get();
        int maxWarnings = parameters.getMaxWarnings().get();
        Map<String, Object> configProperties = parameters.getConfigProperties().get();
        boolean ignoreFailures = parameters.getIgnoreFailures().get();
        RegularFile config = parameters.getConfig().get();
        File configDir = parameters.getConfigDirectory().getAsFile().get();
        boolean isXmlRequired = parameters.getIsXmlRequired().get();
        boolean isHtmlRequired = parameters.getIsHtmlRequired().get();
        boolean isSarifRequired = parameters.getIsSarifRequired().get();
        File xmlOutputLocation = getXmlOutputLocation(isXmlRequired, isHtmlRequired);
        Property<String> stylesheetString = parameters.getStylesheetString();
        File htmlOutputLocation = parameters.getHtmlOutputLocation().getAsFile().getOrNull();
        File sarifOutputLocation = parameters.getSarifOutputLocation().getAsFile().getOrNull();
        VersionNumber currentToolVersion = determineCheckstyleVersion(Thread.currentThread().getContextClassLoader());

        // User provided their own config_loc
        Object userProvidedConfigLoc = configProperties.get(CONFIG_LOC_PROPERTY);
        if (userProvidedConfigLoc != null) {
            throw new InvalidUserDataException("Cannot add config_loc to checkstyle.configProperties. Please configure the configDirectory on the checkstyle task instead.");
        }

        if (isSarifRequired && !isSarifSupported(currentToolVersion)) {
            assertUnsupportedReportFormatSARIF(currentToolVersion);
        }

        try {
            ant.taskdef(ImmutableMap.of(
                "name", "checkstyle",
                "classname", "com.puppycrawl.tools.checkstyle.CheckStyleTask"
            ));
        } catch (RuntimeException ignore) {
            ant.taskdef(ImmutableMap.of(
                "name", "checkstyle",
                "classname", "com.puppycrawl.tools.checkstyle.ant.CheckstyleAntTask"
            ));
        }

        try {
            ant.invokeMethod("checkstyle",
                ImmutableMap.of(
                    "config", config.getAsFile(),
                    "failOnViolation", false,
                    "maxErrors", maxErrors,
                    "maxWarnings", maxWarnings,
                    "failureProperty", FAILURE_PROPERTY_NAME
                ), () -> {
                    source.addToAntBuilder(ant, "fileset", FileCollection.AntType.FileSet);

                    if (showViolations) {
                        ant.invokeMethod("formatter", ImmutableMap.of("type", "plain", "useFile", false));
                    }

                    if (isXmlRequired || isHtmlRequired) {
                        ant.invokeMethod("formatter", ImmutableMap.of(
                            "type", "xml",
                            "toFile", checkNotNull(xmlOutputLocation, "Xml report output location is required when xml or html report is requested."))
                        );
                    }

                    if (isSarifRequired) {
                        ant.invokeMethod("formatter", ImmutableMap.of(
                            "type", "sarif",
                            "toFile", checkNotNull(sarifOutputLocation, "SARIF report output location is required when SARIF report is requested."))
                        );
                    }

                    configProperties.forEach((key, value) ->
                        ant.invokeMethod("property", ImmutableMap.of("key", key, "value", value.toString()))
                    );

                    ant.invokeMethod("property", ImmutableMap.of("key", CONFIG_LOC_PROPERTY, "value", configDir.toString()));
                });
        } catch (Exception e) {
            throw new CheckstyleInvocationException("An unexpected error occurred configuring and executing Checkstyle.", e);
        }

        if (isHtmlRequired) {
            String stylesheet = stylesheetString.isPresent()
                ? stylesheetString.get()
                : readText(Checkstyle.class.getClassLoader().getResourceAsStream("checkstyle-noframes-sorted.xsl"));
            ant.invokeMethod("xslt", ImmutableMap.of("in", checkNotNull(xmlOutputLocation), "out", checkNotNull(htmlOutputLocation)), () -> {
                ant.invokeMethod("param", ImmutableMap.of("name", "gradleVersion", "expression", GradleVersion.current().toString()));
                ant.invokeMethod("style", () ->
                    ant.invokeMethod("string", ImmutableMap.of("value", stylesheet))
                );
            });
        }

        if (isHtmlReportEnabledOnly(isXmlRequired, isHtmlRequired)) {
            GFileUtils.deleteQuietly(xmlOutputLocation);
        }

        Node reportXml = parseCheckstyleXml(isXmlRequired, xmlOutputLocation);
        String message = getMessage(isXmlRequired, xmlOutputLocation, isHtmlRequired, htmlOutputLocation, isSarifRequired, sarifOutputLocation, reportXml);
        boolean hasAFailure = ant.getProjectProperties().get(FAILURE_PROPERTY_NAME) != null;
        if (hasAFailure && !ignoreFailures) {
            throw new MarkedVerificationException(message);
        } else {
            if (violationsExist(reportXml)) {
                LOGGER.warn(message);
            }
        }
    }

    @Nullable
    private File getXmlOutputLocation(boolean isXmlRequired, boolean isHtmlRequired) {
        File xmlOutputLocation = parameters.getXmlOutputLocation().getAsFile().getOrNull();
        if (isHtmlReportEnabledOnly(isXmlRequired, isHtmlRequired)) {
            checkNotNull(xmlOutputLocation, "Xml report output location is required when html report is requested.");
            return new File(parameters.getTemporaryDir().getAsFile().get(), xmlOutputLocation.getName());
        }
        return xmlOutputLocation;
    }

    private static VersionNumber determineCheckstyleVersion(ClassLoader antLoader) {
        Class<?> checkstyleTaskClass;
        try {
            checkstyleTaskClass = antLoader.loadClass("com.puppycrawl.tools.checkstyle.CheckStyleTask");
        } catch (ClassNotFoundException e) {
            try {
                checkstyleTaskClass = antLoader.loadClass("com.puppycrawl.tools.checkstyle.ant.CheckstyleAntTask");
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(ex);
            }
        }
        return VersionNumber.parse(checkstyleTaskClass.getPackage().getImplementationVersion());
    }

    private static boolean isSarifSupported(VersionNumber versionNumber) {
        return versionNumber.compareTo(VersionNumber.parse("10.3.3")) >= 0;
    }

    private static void assertUnsupportedReportFormatSARIF(VersionNumber version) {
        throw new GradleException("SARIF report format is supported on Checkstyle versions 10.3.3 and newer. Please upgrade from Checkstyle " + version +" or disable the SARIF format.");
    }

    @Nullable
    private static Node parseCheckstyleXml(Boolean isXmlRequired, File xmlOutputLocation) {
        try {
            return isXmlRequired ? new XmlParser().parse(xmlOutputLocation) : null;
        } catch (IOException | SAXException | ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getMessage(Boolean isXmlRequired, File xmlOutputLocation, Boolean isHtmlRequired, File htmlOutputLocation, Boolean isSarifRequired, File sarifOutputLocation, Node reportXml) {
        return String.format("Checkstyle rule violations were found.%s%s",
            getReportUrlMessage(isXmlRequired, xmlOutputLocation, isHtmlRequired, htmlOutputLocation, isSarifRequired, sarifOutputLocation),
            getViolationMessage(reportXml)
        );
    }

    private static String getReportUrlMessage(Boolean isXmlRequired, File xmlOutputLocation, Boolean isHtmlRequired, File htmlOutputLocation, Boolean isSarifRequired, File sarifOutputLocation) {
        File outputLocation;
        if (isHtmlRequired) {
            outputLocation = htmlOutputLocation;
        } else if (isXmlRequired) {
            outputLocation = xmlOutputLocation;
        } else if (isSarifRequired) {
            outputLocation = sarifOutputLocation;
        } else {
            outputLocation = null;
        }
        return outputLocation != null ? String.format(" See the report at: %s", new ConsoleRenderer().asClickableFileUrl(outputLocation)) : "\n";
    }

    private static String getViolationMessage(@Nullable Node reportXml) {
        if (violationsExist(reportXml)) {
            int errorFileCount = getErrorFileCount(reportXml);
            List<String> violations = getViolations(reportXml);
            return String.format("\n" +
                "Checkstyle files with violations: %s\n" +
                "Checkstyle violations by severity: %s\n",
                errorFileCount,
                violations
            );
        }
        return "\n";
    }

    private static String readText(InputStream stream) {
        try {
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> getViolations(Node reportXml) {
        @SuppressWarnings("unchecked")
        List<Node> errorNodes = reportXml.getAt(QName.valueOf("file")).getAt("error");
        return errorNodes.stream()
            .map(node -> (String) node.attribute("severity"))
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            .entrySet().stream()
            .map(entry -> entry.getKey() + ":" + entry.getValue())
            .collect(Collectors.toList());
    }

    private static boolean violationsExist(@Nullable Node reportXml) {
        return reportXml != null && getErrorFileCount(reportXml) > 0;
    }

    private static int getErrorFileCount(Node reportXml) {
        int count = 0;
        for (Object node : reportXml.getAt(QName.valueOf("file"))) {
            NodeList errors = ((Node) node).getAt(QName.valueOf("error"));
            if (!errors.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static boolean isHtmlReportEnabledOnly(boolean isXmlRequired, boolean isHtmlRequired) {
        return !isXmlRequired && isHtmlRequired;
    }
}
