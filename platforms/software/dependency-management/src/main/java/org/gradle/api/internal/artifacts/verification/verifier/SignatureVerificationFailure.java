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

import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.security.internal.PGPUtils;
import org.gradle.security.internal.PublicKeyResultBuilder;
import org.gradle.security.internal.PublicKeyService;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class SignatureVerificationFailure extends AbstractVerificationFailure {
    private final Map<String, SignatureError> errors;
    private final PublicKeyService keyService;
    private final File signatureFile;
    private final @Nullable String moduleGroup;
    private final Set<String> otherTrustedKeyIdsForGroup;

    public SignatureVerificationFailure(File affectedFile, File signatureFile, Map<String, SignatureError> errors, PublicKeyService keyService) {
        this(affectedFile, signatureFile, errors, keyService, null, Set.of());
    }

    /**
     * Creates a failure that can also report how many other keys are already trusted for the artifact's
     * module group, enriching {@code MISSING_KEY} messages so users can tell a rotated signing key apart
     * from a group being trusted for the first time.
     *
     * @param moduleGroup the group of the artifact that failed verification, or {@code null} if unknown
     * @param otherTrustedKeyIdsForGroup the IDs of other keys already trusted for {@code moduleGroup},
     *            excluding the key that triggered this failure
     * @see <a href="https://github.com/gradle/gradle/issues/20100">gradle/gradle#20100</a>
     */
    public SignatureVerificationFailure(File affectedFile, File signatureFile, Map<String, SignatureError> errors, PublicKeyService keyService,
                                        @Nullable String moduleGroup, Set<String> otherTrustedKeyIdsForGroup) {
        super(affectedFile);
        this.errors = errors;
        this.keyService = keyService;
        this.signatureFile = signatureFile;
        this.moduleGroup = moduleGroup;
        this.otherTrustedKeyIdsForGroup = otherTrustedKeyIdsForGroup;
    }

    @Override
    public File getSignatureFile() {
        return signatureFile;
    }

    public Map<String, SignatureError> getErrors() {
        return errors;
    }

    @Override
    public void explainTo(TreeFormatter formatter) {
        if (errors.size() == 1) {
            Map.Entry<String, SignatureError> entry = errors.entrySet().iterator().next();
            formatter.append(toMessage(entry.getKey(), entry.getValue()));
            return;
        }
        formatter.append("Multiple signature verification errors found");
        formatter.startChildren();
        errors.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .forEachOrdered(entry -> formatter.node(toMessage(entry.getKey(), entry.getValue())));
        formatter.endChildren();
    }

    private String toMessage(String key, SignatureError value) {
        StringBuilder sb = new StringBuilder();
        appendError(key, value, sb);
        return sb.toString();
    }

    private void appendError(String keyId, SignatureError error, StringBuilder sb) {
        sb.append("Artifact was signed with key '").append(keyId).append("' ");
        PGPPublicKey publicKey = error.publicKey;
        switch (error.kind) {
            case PASSED_NOT_TRUSTED:
                appendKeyDetails(sb, publicKey);
                sb.append("and passed verification but the key isn't in your trusted keys list.");
                break;
            case FAILED:
                appendKeyDetails(sb, publicKey);
                sb.append("but signature didn't match");
                break;
            case MISSING_KEY:
                sb.append("but it wasn't found in any key server so it couldn't be verified");
                appendOtherTrustedKeysNote(sb);
                break;
            default:
                break;
        }
    }

    private void appendOtherTrustedKeysNote(StringBuilder sb) {
        if (moduleGroup == null || otherTrustedKeyIdsForGroup.isEmpty()) {
            return;
        }
        int count = otherTrustedKeyIdsForGroup.size();
        sb.append(" (");
        if (count == 1) {
            sb.append("1 other key is");
        } else {
            sb.append(count).append(" other keys are");
        }
        sb.append(" already trusted for group '").append(moduleGroup).append("')");
    }

    public enum FailureKind {
        PASSED_NOT_TRUSTED,
        FAILED,
        IGNORED_KEY,
        MISSING_KEY
    }

    public void appendKeyDetails(StringBuilder sb, PGPPublicKey key) {
        keyService.findByFingerprint(key.getFingerprint(), new PublicKeyResultBuilder() {
            @Override
            public void keyRing(PGPPublicKeyRing keyring) {
                Set<String> userIds = new TreeSet<>();
                collectUserIds(userIds, key);
                keyring.getPublicKeys().forEachRemaining(userkey -> collectUserIds(userIds, userkey));
                if (!userIds.isEmpty()) {
                    sb.append("(");
                }
                sb.append(String.join(", ", userIds));
                if (!userIds.isEmpty()) {
                    sb.append(") ");
                }
            }

            @Override
            public void publicKey(PGPPublicKey publicKey) {

            }
        });
    }

    private void collectUserIds(Set<String> userIds, PGPPublicKey userkey) {
        userIds.addAll(PGPUtils.getUserIDs(userkey));
    }

    public static class SignatureError {
        private final PGPPublicKey publicKey;
        private final FailureKind kind;

        public SignatureError(@Nullable PGPPublicKey key, FailureKind kind) {
            this.publicKey = key;
            this.kind = kind;
        }

        public FailureKind getKind() {
            return kind;
        }

        public PGPPublicKey getPublicKey() {
            return publicKey;
        }
    }
}
