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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.util.internal.MavenUtil;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MavenToolchainsInstallationSupplier extends AutoDetectingInstallationSupplier {

    private static final String PROPERTY_NAME = "org.gradle.java.installations.maven-toolchains-file";
    private static final String PARSE_EXPRESSION = "/toolchains/toolchain[type='jdk']/configuration/jdkHome//text()";
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{env\\.([^}]+)\\}");
    private static final Logger LOGGER = Logging.getLogger(MavenToolchainsInstallationSupplier.class);

    private final Provider<String> toolchainLocation;
    private final XPathFactory xPathFactory;
    private final DocumentBuilderFactory documentBuilderFactory;
    private final FileResolver fileResolver;

    @Inject
    public MavenToolchainsInstallationSupplier(ProviderFactory factory, FileResolver fileResolver) {
        super(factory);
        toolchainLocation = factory.gradleProperty(PROPERTY_NAME).orElse(defaultMavenToolchainsDefinitionsLocation());
        xPathFactory = XPathFactory.newInstance();
        documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.fileResolver = fileResolver;
    }

    @Override
    public String getSourceName() {
        return "Maven Toolchains";
    }

    @Override
    protected Set<InstallationLocation> findCandidates() {
        File toolchainFile = fileResolver.resolve(toolchainLocation.get());
        if (toolchainFile.exists()) {
            try (FileInputStream toolchain = new FileInputStream(toolchainFile)) {
                DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
                documentBuilder.setErrorHandler(new PropagatingErrorHandler());
                XPath xpath = xPathFactory.newXPath();
                XPathExpression expression = xpath.compile(PARSE_EXPRESSION);

                NodeList nodes = (NodeList) expression.evaluate(documentBuilder.parse(toolchain), XPathConstants.NODESET);
                Set<String> locations = new HashSet<>();
                for (int i = 0; i < nodes.getLength(); i++) {
                    Node item = nodes.item(i);
                    if (item != null && item.getNodeType() == Node.TEXT_NODE) {
                        String location = item.getNodeValue().trim();
                        Matcher envMatches = ENV_VAR_PATTERN.matcher(location);
                        if (envMatches.matches()) {
                            String match = envMatches.group(1);
                            Provider<String> environmentProperty = getEnvironmentProperty(match);
                            location = environmentProperty == null ? null : environmentProperty.getOrNull(); // environmentProperty.getOrNull() occasionally fails in tests?!
                            if (location == null || location.isEmpty()) {
                                LOGGER.info("Java Toolchain auto-detection failed to fully parse Maven Toolchains located at '{}'. Environment variable '{}' is not set or empty.", toolchainFile, match);
                                continue;
                            }
                        }
                        locations.add(location);
                    }
                }
                return locations.stream()
                    .map(jdkHome -> new InstallationLocation(new File(jdkHome), getSourceName()))
                    .collect(Collectors.toSet());
            } catch (IOException | ParserConfigurationException | SAXException | XPathExpressionException e) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Java Toolchain auto-detection failed to parse Maven Toolchains located at {}", toolchainFile, e);
                } else {
                    LOGGER.info("Java Toolchain auto-detection failed to parse Maven Toolchains located at {}. {}", toolchainFile, e.getMessage());
                }
            }
        }
        return Collections.emptySet();
    }

    private String defaultMavenToolchainsDefinitionsLocation() {
        return new File(MavenUtil.getUserMavenDir(), "toolchains.xml").getAbsolutePath();
    }

    private static class PropagatingErrorHandler implements ErrorHandler {
        @Override
        public void warning(SAXParseException e) throws SAXException {
            // Non-fatal error. No need to log.
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            // Non-fatal error. No need to log.
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            // Propagate error -- consistent with default behavior.
            throw e;
        }
    }
}
