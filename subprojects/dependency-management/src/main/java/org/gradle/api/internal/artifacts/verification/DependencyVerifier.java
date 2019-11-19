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
package org.gradle.api.internal.artifacts.verification;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.Action;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.verification.model.ArtifactVerificationMetadata;
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind;
import org.gradle.api.internal.artifacts.verification.model.ComponentVerificationMetadata;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.hash.HashValue;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DependencyVerifier {
    private final Map<ComponentIdentifier, ComponentVerificationMetadata> verificationMetadata;

    DependencyVerifier(Map<ComponentIdentifier, ComponentVerificationMetadata> verificationMetadata) {
        this.verificationMetadata = ImmutableMap.copyOf(verificationMetadata);
    }

    public void verify(ModuleComponentArtifactIdentifier foundArtifact, File file, Action<VerificationFailure> onFailure) {
        ComponentVerificationMetadata componentVerification = verificationMetadata.get(foundArtifact.getComponentIdentifier());
        if (componentVerification != null) {
            List<ArtifactVerificationMetadata> verifications = componentVerification.getArtifactVerifications();
            for (ArtifactVerificationMetadata verification : verifications) {
                ComponentArtifactIdentifier verifiedArtifact = verification.getArtifact();
                if (verifiedArtifact.equals(foundArtifact)) {
                    Map<ChecksumKind, String> checksums = verification.getChecksums();
                    for (ChecksumKind value : ChecksumKind.mostSecureFirst()) {
                        String checksum = checksums.get(value);
                        if (checksum != null) {
                            verify(value, file, checksum, onFailure);
                        }
                    }
                    break; // we've found our artifact, no need to continue
                }
            }
        }
    }

    private static void verify(ChecksumKind algorithm, File file, String expected, Action<VerificationFailure> onFailure) {
        HashValue hashValue = null;
        switch (algorithm) {
            case md5:
                hashValue = HashUtil.md5(file);
                break;
            case sha1:
                hashValue = HashUtil.sha1(file);
                break;
            case sha256:
                hashValue = HashUtil.sha256(file);
                break;
            case sha512:
                hashValue = HashUtil.sha512(file);
                break;
        }
        String actual = hashValue.asHexString();
        if (!actual.equals(expected)) {
            onFailure.execute(new VerificationFailure(algorithm, expected, actual));
        }
    }

    public Collection<ComponentVerificationMetadata> getVerificationMetadata() {
        return verificationMetadata.values();
    }

    public static class VerificationFailure {
        private final ChecksumKind kind;
        private final String expected;
        private final String actual;

        VerificationFailure(ChecksumKind kind, String expected, String actual) {
            this.kind = kind;
            this.expected = expected;
            this.actual = actual;
        }

        public ChecksumKind getKind() {
            return kind;
        }

        public String getExpected() {
            return expected;
        }

        public String getActual() {
            return actual;
        }
    }
}
