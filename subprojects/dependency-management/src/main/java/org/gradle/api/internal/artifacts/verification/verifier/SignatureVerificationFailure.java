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

import com.google.common.collect.Sets;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.utils.PGPUtils;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.security.internal.PublicKeyResultBuilder;
import org.gradle.security.internal.PublicKeyService;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;
import java.util.Set;

public class SignatureVerificationFailure extends AbstractVerificationFailure {
    private final Map<String, SignatureError> errors;
    private final PublicKeyService keyService;
    private final File signatureFile;

    public SignatureVerificationFailure(File affectedFile, File signatureFile, Map<String, SignatureError> errors, PublicKeyService keyService) {
        super(affectedFile);
        this.errors = errors;
        this.keyService = keyService;
        this.signatureFile = signatureFile;
    }

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
                break;
        }
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
                Set<String> userIds = Sets.newTreeSet();
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
