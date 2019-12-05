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
import org.gradle.api.internal.artifacts.verification.DependencyVerifier;
import org.gradle.api.internal.artifacts.verification.model.ArtifactVerificationMetadata;
import org.gradle.api.internal.artifacts.verification.model.Checksum;
import org.gradle.api.internal.artifacts.verification.model.ComponentVerificationMetadata;
import org.gradle.internal.xml.SimpleXmlWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.ALSO_TRUST;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.ARTIFACT;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.COMPONENT;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.COMPONENTS;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.GROUP;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.NAME;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.ORIGIN;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.VALUE;
import static org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationXmlTags.VERIFICATION_METADATA;
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
        writeVerifications(verifier.getVerificationMetadata());
        writer.endElement();
        writer.close();
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
        writer.attribute(GROUP, mci.getGroup());
        writer.attribute(NAME, mci.getModule());
        writer.attribute(VERSION, mci.getVersion());
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
        writer.attribute(NAME, artifact);
        writeChecksums(verification.getChecksums());
        writer.endElement();
    }

    private void writeChecksums(List<Checksum> checksums) throws IOException {
        for (Checksum checksum : checksums) {
            String kind = checksum.getKind().name();
            String value = checksum.getValue();
            writer.startElement(kind);
            writer.attribute(VALUE, value);
            String origin = checksum.getOrigin();
            if (origin != null) {
                writer.attribute(ORIGIN, origin);
            }
            Set<String> alternatives = checksum.getAlternatives();
            if (alternatives != null) {
                for (String alternative : alternatives) {
                    writer.startElement(ALSO_TRUST);
                    writer.attribute(VALUE, alternative);
                    writer.endElement();
                }
            }
            writer.endElement();
        }
    }
}
