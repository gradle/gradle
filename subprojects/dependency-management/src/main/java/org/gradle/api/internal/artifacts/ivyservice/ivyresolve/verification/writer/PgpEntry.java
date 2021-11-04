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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.writer;

import com.google.common.collect.Sets;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.ArtifactVerificationOperation;
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerificationConfiguration;
import org.gradle.internal.Factory;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;

import java.io.File;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

class PgpEntry extends VerificationEntry {
    private final Factory<File> signatureFile;
    private final Set<String> trustedKeys = Sets.newTreeSet();
    private final AtomicBoolean requiresChecksums = new AtomicBoolean();
    private final Set<String> failed = Sets.newConcurrentHashSet();
    private final AtomicBoolean missing = new AtomicBoolean();
    private final AtomicBoolean hasSignatureFile = new AtomicBoolean();

    // this field is used during "grouping" of entries to tell if we should ignore writing this entry
    private final Set<String> keysDeclaredGlobally = Sets.newHashSet();

    PgpEntry(ModuleComponentArtifactIdentifier id, ArtifactVerificationOperation.ArtifactKind artifactKind, File file, Factory<File> signatureFile) {
        super(id, artifactKind, file);
        this.signatureFile = () -> {
            File f = signatureFile.create();
            boolean hasSig = f != null && f.exists();
            hasSignatureFile.set(hasSig);
            if (!hasSig) {
                requiresChecksums.set(true);
            }
            return f;
        };
    }

    @Override
    int getOrder() {
        return -1;
    }

    public Set<String> getTrustedKeys() {
        return trustedKeys;
    }

    public PgpEntry addVerifiedKey(String key) {
        trustedKeys.add(key);
        return this;
    }

    public Factory<File> getSignatureFile() {
        return signatureFile;
    }

    public void fail(String keyId) {
        requiresChecksums.set(true);
        failed.add(keyId);
    }

    public void missing() {
        requiresChecksums.set(true);
        missing.set(true);
    }

    public boolean isRequiringChecksums() {
        return requiresChecksums.get();
    }

    public boolean isFailed() {
        return !failed.isEmpty();
    }

    public Set<String> getFailed() {
        return failed;
    }

    public void keyDeclaredGlobally(String keyId) {
        keysDeclaredGlobally.add(keyId);
    }

    public boolean doesNotDeclareKeyGlobally(String keyId) {
        return !keysDeclaredGlobally.contains(keyId);
    }

    public boolean hasArtifactLevelKeys() {
        return !trustedKeys.equals(keysDeclaredGlobally);
    }

    public Set<String> getArtifactLevelKeys() {
        Set<String> keys = Sets.newHashSet(trustedKeys);
        keys.removeAll(keysDeclaredGlobally);
        return keys;
    }

    public boolean hasSignatureFile() {
        return hasSignatureFile.get();
    }

    boolean checkAndMarkSatisfiedBy(DependencyVerificationConfiguration.TrustedKey trustedKey) {
        if (!trustedKeys.contains(trustedKey.getKeyId())) {
            return false;
        }
        boolean matches = trustedKey.matches(id);
        if (matches) {
            keyDeclaredGlobally(trustedKey.getKeyId());
        }
        return matches;
    }
}
