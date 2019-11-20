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

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.verification.DependencyVerifier;
import org.gradle.api.internal.artifacts.verification.model.ArtifactVerificationMetadata;
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind;
import org.gradle.api.internal.artifacts.verification.model.ComponentVerificationMetadata;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.xml.SimpleXmlWriter;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
        ComponentIdentifier component = verification.getComponentId();
        if (component instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier mci = (ModuleComponentIdentifier) component;
            writer.startElement(COMPONENT);
            writer.attribute(GROUP, mci.getGroup());
            writer.attribute(NAME, mci.getModule());
            writer.attribute(VERSION, mci.getVersion());
            writeArtifactVerifications(verification.getArtifactVerifications());
            writer.endElement();
        }
    }

    private void writeArtifactVerifications(List<ArtifactVerificationMetadata> verifications) throws IOException {
        for (ArtifactVerificationMetadata verification : verifications) {
            writeArtifactVerification(verification);
        }
    }

    private void writeArtifactVerification(ArtifactVerificationMetadata verification) throws IOException {
        ComponentArtifactIdentifier artifact = verification.getArtifact();
        if (artifact instanceof ModuleComponentArtifactIdentifier) {
            ModuleComponentArtifactIdentifier mcai = (ModuleComponentArtifactIdentifier) artifact;
            IvyArtifactName name = mcai.getName();
            writer.startElement(ARTIFACT);
            writer.attribute(NAME, name.getName());
            writeAttributeIfNotNull(CLASSIFIER, name.getClassifier());
            writeAttributeIfNotNull(TYPE, name.getType());
            writeAttributeIfNotNull(EXT, name.getExtension());
            writeChecksums(verification.getChecksums());
            writer.endElement();
        }
    }

    private void writeAttributeIfNotNull(String attributeName, @Nullable String value) throws IOException {
        if (value != null) {
            writer.attribute(attributeName, value);
        }
    }

    private void writeChecksums(Map<ChecksumKind, String> checksums) throws IOException {
        for (Map.Entry<ChecksumKind, String> entry : checksums.entrySet()) {
            String kind = entry.getKey().name();
            String value = entry.getValue();
            writer.startElement(kind);
            writer.attribute(VALUE, value);
            writer.endElement();
        }
    }
}
