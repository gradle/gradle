/*
 * Copyright 2022 the original author or authors.
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

/**
 * This class is a DependencyVerifierBuilder2 that skips adding the
 * version field.
 */
public class VersionRemovingDependencyVerifierBuilder implements DependencyVerifierBuilder2 {
    private final DependencyVerifierBuilder2 delegate;

    public VersionRemovingDependencyVerifierBuilder(DependencyVerifierBuilder2 wrapped) {
        delegate = wrapped;
    }

    public void addTrustedArtifact(@Nullable String group, @Nullable String name, @Nullable String version, @Nullable String fileName, boolean regex) {
        delegate.addTrustedArtifact(group, name, null, fileName, regex);
    }

    public void addTrustedKey(String keyId, @Nullable String group, @Nullable String name, @Nullable String version, @Nullable String fileName, boolean regex) {
        delegate.addTrustedKey(keyId, group, name, null, fileName, regex);
    }

    public void addTopLevelComment(String comment) {
        delegate.addTopLevelComment(comment);
    }

    public void addChecksum(ModuleComponentArtifactIdentifier artifact, ChecksumKind kind, String value, @Nullable String origin) {
        delegate.addChecksum(artifact, kind, value, origin);
    }

    public void addTrustedKey(ModuleComponentArtifactIdentifier artifact, String key) {
        delegate.addTrustedKey(artifact, key);
    }

    public void addIgnoredKey(ModuleComponentArtifactIdentifier artifact, IgnoredKey key) {
        delegate.addIgnoredKey(artifact, key);
    }

    public void setVerifyMetadata(boolean verifyMetadata) {
        delegate.setVerifyMetadata(verifyMetadata);
    }

    public boolean isVerifyMetadata() {
        return delegate.isVerifyMetadata();
    }

    public boolean isVerifySignatures() {
        return delegate.isVerifySignatures();
    }

    public void setVerifySignatures(boolean verifySignatures) {
        delegate.setVerifySignatures(verifySignatures);
    }

    public boolean isUseKeyServers() {
        return delegate.isUseKeyServers();
    }

    public void setUseKeyServers(boolean useKeyServers) {
        delegate.setUseKeyServers(useKeyServers);
    }

    public List<URI> getKeyServers() {
        return delegate.getKeyServers();
    }

    public Set<DependencyVerificationConfiguration.TrustedKey> getTrustedKeys() {
        return delegate.getTrustedKeys();
    }

    public void addIgnoredKey(IgnoredKey keyId) {
        delegate.addIgnoredKey(keyId);
    }

    public DependencyVerifier build() {
        return delegate.build();
    }

    public List<DependencyVerificationConfiguration.TrustedArtifact> getTrustedArtifacts() {
        return delegate.getTrustedArtifacts();
    }

    public void addKeyServer(URI uri) {
        delegate.addKeyServer(uri);
    }
}
