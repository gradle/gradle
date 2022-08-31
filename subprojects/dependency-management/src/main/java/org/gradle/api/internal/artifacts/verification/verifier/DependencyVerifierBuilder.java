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
package org.gradle.api.internal.artifacts.verification.verifier;

import org.gradle.api.internal.artifacts.verification.model.ChecksumKind;
import org.gradle.api.internal.artifacts.verification.model.IgnoredKey;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.List;
import java.util.Set;

public interface DependencyVerifierBuilder {
    void addTopLevelComment(String comment);

    void addChecksum(ModuleComponentArtifactIdentifier artifact, ChecksumKind kind, String value, @Nullable String origin);

    void addTrustedKey(ModuleComponentArtifactIdentifier artifact, String key);

    void addIgnoredKey(ModuleComponentArtifactIdentifier artifact, IgnoredKey key);

    void setVerifyMetadata(boolean verifyMetadata);

    boolean isVerifyMetadata();

    boolean isVerifySignatures();

    void setVerifySignatures(boolean verifySignatures);

    boolean isUseKeyServers();

    void setUseKeyServers(boolean useKeyServers);

    List<URI> getKeyServers();

    Set<DependencyVerificationConfiguration.TrustedKey> getTrustedKeys();

    void addTrustedArtifact(@Nullable String group, @Nullable String name, @Nullable String version, @Nullable String fileName, boolean regex);

    void addIgnoredKey(IgnoredKey keyId);

    void addTrustedKey(String keyId, @Nullable String group, @Nullable String name, @Nullable String version, @Nullable String fileName, boolean regex);

    DependencyVerifier build();

    List<DependencyVerificationConfiguration.TrustedArtifact> getTrustedArtifacts();

    void addKeyServer(URI uri);
}
