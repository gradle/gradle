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
import org.gradle.api.internal.artifacts.verification.DependencyVerifier;
import org.gradle.api.internal.artifacts.verification.DependencyVerifierBuilder;
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;

import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.ARTIFACT;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.CLASSIFIER;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.COMPONENT;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.COMPONENTS;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.EXT;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.GROUP;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.NAME;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.TYPE;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.VALUE;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.VERIFICATION_METADATA;
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
        private ModuleComponentIdentifier currentComponent;
        private ModuleComponentArtifactIdentifier currentArtifact;

        public VerifiersHandler(DependencyVerifierBuilder builder) {
            this.builder = builder;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if (VERIFICATION_METADATA.equals(qName)) {
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
            } else {
                if (currentArtifact != null) {
                    ChecksumKind kind = ChecksumKind.valueOf(qName);
                    builder.addChecksum(currentArtifact, kind, getAttribute(attributes, VALUE));
                }
            }
        }

        private void assertInComponents() {
            assertContext(inComponents, "<component> must be found under the <components> tag");
        }

        private void assertInMetadata() {
            assertContext(inMetadata, "<components> must be found under the <verification-metadata> tag");
        }

        private void assertValidComponent() {
            assertContext(currentComponent != null, "<artifact> must be found  under the <component> tag");
        }

        private static void assertContext(boolean test, String message) {
            if (!test) {
                throw new InvalidUserDataException("Invalid dependency verification metadata file: " + message);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (VERIFICATION_METADATA.equals(qName)) {
                inMetadata = false;
            } else if (COMPONENTS.equals(qName)) {
                inComponents = false;
            } else if (COMPONENT.equals(qName)) {
                currentComponent = null;
            } else if (ARTIFACT.equals(qName)) {
                currentArtifact = null;
            }
        }

        private DefaultModuleComponentArtifactIdentifier createArtifactId(Attributes attributes) {
            return new DefaultModuleComponentArtifactIdentifier(
                currentComponent,
                getAttribute(attributes, NAME),
                getNullableAttribute(attributes, TYPE),
                getNullableAttribute(attributes, EXT),
                getNullableAttribute(attributes, CLASSIFIER)
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

        DependencyVerifier toVerifier() {
            return builder.build();
        }
    }
}
