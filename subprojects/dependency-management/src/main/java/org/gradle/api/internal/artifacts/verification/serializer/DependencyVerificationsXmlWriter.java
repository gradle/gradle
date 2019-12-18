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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerificationConfiguration;
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifier;
import org.gradle.api.internal.artifacts.verification.model.ArtifactVerificationMetadata;
import org.gradle.api.internal.artifacts.verification.model.Checksum;
import org.gradle.api.internal.artifacts.verification.model.ComponentVerificationMetadata;
import org.gradle.internal.xml.SimpleMarkupWriter;
import org.gradle.internal.xml.SimpleXmlWriter;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.PGP;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.REGEX;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.TRUST;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.TRUSTED_ARTIFACTS;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.ORIGIN;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.URI;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.VALUE;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.VERIFICATION_METADATA;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.VERIFY_METADATA;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.VERIFY_SIGNATURES;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.VERSION;

public class DependencyVerificationsXmlWriter {
    private static final String SPACES = "   ";
    private final SimpleXmlWriter writer;

    private DependencyVerificationsXmlWriter(OutputStream out) throws IOException {
        this.writer = new SimpleXmlWriter(out, SPACES);
    }

    public static void serialize(DependencyVerifier verifier, OutputStream out) throws IOException {
        try {
            DependencyVerificationsXmlWriter writer = new DependencyVerificationsXmlWriter(out);
            writer.write(verifier);
        } finally {
            out.close();
        }
    }

    private void write(DependencyVerifier verifier) throws IOException {
        writer.startElement(VERIFICATION_METADATA);
        writeConfiguration(verifier.getConfiguration());
        writeVerifications(verifier.getVerificationMetadata());
        writer.endElement();
        writer.close();
    }

    private void writeConfiguration(DependencyVerificationConfiguration configuration) throws IOException {
        writer.startElement(CONFIG);
        writeVerifyMetadata(configuration);
        writeSignatureCheck(configuration);
        writeKeyServers(configuration);
        writeTrustedArtifacts(configuration);
        writIgnoredKeys(configuration);
        writer.endElement();
    }

    private void writIgnoredKeys(DependencyVerificationConfiguration configuration) throws IOException {
        Set<String> ignoredKeys = configuration.getIgnoredKeys();
        if (!ignoredKeys.isEmpty()) {
            writer.startElement(IGNORED_KEYS);
            for (String ignoredKey : ignoredKeys) {
                writeIgnoredKey(ignoredKey);
            }
            writer.endElement();
        }
    }

    private void writeIgnoredKey(String ignoredKey) throws IOException {
        writer.startElement(IGNORED_KEY);
        writer.attribute(ID, ignoredKey);
        writer.endElement();
    }

    private void writeTrustedArtifacts(DependencyVerificationConfiguration configuration) throws IOException {
        List<DependencyVerificationConfiguration.TrustedArtifact> trustedArtifacts = configuration.getTrustedArtifacts();
        if (trustedArtifacts.isEmpty()) {
            return;
        }
        writer.startElement(TRUSTED_ARTIFACTS);
        for (DependencyVerificationConfiguration.TrustedArtifact trustedArtifact : trustedArtifacts) {
            writeTrustedArtifact(trustedArtifact);
        }
        writer.endElement();
    }

    private void writeTrustedArtifact(DependencyVerificationConfiguration.TrustedArtifact trustedArtifact) throws IOException {
        writer.startElement(TRUST);
        writeNullableAttribute(GROUP, trustedArtifact.getGroup());
        writeNullableAttribute(NAME, trustedArtifact.getName());
        writeNullableAttribute(VERSION, trustedArtifact.getVersion());
        writeNullableAttribute(FILE, trustedArtifact.getFileName());
        if (trustedArtifact.isRegex()) {
            writeAttribute(REGEX, "true");
        }
        writer.endElement();
    }

    private void writeSignatureCheck(DependencyVerificationConfiguration configuration) throws IOException {
        writer.startElement(VERIFY_SIGNATURES);
        writer.write(String.valueOf(configuration.isVerifySignatures()));
        writer.endElement();
    }

    private void writeVerifyMetadata(DependencyVerificationConfiguration configuration) throws IOException {
        writer.startElement(VERIFY_METADATA);
        writer.write(String.valueOf(configuration.isVerifyMetadata()));
        writer.endElement();
    }

    private void writeKeyServers(DependencyVerificationConfiguration configuration) throws IOException {
        List<URI> keyServers = configuration.getKeyServers();
        if (!keyServers.isEmpty()) {
            writer.startElement(KEY_SERVERS);
            for (URI keyServer : keyServers) {
                writer.startElement(KEY_SERVER);
                writeAttribute(URI, keyServer.toASCIIString());
                writer.endElement();
            }
            writer.endElement();
        }
    }

    private SimpleMarkupWriter writeAttribute(String name, String value) throws IOException {
        return writer.attribute(name, value);
    }

    private SimpleMarkupWriter writeNullableAttribute(String name, @Nullable String value) throws IOException {
        if (value == null) {
            return writer;
        }
        return writeAttribute(name, value);
    }

    private void writeVerifications(Collection<ComponentVerificationMetadata> verifications) throws IOException {
        writer.startElement(COMPONENTS);
        for (ComponentVerificationMetadata verification : verifications) {
            writeVerification(verification);
        }
        writer.endElement();
    }

    private void writeVerification(ComponentVerificationMetadata verification) throws IOException {
        ModuleComponentIdentifier mci = verification.getComponentId();
        writer.startElement(COMPONENT);
        writeAttribute(GROUP, mci.getGroup());
        writeAttribute(NAME, mci.getModule());
        writeAttribute(VERSION, mci.getVersion());
        writeArtifactVerifications(verification.getArtifactVerifications());
        writer.endElement();
    }

    private void writeArtifactVerifications(List<ArtifactVerificationMetadata> verifications) throws IOException {
        for (ArtifactVerificationMetadata verification : verifications) {
            writeArtifactVerification(verification);
        }
    }

    private void writeArtifactVerification(ArtifactVerificationMetadata verification) throws IOException {
        String artifact = verification.getArtifactName();
        writer.startElement(ARTIFACT);
        writeAttribute(NAME, artifact);
        writeTrustedKeys(verification.getTrustedPgpKeys());
        writeChecksums(verification.getChecksums());
        writer.endElement();

    }

    private void writeTrustedKeys(Set<String> trustedPgpKeys) throws IOException {
        for (String key : trustedPgpKeys) {
            writer.startElement(PGP);
            writeAttribute(VALUE, key);
            writer.endElement();
        }
    }

    private void writeChecksums(List<Checksum> checksums) throws IOException {
        for (Checksum checksum : checksums) {
            String kind = checksum.getKind().name();
            String value = checksum.getValue();
            writer.startElement(kind);
            writeAttribute(VALUE, value);
            String origin = checksum.getOrigin();
            if (origin != null) {
                writeAttribute(ORIGIN, origin);
            }
            Set<String> alternatives = checksum.getAlternatives();
            if (alternatives != null) {
                for (String alternative : alternatives) {
                    writer.startElement(ALSO_TRUST);
                    writeAttribute(VALUE, alternative);
                    writer.endElement();
                }
            }
            writer.endElement();
        }
    }
}
