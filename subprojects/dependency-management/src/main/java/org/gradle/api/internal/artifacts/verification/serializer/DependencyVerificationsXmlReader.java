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
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifier;
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder;
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind;
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
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.KEY_SERVER;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.KEY_SERVERS;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.NAME;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.ORIGIN;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.PGP;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.REGEX;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.TRUST;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.TRUSTED_ARTIFACTS;
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
        private ModuleComponentIdentifier currentComponent;
        private ModuleComponentArtifactIdentifier currentArtifact;
        private ChecksumKind currentChecksum;

        public VerifiersHandler(DependencyVerifierBuilder builder) {
            this.builder = builder;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if (CONFIG.equals(qName)) {
                inConfiguration = true;
            } else if (VERIFICATION_METADATA.equals(qName)) {
                inMetadata = true;
            } else if (COMPONENTS.equals(qName)) {
                assertInMetadata();
                inComponents = true;
            } else if (COMPONENT.equals(qName)) {
                assertInComponents();
                currentComponent = createComponentId(attributes);
            } else if (ARTIFACT.equals(qName)) {
                assertValidComponent();
                currentArtifact = createArtifactId(attributes);
            } else if (VERIFY_METADATA.equals(qName)) {
                assertInConfiguration(VERIFY_METADATA);
                inVerifyMetadata = true;
            } else if (VERIFY_SIGNATURES.equals(qName)) {
                assertInConfiguration(VERIFY_SIGNATURES);
                inVerifySignatures = true;
            } else if (TRUSTED_ARTIFACTS.equals(qName)) {
                assertInConfiguration(TRUSTED_ARTIFACTS);
                inTrustedArtifacts = true;
            } else if (TRUST.equals(qName)) {
                assertInTrustedArtifacts();
                addTrustedArtifact(attributes);
            } else if (KEY_SERVERS.equals(qName)) {
                assertInConfiguration(KEY_SERVERS);
                inKeyServers = true;
            } else if (KEY_SERVER.equals(qName)) {
                assertContext(inKeyServers, KEY_SERVER, KEY_SERVERS);
                String server = getAttribute(attributes, URI);
                try {
                    builder.addKeyServer(new URI(server));
                } catch (URISyntaxException e) {
                    throw new InvalidUserDataException("Unsupported URI for key server: " + server);
                }
            } else {
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
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (CONFIG.equals(qName)) {
                inConfiguration = false;
            } else if (VERIFY_METADATA.equals(qName)) {
                inVerifyMetadata = false;
            } else if (VERIFY_SIGNATURES.equals(qName)) {
                inVerifySignatures = false;
            } else if (VERIFICATION_METADATA.equals(qName)) {
                inMetadata = false;
            } else if (COMPONENTS.equals(qName)) {
                inComponents = false;
            } else if (COMPONENT.equals(qName)) {
                currentComponent = null;
            } else if (TRUSTED_ARTIFACTS.equals(qName)) {
                inTrustedArtifacts = false;
            } else if (KEY_SERVERS.equals(qName)) {
                inKeyServers = false;
            } else if (ARTIFACT.equals(qName)) {
                currentArtifact = null;
                currentChecksum = null;
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
