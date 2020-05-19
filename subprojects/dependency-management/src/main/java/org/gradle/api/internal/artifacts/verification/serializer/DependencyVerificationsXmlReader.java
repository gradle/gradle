/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.verification.serializer;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind;
import org.gradle.api.internal.artifacts.verification.model.IgnoredKey;
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifier;
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentFileArtifactIdentifier;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.ALSO_TRUST;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.ARTIFACT;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.COMPONENT;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.COMPONENTS;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.CONFIG;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.FILE;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.GROUP;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.ID;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.IGNORED_KEY;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.IGNORED_KEYS;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.KEY_SERVER;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.KEY_SERVERS;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.NAME;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.ORIGIN;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.PGP;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.REASON;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.REGEX;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.TRUST;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.TRUSTED_ARTIFACTS;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.TRUSTED_KEY;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.TRUSTED_KEYS;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.TRUSTING;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.URI;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.VALUE;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.VERIFICATION_METADATA;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.VERIFY_METADATA;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.VERIFY_SIGNATURES;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.VERSION;

public class DependencyVerificationsXmlReader {
    public static void readFromXml(InputStream in, DependencyVerifierBuilder builder) {
        try {
            SAXParser saxParser = createSecureParser();
            XMLReader xmlReader = saxParser.getXMLReader();
            VerifiersHandler handler = new VerifiersHandler(builder);
            xmlReader.setContentHandler(handler);
            xmlReader.parse(new InputSource(in));
        } catch (Exception e) {
            throw new InvalidUserDataException("Unable to read dependency verification metadata", e);
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }

    public static DependencyVerifier readFromXml(InputStream in) {
        DependencyVerifierBuilder builder = new DependencyVerifierBuilder();
        readFromXml(in, builder);
        return builder.build();
    }

    private static SAXParser createSecureParser() throws ParserConfigurationException, SAXException {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        spf.setFeature("http://xml.org/sax/features/namespaces", false);
        spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return spf.newSAXParser();
    }

    private static class VerifiersHandler extends DefaultHandler {
        private final Interner<String> stringInterner = Interners.newStrongInterner();
        private final DependencyVerifierBuilder builder;
        private boolean inMetadata;
        private boolean inComponents;
        private boolean inConfiguration;
        private boolean inVerifyMetadata;
        private boolean inVerifySignatures;
        private boolean inTrustedArtifacts;
        private boolean inKeyServers;
        private boolean inIgnoredKeys;
        private boolean inTrustedKeys;
        private boolean inTrustedKey;
        private String currentTrustedKey;
        private ModuleComponentIdentifier currentComponent;
        private ModuleComponentArtifactIdentifier currentArtifact;
        private ChecksumKind currentChecksum;

        public VerifiersHandler(DependencyVerifierBuilder builder) {
            this.builder = builder;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            switch (qName) {
                case CONFIG:
                    inConfiguration = true;
                    break;
                case VERIFICATION_METADATA:
                    inMetadata = true;
                    break;
                case COMPONENTS:
                    assertInMetadata();
                    inComponents = true;
                    break;
                case COMPONENT:
                    assertInComponents();
                    currentComponent = createComponentId(attributes);
                    break;
                case ARTIFACT:
                    assertValidComponent();
                    currentArtifact = createArtifactId(attributes);
                    break;
                case VERIFY_METADATA:
                    assertInConfiguration(VERIFY_METADATA);
                    inVerifyMetadata = true;
                    break;
                case VERIFY_SIGNATURES:
                    assertInConfiguration(VERIFY_SIGNATURES);
                    inVerifySignatures = true;
                    break;
                case TRUSTED_ARTIFACTS:
                    assertInConfiguration(TRUSTED_ARTIFACTS);
                    inTrustedArtifacts = true;
                    break;
                case TRUSTED_KEY:
                    assertContext(inTrustedKeys, TRUSTED_KEY, TRUSTED_KEYS);
                    addTrustedKey(attributes);
                    inTrustedKey = true;
                    break;
                case TRUSTED_KEYS:
                    assertInConfiguration(TRUSTED_KEYS);
                    inTrustedKeys = true;
                    break;
                case TRUST:
                    assertInTrustedArtifacts();
                    addTrustedArtifact(attributes);
                    break;
                case TRUSTING:
                    assertContext(inTrustedKey, TRUSTING, TRUSTED_KEY);
                    maybeAddTrustedKey(attributes);
                    break;
                case KEY_SERVERS:
                    assertInConfiguration(KEY_SERVERS);
                    inKeyServers = true;
                    break;
                case KEY_SERVER:
                    assertContext(inKeyServers, KEY_SERVER, KEY_SERVERS);
                    String server = getAttribute(attributes, URI);
                    try {
                        builder.addKeyServer(new URI(server));
                    } catch (URISyntaxException e) {
                        throw new InvalidUserDataException("Unsupported URI for key server: " + server);
                    }
                    break;
                case IGNORED_KEYS:
                    if (currentArtifact == null) {
                        assertInConfiguration(IGNORED_KEYS);
                    }
                    inIgnoredKeys = true;
                    break;
                case IGNORED_KEY:
                    assertContext(inIgnoredKeys, IGNORED_KEY, IGNORED_KEYS);
                    if (currentArtifact != null) {
                        addArtifactIgnoredKey(attributes);
                    } else {
                        addIgnoredKey(attributes);
                    }
                    break;
                default:
                    if (currentChecksum != null && ALSO_TRUST.equals(qName)) {
                        builder.addChecksum(currentArtifact, currentChecksum, getAttribute(attributes, VALUE), null);
                    } else if (currentArtifact != null) {
                        if (PGP.equals(qName)) {
                            builder.addTrustedKey(currentArtifact, getAttribute(attributes, VALUE));
                        } else {
                            currentChecksum = ChecksumKind.valueOf(qName);
                            builder.addChecksum(currentArtifact, currentChecksum, getAttribute(attributes, VALUE), getNullableAttribute(attributes, ORIGIN));
                        }
                    }
            }
        }

        private void addArtifactIgnoredKey(Attributes attributes) {
            builder.addIgnoredKey(currentArtifact, toIgnoredKey(attributes));
        }

        private IgnoredKey toIgnoredKey(Attributes attributes) {
            return new IgnoredKey(getAttribute(attributes, ID), getNullableAttribute(attributes, REASON));
        }

        private void addIgnoredKey(Attributes attributes) {
            builder.addIgnoredKey(toIgnoredKey(attributes));
        }

        private void assertInTrustedArtifacts() {
            assertContext(inTrustedArtifacts, TRUST, TRUSTED_ARTIFACTS);
        }

        private void addTrustedArtifact(Attributes attributes) {
            boolean regex = false;
            String regexAttr = getNullableAttribute(attributes, REGEX);
            if (regexAttr != null) {
                regex = Boolean.parseBoolean(regexAttr);
            }
            builder.addTrustedArtifact(
                getNullableAttribute(attributes, GROUP),
                getNullableAttribute(attributes, NAME),
                getNullableAttribute(attributes, VERSION),
                getNullableAttribute(attributes, FILE),
                regex
            );
        }

        private void addTrustedKey(Attributes attributes) {
            currentTrustedKey = getAttribute(attributes, ID);
            maybeAddTrustedKey(attributes);
        }

        private void maybeAddTrustedKey(Attributes attributes) {
            boolean regex = false;
            String regexAttr = getNullableAttribute(attributes, REGEX);
            if (regexAttr != null) {
                regex = Boolean.parseBoolean(regexAttr);
            }
            String group = getNullableAttribute(attributes, GROUP);
            String name = getNullableAttribute(attributes, NAME);
            String version = getNullableAttribute(attributes, VERSION);
            String file = getNullableAttribute(attributes, FILE);
            if (group != null || name!=null || version != null || file != null) {
                builder.addTrustedKey(
                    currentTrustedKey,
                    group,
                    name,
                    version,
                    file,
                    regex
                );
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inVerifyMetadata) {
                builder.setVerifyMetadata(readBoolean(ch, start, length));
            } else if (inVerifySignatures) {
                builder.setVerifySignatures(readBoolean(ch, start, length));
            }
        }

        private boolean readBoolean(char[] ch, int start, int length) {
            return Boolean.parseBoolean(new String(ch, start, length));
        }

        private void assertInConfiguration(String tag) {
            assertContext(inConfiguration, tag, CONFIG);
        }

        private void assertInComponents() {
            assertContext(inComponents, COMPONENT, COMPONENTS);
        }

        private void assertInMetadata() {
            assertContext(inMetadata, COMPONENTS, VERIFICATION_METADATA);
        }

        private void assertValidComponent() {
            assertContext(currentComponent != null, ARTIFACT, COMPONENT);
        }

        private static void assertContext(boolean test, String innerTag, String outerTag) {
            assertContext(test, "<" + innerTag + "> must be found under the <" + outerTag + "> tag");
        }

        private static void assertContext(boolean test, String message) {
            if (!test) {
                throw new InvalidUserDataException("Invalid dependency verification metadata file: " + message);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            switch (qName) {
                case CONFIG:
                    inConfiguration = false;
                    break;
                case VERIFY_METADATA:
                    inVerifyMetadata = false;
                    break;
                case VERIFY_SIGNATURES:
                    inVerifySignatures = false;
                    break;
                case VERIFICATION_METADATA:
                    inMetadata = false;
                    break;
                case COMPONENTS:
                    inComponents = false;
                    break;
                case COMPONENT:
                    currentComponent = null;
                    break;
                case TRUSTED_ARTIFACTS:
                    inTrustedArtifacts = false;
                    break;
                case TRUSTED_KEYS:
                    inTrustedKeys = false;
                    break;
                case TRUSTED_KEY:
                    inTrustedKey = false;
                    currentTrustedKey = null;
                    break;
                case KEY_SERVERS:
                    inKeyServers = false;
                    break;
                case ARTIFACT:
                    currentArtifact = null;
                    currentChecksum = null;
                    break;
                case IGNORED_KEYS:
                    inIgnoredKeys = false;
                    break;
            }
        }

        private ModuleComponentFileArtifactIdentifier createArtifactId(Attributes attributes) {
            return new ModuleComponentFileArtifactIdentifier(
                currentComponent,
                getAttribute(attributes, NAME)
            );
        }

        private ModuleComponentIdentifier createComponentId(Attributes attributes) {
            return DefaultModuleComponentIdentifier.newId(
                createModuleId(attributes),
                getAttribute(attributes, VERSION)
            );
        }

        private ModuleIdentifier createModuleId(Attributes attributes) {
            return DefaultModuleIdentifier.newId(getAttribute(attributes, GROUP), getAttribute(attributes, NAME));
        }

        private String getAttribute(Attributes attributes, String name) {
            String value = attributes.getValue(name);
            assertContext(value != null, "Missing attribute: " + name);
            return stringInterner.intern(value);
        }

        private String getNullableAttribute(Attributes attributes, String name) {
            String value = attributes.getValue(name);
            if (value == null) {
                return null;
            }
            return stringInterner.intern(value);
        }

    }
}
